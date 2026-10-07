package com.antigravity.mobile.ui.demo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.data.model.ChatModel
import kotlinx.coroutines.launch

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
 * 白色外层胶囊与内嵌蓝色滑轨，保留低/中/高三个实际档位。
 */
@Composable
internal fun ThinkingLevelSlider(
    currentLevel: ThinkingLevel,
    onLevelSelected: (ThinkingLevel) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val selectLevel by rememberUpdatedState(onLevelSelected)
    val committedLevel by rememberUpdatedState(currentLevel)
    val blue = Color(0xFF2166F5)

    // 当前在拖拽过程中的预览档位（用于顶部文字实时响应）
    var previewLevel by remember { mutableStateOf(currentLevel) }
    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val outerPadding = 12.dp
        val totalWidth = maxWidth - outerPadding * 2
        val thumbSize = 40.dp
        val horizontalPadding = 4.dp
        val travelDistancePx = with(density) { (totalWidth - thumbSize - horizontalPadding * 2).toPx() }

        // 三个刻度点的绝对位移 (px)
        val tick0Px = 0f
        val tick1Px = travelDistancePx / 2f
        val tick2Px = travelDistancePx

        // 连续位置 Animatable
        val initialOffsetPx = when (currentLevel) {
            ThinkingLevel.LOW -> tick0Px
            ThinkingLevel.MEDIUM -> tick1Px
            ThinkingLevel.HIGH -> tick2Px
        }
        val offsetX = remember { Animatable(initialOffsetPx) }

        // 当外部 currentLevel 改变且用户未在主动拖拽时，弹簧滑向目标
        LaunchedEffect(currentLevel, travelDistancePx) {
            if (!isDragging) {
                val target = when (currentLevel) {
                    ThinkingLevel.LOW -> tick0Px
                    ThinkingLevel.MEDIUM -> tick1Px
                    ThinkingLevel.HIGH -> tick2Px
                }
                previewLevel = currentLevel
                offsetX.animateTo(
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f)
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部居中文字：“高 推理强度”（实时预览当前手指靠近的档位，纯正 GPT 视觉）
            Row(
                modifier = Modifier.padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = previewLevel.displayName,
                    fontSize = 19.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = blue
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "推理强度",
                    fontSize = 19.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111111)
                )
            }

            // 当前滑动进度 0f .. 1f
            val progress = (if (travelDistancePx > 0f) offsetX.value / travelDistancePx else 0f).coerceIn(0f, 1f)
            val totalWidthPx = with(density) { totalWidth.toPx() }
            val trackHeight = 48.dp
            val trackRadius = trackHeight / 2
            val thumbOffsetPx = offsetX.value.coerceIn(0f, travelDistancePx)

            // 蓝色右端圆心与白色滑块同心，四周始终保留4dp蓝色包边。
            val activeWidthPx = (thumbOffsetPx + with(density) { trackHeight.toPx() }).coerceIn(0f, totalWidthPx)
            val activeWidthDp = with(density) { activeWidthPx.toDp() }

            Surface(modifier = Modifier.fillMaxWidth(), color = Color.White,
                shape = CircleShape, shadowElevation = 1.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(outerPadding)
                    .height(trackHeight)
                    .semantics {
                        contentDescription = "推理强度"
                        stateDescription = previewLevel.displayName
                    }
                    .pointerInput(enabled, travelDistancePx) {
                        if (!enabled) return@pointerInput
                        detectTapGestures { tapOffset ->
                            val tapX = (tapOffset.x - with(density) { (horizontalPadding + thumbSize / 2).toPx() })
                                .coerceIn(0f, travelDistancePx)
                            val snappedIndex = when {
                                tapX < travelDistancePx * 0.28f -> 0
                                tapX > travelDistancePx * 0.72f -> 2
                                else -> 1
                            }
                            val targetLevel = ThinkingLevel.values()[snappedIndex]
                            val targetPx = when (targetLevel) {
                                ThinkingLevel.LOW -> tick0Px
                                ThinkingLevel.MEDIUM -> tick1Px
                                ThinkingLevel.HIGH -> tick2Px
                            }
                            previewLevel = targetLevel
                            selectLevel(targetLevel)
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = targetPx,
                                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 450f)
                                )
                            }
                        }
                    }
                    .pointerInput(enabled, travelDistancePx) {
                        if (!enabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = {
                                isDragging = true
                            },
                            onDragEnd = {
                                isDragging = false
                                val currentPx = offsetX.value
                                val nearestIndex = when {
                                    currentPx < travelDistancePx * 0.28f -> 0
                                    currentPx > travelDistancePx * 0.72f -> 2
                                    else -> 1
                                }
                                val finalLevel = ThinkingLevel.values()[nearestIndex]
                                val finalTargetPx = when (finalLevel) {
                                    ThinkingLevel.LOW -> tick0Px
                                    ThinkingLevel.MEDIUM -> tick1Px
                                    ThinkingLevel.HIGH -> tick2Px
                                }
                                previewLevel = finalLevel
                                selectLevel(finalLevel)
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = finalTargetPx,
                                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f)
                                    )
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                previewLevel = committedLevel
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = committedLevel.ordinal * travelDistancePx / 2f,
                                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f)
                                    )
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val nextPx = (offsetX.value + dragAmount).coerceIn(0f, travelDistancePx)
                                scope.launch {
                                    offsetX.snapTo(nextPx)
                                }
                                val nearestIndex = when {
                                    nextPx < travelDistancePx * 0.28f -> 0
                                    nextPx > travelDistancePx * 0.72f -> 2
                                    else -> 1
                                }
                                previewLevel = ThinkingLevel.values()[nearestIndex]
                            }
                        )
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                // 内嵌蓝色胶囊；低档位也包住滑块，而不是单独给滑块画描边。
                    Box(
                        modifier = Modifier
                            .width(activeWidthDp)
                            .height(trackHeight)
                            .clip(RoundedCornerShape(trackRadius))
                            .background(blue)
                    )

                // 圆点中心与三个滑块落点一致。
                val dotSize = 6.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding + thumbSize / 2 - dotSize / 2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val dotColor = if (index / 2f <= progress) Color.White.copy(alpha = 0.28f) else Color(0xFFC7C7CC)
                        Box(
                            modifier = Modifier
                                .size(dotSize)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }

                val currentThumbOffsetDp = horizontalPadding + with(density) { thumbOffsetPx.toDp() }

                Box(
                    modifier = Modifier
                        .offset(x = currentThumbOffsetDp)
                        .size(thumbSize)
                        .background(Color.White, CircleShape)
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
    onDone: () -> Unit = onDismiss,
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

    // 判断当前选择的模型是否支持调节思考程度（如 Claude 没有思考程度选择，则隐藏滑块）
    val hasThinkingLevels = currentFamily?.variants?.any { it.level != null } == true

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
            if (models.isEmpty() && (isLoadingModels || modelsError != null)) {
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

            // 仅列表滚动，底部滑块与完成按钮保持位置。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFEEEEEE))
                    .verticalScroll(rememberScrollState())
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

            // 保留滑块区域高度，切换模型仅淡入淡出，完成按钮和面板锚点不参与尺寸动画。
            Box(Modifier.fillMaxWidth().height(106.dp + with(LocalDensity.current) { 23.sp.toDp() })) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = hasThinkingLevels,
                    enter = fadeIn(animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(20.dp))
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
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // 底部黑色“完成”大胶囊按钮
            Button(
                onClick = onDone,
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
