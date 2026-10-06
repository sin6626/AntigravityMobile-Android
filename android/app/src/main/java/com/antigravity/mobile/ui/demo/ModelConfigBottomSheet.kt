package com.antigravity.mobile.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * 将后端的扁平模型列表智能聚合成同一个模型族（如 Gemini 3.8 Flash、Gemini 3.1 Pro、Claude 等）
 */
internal fun groupChatModels(models: List<ChatModel>): List<ModelFamily> {
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
            .replace(Regex("\\s*\\((High|Medium|Low)\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(High|Medium|Low)$", RegexOption.IGNORE_CASE), "")
            .trim()

        val baseKey = if (level != null) {
            baseName.lowercase().replace(Regex("[^a-z0-9]"), "")
        } else {
            id
        }

        val list = families.getOrPut(baseKey) { mutableListOf() }
        list.add(ModelVariant(m.id, m.label, level, m))
        displayNameMap.putIfAbsent(baseKey, baseName)
        if (m.supportsImages) {
            imageSupportMap[baseKey] = true
        }
    }

    return families.map { (key, variants) ->
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
        .replace(Regex("\\s*\\((High|Medium|Low)\\)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+(High|Medium|Low)$", RegexOption.IGNORE_CASE), "")
        .trim()
    return cleanName + levelSuffix
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
    val families = remember(models) { groupChatModels(models) }

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

            // 主模型列表大卡片组（浅灰色大圆角卡片）
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

                // 各模型列表项
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
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
                }
            }

            Spacer(Modifier.height(14.dp))

            // 思考程度 / 速度 卡片
            val availableLevels = currentFamily?.variants?.mapNotNull { it.level }?.distinct() ?: emptyList()
            val hasThinkingLevels = availableLevels.size > 1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEEEEEE))
                    .clickable(enabled = modelSelectionEnabled && hasThinkingLevels) {
                        // 点击切换思考档位（在可选档位之间循环）
                        if (currentFamily != null && availableLevels.isNotEmpty()) {
                            val nextIndex = (availableLevels.indexOf(currentLevel) + 1) % availableLevels.size
                            val nextLevel = availableLevels[nextIndex]
                            val targetVariant = currentFamily.variants.firstOrNull { it.level == nextLevel }
                            targetVariant?.let { onSelectModel(it.modelId) }
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "思考程度",
                    fontSize = 15.sp,
                    color = Color(0xFF111111),
                    fontWeight = FontWeight.Normal
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (hasThinkingLevels) "${currentLevel.displayName}档" else "标准",
                        fontSize = 15.sp,
                        color = Color(0xFF666666),
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "切换思考程度",
                        tint = Color(0xFF777777),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

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

            Spacer(Modifier.height(12.dp))
        }
    }
}
