package com.antigravity.mobile.ui.demo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.data.model.ChatModel

enum class ThinkingLevel(val displayName: String, val shortName: String) {
    LOW("低", "低"),
    MEDIUM("中", "中"),
    HIGH("高", "高")
}

data class ModelVariant(
    val modelId: String,
    val fullLabel: String,
    val level: ThinkingLevel?,
    val raw: ChatModel
)

data class ModelFamily(
    val baseKey: String,
    val displayName: String,
    val supportsImages: Boolean,
    val variants: List<ModelVariant>
)

/**
 * 全量聚合后端返回的所有模型（绝不过滤任何模型），并将 Gemini 模型按版本倒序排在前面。
 */
internal fun groupAndSortChatModels(models: List<ChatModel>): List<ModelFamily> {
    val families = linkedMapOf<String, MutableList<ModelVariant>>()
    val imageSupportMap = mutableMapOf<String, Boolean>()
    val displayNameMap = mutableMapOf<String, String>()

    for (m in models) {
        val id = m.id.lowercase()
        val label = m.label

        val levelFromId = when {
            id.endsWith("-high") || id.contains("-high-") || id.endsWith("_high") -> ThinkingLevel.HIGH
            id.endsWith("-medium") || id.contains("-medium-") || id.endsWith("_medium") -> ThinkingLevel.MEDIUM
            id.endsWith("-low") || id.contains("-low-") || id.endsWith("_low") -> ThinkingLevel.LOW
            else -> null
        }

        val levelFromLabel = when {
            label.contains("(High)", ignoreCase = true) || label.endsWith(" High", ignoreCase = true) -> ThinkingLevel.HIGH
            label.contains("(Medium)", ignoreCase = true) || label.endsWith(" Medium", ignoreCase = true) -> ThinkingLevel.MEDIUM
            label.contains("(Low)", ignoreCase = true) || label.endsWith(" Low", ignoreCase = true) -> ThinkingLevel.LOW
            else -> null
        }

        val level = levelFromId ?: levelFromLabel

        val baseName = label
            .replace(Regex("""\s*\((High|Medium|Low)\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(High|Medium|Low)$""", RegexOption.IGNORE_CASE), "")
            .trim()

        val baseKey = if (level != null) {
            baseName.lowercase().replace(Regex("[^a-z0-9]"), "")
        } else {
            m.id
        }

        val list = families.getOrPut(baseKey) { mutableListOf() }
        list.add(ModelVariant(m.id, m.label, level, m))
        displayNameMap.putIfAbsent(baseKey, baseName)
        if (m.supportsImages) {
            imageSupportMap[baseKey] = true
        }
    }

    val familyList = families.map { (key, variants) ->
        val sortedVariants = variants.sortedBy { v ->
            when (v.level) {
                ThinkingLevel.LOW -> 1
                ThinkingLevel.MEDIUM -> 2
                ThinkingLevel.HIGH -> 3
                null -> 0
            }
        }
        ModelFamily(
            baseKey = key,
            displayName = displayNameMap[key] ?: key,
            supportsImages = imageSupportMap[key] == true,
            variants = sortedVariants
        )
    }

    // 辅助解析版本号进行倒序排序
    fun extractVersionScore(name: String): Double {
        val match = Regex("""(\d+\.?\d*)""").find(name)
        val num = match?.value?.toDoubleOrNull() ?: 0.0
        val sub = if (name.contains("flash", ignoreCase = true)) 0.05 else 0.0
        return num + sub
    }

    // Gemini 模型倒序排在前面（版本高的排前面）
    val geminiFamilies = familyList.filter {
        it.displayName.contains("gemini", ignoreCase = true) || it.baseKey.contains("gemini", ignoreCase = true)
    }.sortedByDescending { extractVersionScore(it.displayName) }

    // 其余非 Gemini 模型（Claude, GPT-OSS等）紧随其后全量展示
    val otherFamilies = familyList.filter {
        !it.displayName.contains("gemini", ignoreCase = true) && !it.baseKey.contains("gemini", ignoreCase = true)
    }

    return geminiFamilies + otherFamilies
}

/**
 * 格式化为类似 GPT 的简短模型标签，例如 "Gemini 3.8 Flash 高" 或 "Claude Opus 4.6"
 */
internal fun formatGptModelBadge(model: ChatModel?): String {
    if (model == null) return "Gemini 3.8 Flash 高"
    val label = model.label
    val levelSuffix = when {
        model.id.endsWith("-high") || label.contains("(High)", ignoreCase = true) -> " 高"
        model.id.endsWith("-medium") || label.contains("(Medium)", ignoreCase = true) -> " 中"
        model.id.endsWith("-low") || label.contains("(Low)", ignoreCase = true) -> " 低"
        else -> ""
    }
    val cleanName = label
        .replace(Regex("""\s*\((High|Medium|Low)\)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+(High|Medium|Low)$""", RegexOption.IGNORE_CASE), "")
        .trim()
    return cleanName + levelSuffix
}

/**
 * GPT 风格的思考程度（推理强度）大药丸可拉动滑块选择器
 */
@Composable
internal fun ThinkingLevelSlider(
    currentLevel: ThinkingLevel,
    onLevelSelected: (ThinkingLevel) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val targetIndex = currentLevel.ordinal.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = targetIndex,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "thinking_progress"
    )

    val targetTrackColor = when (currentLevel) {
        ThinkingLevel.HIGH -> Color(0xFF0A84FF)
        ThinkingLevel.MEDIUM -> Color(0xFF2E82E6)
        ThinkingLevel.LOW -> Color(0xFFE5E5EA)
    }
    val animatedTrackColor by animateColorAsState(
        targetValue = if (enabled) targetTrackColor else Color(0xFFE5E5EA),
        animationSpec = tween(250),
        label = "thinking_track_color"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部文本：“高 推理强度”（高高亮蓝色）
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = currentLevel.displayName,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentLevel == ThinkingLevel.LOW) Color(0xFF111111) else Color(0xFF0A84FF)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "推理强度",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111111)
            )
        }

        // 下方胶囊滑块轨道
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(animatedTrackColor)
                .then(
                    if (currentLevel == ThinkingLevel.LOW) {
                        Modifier.border(1.dp, Color(0xFFD0D0D7), RoundedCornerShape(27.dp))
                    } else Modifier
                )
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val widthPx = size.width
                        val fraction = (offset.x / widthPx).coerceIn(0f, 1f)
                        val newIndex = when {
                            fraction < 0.33f -> 0
                            fraction < 0.67f -> 1
                            else -> 2
                        }
                        onLevelSelected(ThinkingLevel.values()[newIndex])
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {},
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val widthPx = size.width
                            val fraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                            val newIndex = when {
                                fraction < 0.33f -> 0
                                fraction < 0.67f -> 1
                                else -> 2
                            }
                            if (newIndex != currentLevel.ordinal) {
                                onLevelSelected(ThinkingLevel.values()[newIndex])
                            }
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidth = maxWidth
            val thumbSize = 44.dp
            val padding = 5.dp
            val travelDistance = totalWidth - thumbSize - (padding * 2)

            // 3 个刻度圆点
            val isBlueTrack = currentLevel != ThinkingLevel.LOW
            val tickDotColor = if (isBlueTrack) Color.White.copy(alpha = 0.55f) else Color(0xFFA0A0A8)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = padding + thumbSize / 2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(tickDotColor)
                    )
                }
            }

            // 白色可拉动滑块把手（Thumb）
            val thumbOffset = padding + travelDistance * (animatedProgress / 2f)

            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .shadow(
                        elevation = if (isBlueTrack) 3.dp else 2.dp,
                        shape = CircleShape,
                        spotColor = Color(0x33000000)
                    )
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (currentLevel == ThinkingLevel.LOW) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .border(2.5.dp, Color(0xFF0A84FF), CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * 1:1 还原 GPT 风格的模型“配置” BottomSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelConfigBottomSheet(
    onDismiss: () -> Unit,
    models: List<ChatModel>,
    currentChosenModelId: String?,
    onSelectModel: (String) -> Unit,
    isLoadingModels: Boolean,
    modelsError: String?,
    onRetryModels: () -> Unit,
    modelSelectionEnabled: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 全量聚合与 Gemini 倒序排序
    val families = remember(models) { groupAndSortChatModels(models) }

    // 找到当前选中的模型变体与所属模型族
    val currentVariant = remember(models, currentChosenModelId) {
        models.firstOrNull { it.id == currentChosenModelId || it.model == currentChosenModelId }
    }
    val currentFamily = remember(families, currentVariant) {
        if (currentVariant == null) families.firstOrNull()
        else families.firstOrNull { fam -> fam.variants.any { it.modelId == currentVariant.id || it.raw.model == currentVariant.id } }
    }

    // 当前思考程度
    val currentLevel = remember(currentVariant) {
        val id = currentVariant?.id.orEmpty().lowercase()
        val label = currentVariant?.label.orEmpty()
        when {
            id.endsWith("-high") || label.contains("(High)", ignoreCase = true) -> ThinkingLevel.HIGH
            id.endsWith("-medium") || label.contains("(Medium)", ignoreCase = true) -> ThinkingLevel.MEDIUM
            id.endsWith("-low") || label.contains("(Low)", ignoreCase = true) -> ThinkingLevel.LOW
            else -> ThinkingLevel.HIGH
        }
    }

    // 是否选中了默认项（首个推荐模型）
    val defaultModel = models.firstOrNull { it.id.contains("3.8-flash-high") } ?: models.firstOrNull()
    val isDefaultSelected = currentChosenModelId == null || currentChosenModelId == defaultModel?.id || currentChosenModelId == defaultModel?.model

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Color(0xFFDCDCDC), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 居中标题：“配置”
            Text(
                text = "配置",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111111),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 加载与错误状态提示
            if (isLoadingModels || modelsError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isLoadingModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF111111),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("加载模型列表中…", color = Color(0xFF666666), fontSize = 14.sp)
                    } else if (modelsError != null) {
                        Text(modelsError, color = Color.Red, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onRetryModels) {
                            Text("重试", color = Color(0xFF0A84FF))
                        }
                    }
                }
            }

            // 主模型列表大卡片组（浅灰色大圆角卡片，直接列表渲染，绝不嵌套内部 scrollView）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFEEEEEE))
            ) {
                // 第一项：“默认”
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = modelSelectionEnabled && defaultModel != null) {
                            defaultModel?.let { onSelectModel(it.id) }
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "默认",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF111111)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "推荐的前沿模型组合",
                            fontSize = 13.sp,
                            color = Color(0xFF757575)
                        )
                    }
                    if (isDefaultSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "已选择",
                            tint = Color(0xFF444444),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // 分割线
                HorizontalDivider(color = Color.White, thickness = 1.dp)

                // 各模型列表项（全量直接展开渲染）
                families.forEachIndexed { index, family ->
                    val isFamilySelected = currentFamily?.baseKey == family.baseKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = modelSelectionEnabled) {
                                // 选中此模型族时，优先保持当前思考档位对应的变体
                                val matched = family.variants.firstOrNull { it.level == currentLevel }
                                    ?: family.variants.firstOrNull { it.level == ThinkingLevel.HIGH }
                                    ?: family.variants.first()
                                onSelectModel(matched.modelId)
                            }
                            .padding(horizontal = 20.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = family.displayName,
                            fontSize = 16.sp,
                            fontWeight = if (isFamilySelected) FontWeight.Medium else FontWeight.Normal,
                            color = Color(0xFF111111),
                            modifier = Modifier.weight(1f)
                        )
                        if (isFamilySelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "已选择",
                                tint = Color(0xFF444444),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    if (index < families.size - 1) {
                        HorizontalDivider(color = Color.White, thickness = 1.dp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // 思考程度 / 推理强度 拖动滑块组件（GPT 风格大胶囊滑块）
            val hasThinkingLevels = currentFamily?.variants?.any { it.level != null } == true
            ThinkingLevelSlider(
                currentLevel = currentLevel,
                onLevelSelected = { newLevel ->
                    if (currentFamily != null) {
                        val targetVariant = currentFamily.variants.firstOrNull { it.level == newLevel }
                            ?: currentFamily.variants.firstOrNull()
                        targetVariant?.let { onSelectModel(it.modelId) }
                    }
                },
                enabled = modelSelectionEnabled && hasThinkingLevels
            )

            Spacer(Modifier.height(24.dp))

            // 底部黑色“完成”大胶囊按钮
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF111111),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(26.dp)
            ) {
                Text(
                    text = "完成",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
