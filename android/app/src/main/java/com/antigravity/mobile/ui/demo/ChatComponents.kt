package com.antigravity.mobile.ui.demo

import android.net.Uri
import coil.compose.AsyncImage
import com.antigravity.mobile.ui.chat.PendingImage
import com.antigravity.mobile.ui.chat.PendingFile
import com.antigravity.mobile.data.model.WorkspaceContextItem
import com.antigravity.mobile.data.model.SlashCommandDefinition
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.Crossfade
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import com.antigravity.mobile.R

@Composable
internal fun GradientTopBar(height: Dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(height + 16.dp)) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.White.copy(alpha = .94f), .35f to Color.White.copy(alpha = .80f),
            .75f to Color.White.copy(alpha = .38f), 1f to Color.Transparent)))
        content()
    }
}

internal val Ink = Color(0xFF111111)
internal val SecondaryInk = Color(0xFF666666)
internal val AccentBlue = Color(0xFF0A84FF)
private val symbolFont = FontFamily(Font(R.font.material_symbols_outlined))
private val symbolCodepoints = mapOf(
    "content_copy" to "\uE14D",
    "chat_bubble" to "\uE0CA",
    "edit_square" to "\uF88D",
    "add" to "\uE145",
    "computer" to "\uE30A",
    "folder" to "\uE2C7",
    "more_horiz" to "\uE5D3",
    "more_vert" to "\uE5D4",
    "push_pin" to "\uF10D",
    "archive" to "\uE149",
    "unarchive" to "\uE169",
    "undo" to "\uE166",
    "delete" to "\uE872",
    "edit" to "\uE3C9",
    "menu" to "\uE5D2",
    "mic" to "\uE029",
    "person" to "\uE7FD",
    "search" to "\uE8B6",
    "thumb_up" to "\uE817",
    "arrow_upward" to "\uE5D8",
)

@Composable
internal fun Symbol(
    name: String,
    modifier: Modifier = Modifier,
    size: Int = 28,
    color: Color = Ink,
) {
    val iconSize = with(LocalDensity.current) { size.dp.toSp() }
    Text(
        text = symbolCodepoints[name] ?: symbolCodepoints.getValue("computer"),
        modifier = modifier,
        color = color,
        maxLines = 1,
        style = TextStyle(
            fontFamily = symbolFont,
            fontSize = iconSize,
            lineHeight = iconSize,
        ),
    )
}

@Composable
internal fun RoundIconButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    size: Dp = 40.dp,
    iconSize: Int = 24,
    background: Color = Color.White,
) {
    Box(Modifier.size(48.dp).semantics { contentDescription = label }
        .quietClickable(CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.size(size), shape = CircleShape, color = background, shadowElevation = 1.dp) {
            Box(contentAlignment = Alignment.Center) {
                if (icon == "arrow_back") {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(iconSize.dp))
                } else Symbol(icon, size = iconSize)
            }
        }
    }
}

@Composable
internal fun EmptyTopBar(
    onMenu: () -> Unit,
    tabPosition: Float,
    onChat: () -> Unit,
    onProjects: () -> Unit,
    onUnsupported: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RoundIconButton("menu", "打开菜单", onMenu)
        val density = LocalDensity.current
        Box(Modifier.width(160.dp).height(48.dp)) {
            Box(Modifier.fillMaxWidth().height(40.dp).align(Alignment.Center)
                .background(Color(0xFFF4F4F4), RoundedCornerShape(24.dp)))
            Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp).width(76.dp).height(32.dp)
                .graphicsLayer { translationX = with(density) { (76.dp * tabPosition.coerceIn(0f, 1f)).toPx() } }
                .background(Color.White, RoundedCornerShape(20.dp)))
            Row(Modifier.padding(horizontal = 4.dp)) {
                Box(Modifier.width(76.dp).height(48.dp).semantics { selected = tabPosition < 0.5f }
                    .quietClickable(RoundedCornerShape(24.dp), onClick = onChat), contentAlignment = Alignment.Center) {
                    Text("聊天", fontSize = 16.sp, color = Ink)
                }
                Box(Modifier.width(76.dp).height(48.dp).semantics { selected = tabPosition >= 0.5f }
                    .quietClickable(RoundedCornerShape(24.dp), onClick = onProjects), contentAlignment = Alignment.Center) {
                    Text("项目", fontSize = 16.sp, color = Ink)
                }
            }
        }
        RoundIconButton("chat_bubble", "语音聊天，暂未开放", onUnsupported)
    }
}

@Composable
internal fun ConversationTopBar(
    returnToProjects: Boolean,
    onLeading: () -> Unit,
    onNewChat: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    actionsEnabled: Boolean = true,
    title: String = "未命名会话",
    isPinned: Boolean = false,
    isArchived: Boolean = false,
    onPin: () -> Unit = {},
    onArchive: () -> Unit = {},
    onReviewChanges: (() -> Unit)? = null,
    leadingDescription: String = if (returnToProjects) "返回项目列表" else "打开菜单",
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RoundIconButton(
            if (returnToProjects) "arrow_back" else "menu",
            leadingDescription,
            onLeading,
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.background(Color.White, RoundedCornerShape(32.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton("edit_square", "新建聊天", onNewChat)
            Box {
                RoundIconButton("more_vert", "更多选项", { menuOpen = true })
                ConversationActionsMenu(menuOpen, { menuOpen = false }, title, isPinned, isArchived, actionsEnabled,
                    { menuOpen = false; onRename() }, { menuOpen = false; onPin() },
                    { menuOpen = false; onArchive() }, { menuOpen = false; onDelete() },
                    onReview = onReviewChanges?.let { review -> { menuOpen = false; review() } })
            }
        }
    }
}

@Composable
internal fun ComposerPlusMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    if (!expanded && visibility.isIdle && !visibility.currentState) return

    val density = LocalDensity.current
    // 向上弹出定位在加号按钮上方
    val offsetY = with(density) { (-190).dp.roundToPx() }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(x = 0, y = offsetY),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = expanded)
    ) {
        AnimatedVisibility(visibility, enter = EnterTransition.None, exit = ExitTransition.None) {
        val progress by transition.animateFloat(
            transitionSpec = { tween(if (targetState == EnterExitState.Visible) 160 else 100) }, label = "plus menu appearance") {
            if (it == EnterExitState.Visible) 1f else 0f
        }
        val shape = RoundedCornerShape(26.dp)
        Surface(
            modifier = Modifier
                .width(185.dp)
                .graphicsLayer {
                    alpha = progress
                    scaleX = .96f + .04f * progress
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(0f, 1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    this.shape = shape
                    shadowElevation = 16.dp.toPx() * progress
                    ambientShadowColor = Color.Black.copy(alpha = progress)
                    spotShadowColor = Color(0x33000000).copy(alpha = .2f * progress)
                }
                .border(1.dp, Color(0xFFEEEEEE), shape)
                .then(if (!expanded) Modifier.clearAndSetSemantics {} else Modifier),
            shape = shape,
            color = Color.White
        ) {
            Column(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp)
            ) {
                // 1. 拍照
                PlusMenuItem(
                    icon = Icons.Outlined.PhotoCamera,
                    label = "拍照",
                    enabled = expanded,
                    onClick = {
                        onDismissRequest()
                        onTakePhoto()
                    }
                )

                Spacer(Modifier.height(4.dp))

                // 2. 图片
                PlusMenuItem(
                    icon = Icons.Outlined.Image,
                    label = "图片",
                    enabled = expanded,
                    onClick = {
                        onDismissRequest()
                        onPickImage()
                    }
                )

                Spacer(Modifier.height(4.dp))

                // 3. 文件
                PlusMenuItem(
                    icon = Icons.Outlined.AttachFile,
                    label = "文件",
                    enabled = expanded,
                    onClick = {
                        onDismissRequest()
                        onPickFile()
                    }
                )
            }
        }
        }
    }
}

@Composable
private fun PlusMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(Color(0xFFF1F1F1), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF1E1E1E),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1E1E1E)
        )
    }
}

@Composable
internal fun Composer(
    modifier: Modifier = Modifier,
    activeConversation: Boolean,
    draft: String,
    attachments: List<PendingImage>,
    fileAttachment: PendingFile? = null,
    workspaceContexts: List<WorkspaceContextItem> = emptyList(),
    slashCommand: SlashCommandDefinition? = null,
    contextSuggestions: List<WorkspaceContextItem> = emptyList(),
    commandSuggestions: List<SlashCommandDefinition> = emptyList(),
    isLoadingContextSuggestions: Boolean = false,
    isLoadingCommandSuggestions: Boolean = false,
    contextSuggestionsError: String? = null,
    commandSuggestionsError: String? = null,
    onDraftChange: (String) -> Unit,
    onAddImage: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onPickImage: () -> Unit = onAddImage,
    onPickFile: () -> Unit = {},
    onRemoveImage: (Uri) -> Unit,
    onRemoveFile: () -> Unit = {},
    onSearchWorkspaceContexts: (String) -> Unit = {},
    onSearchSlashCommands: (String) -> Unit = {},
    onAddWorkspaceContext: (WorkspaceContextItem) -> Unit = {},
    onRemoveWorkspaceContext: (String) -> Unit = {},
    onSelectSlashCommand: (SlashCommandDefinition) -> Unit = {},
    onRemoveSlashCommand: () -> Unit = {},
    onDismissSuggestions: () -> Unit = {},
    onSend: () -> Unit,
    isSending: Boolean,
    onUnsupported: () -> Unit,
    isRunning: Boolean = false,
    isStopping: Boolean = false,
    onStop: () -> Unit = {},
    shadowAlpha: Float = 1f,
    modelBadge: String? = null,
    onOpenModelConfig: () -> Unit = {},
    modelEnabled: Boolean = true,
    inputFocusRequester: FocusRequester? = null,
) {
    val trigger = remember(draft) { composerTrigger(draft) }
    LaunchedEffect(trigger) {
        when (trigger?.kind) {
            '@' -> onSearchWorkspaceContexts(trigger.query)
            '/' -> onSearchSlashCommands(trigger.query)
            else -> onDismissSuggestions()
        }
    }
    val canSend = (draft.isNotBlank() || attachments.isNotEmpty() || fileAttachment != null ||
        workspaceContexts.isNotEmpty() || slashCommand != null) && !isSending && !isStopping
    var focused by remember { mutableStateOf(false) }
    var plusMenuOpen by remember { mutableStateOf(false) }
    val expanded = focused || attachments.isNotEmpty() || fileAttachment != null ||
        workspaceContexts.isNotEmpty() || slashCommand != null || trigger != null
    val targetHorizontalPadding = if (activeConversation || expanded) 14.dp else 34.dp
    val targetCorner = if (expanded) 30.dp else 36.dp
    val horizontalPadding by animateDpAsState(
        targetHorizontalPadding,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 350f), label = "composer width")
    val corner by animateDpAsState(targetCorner,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 350f), label = "composer corner")
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding)
            .shadow(8.dp * shadowAlpha, RoundedCornerShape(corner), clip = false,
                ambientColor = Color.Black.copy(alpha = shadowAlpha), spotColor = Color.Black.copy(alpha = shadowAlpha)),
        shape = RoundedCornerShape(corner),
        color = Color.White,
    ) {
        Column(Modifier.fillMaxWidth()) {
            AnimatedVisibility(trigger != null, enter = expandVertically(tween(180), expandFrom = Alignment.Bottom) + fadeIn(tween(120)),
                exit = shrinkVertically(tween(160), shrinkTowards = Alignment.Bottom) + fadeOut(tween(100))) {
                trigger?.let {
                    ComposerSuggestionPanel(
                        trigger = it,
                        contexts = contextSuggestions,
                        commands = commandSuggestions,
                        loading = if (it.kind == '@') isLoadingContextSuggestions else isLoadingCommandSuggestions,
                        error = if (it.kind == '@') contextSuggestionsError else commandSuggestionsError,
                        onRetry = { if (it.kind == '@') onSearchWorkspaceContexts(it.query) else onSearchSlashCommands(it.query) },
                        onContext = { item ->
                            onDraftChange(removeComposerTrigger(draft))
                            onAddWorkspaceContext(item)
                            inputFocusRequester?.requestFocus()
                        },
                        onCommand = { command ->
                            onDraftChange(removeComposerTrigger(draft))
                            onSelectSlashCommand(command)
                            inputFocusRequester?.requestFocus()
                        },
                    )
                }
            }
            AnimatedVisibility(workspaceContexts.isNotEmpty() || slashCommand != null,
                enter = expandVertically(tween(180), expandFrom = Alignment.Top) + fadeIn(tween(120)),
                exit = shrinkVertically(tween(180), shrinkTowards = Alignment.Top) + fadeOut(tween(100))) {
                ComposerContextTray(workspaceContexts, slashCommand, onRemoveWorkspaceContext, onRemoveSlashCommand)
            }
            AnimatedVisibility(attachments.isNotEmpty() || fileAttachment != null, enter = expandVertically(tween(180), expandFrom = Alignment.Top) + fadeIn(tween(120)),
                exit = shrinkVertically(tween(180), shrinkTowards = Alignment.Top) + fadeOut(tween(100))) {
                Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (attachments.isNotEmpty()) AttachmentTray(attachments, onRemoveImage)
                    fileAttachment?.let { FileAttachmentChip(it, onRemoveFile) }
                }
            }
            Row(Modifier.fillMaxWidth().animateContentSize(tween(180))
                .heightIn(min = 58.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(!expanded, enter = expandHorizontally(tween(180)) + fadeIn(tween(120)),
                    exit = shrinkHorizontally(tween(180)) + fadeOut(tween(100))) {
                    Row(if (expanded) Modifier.clearAndSetSemantics {} else Modifier, verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            ComposerIcon("add", "添加附件与操作", 32, { plusMenuOpen = true }, enabled = !expanded)
                            ComposerPlusMenu(
                                expanded = plusMenuOpen && !expanded,
                                onDismissRequest = { plusMenuOpen = false },
                                onTakePhoto = onTakePhoto,
                                onPickImage = onPickImage,
                                onPickFile = onPickFile
                            )
                        }
                    }
                }
                ComposerTextField(draft, onDraftChange, onSend, canSend,
                    if (activeConversation) "回复 Multigravity" else "询问 Multigravity",
                    Modifier.weight(1f).padding(vertical = 12.dp)
                        .then(inputFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                        .onFocusChanged { focused = it.isFocused }, expanded)
                AnimatedVisibility(!expanded, enter = expandHorizontally(tween(180)) + fadeIn(tween(120)),
                    exit = shrinkHorizontally(tween(180)) + fadeOut(tween(100))) {
                    Row(if (expanded) Modifier.clearAndSetSemantics {} else Modifier, verticalAlignment = Alignment.CenterVertically) {
                        ComposerIcon("mic", "语音输入，暂不可用", 26, onUnsupported, enabled = !expanded)
                        SendAction(canSend, onSend, isRunning, isSending, isStopping, onStop, active = !expanded)
                    }
                }
            }
            AnimatedVisibility(expanded, enter = expandVertically(tween(180), expandFrom = Alignment.Top) + fadeIn(tween(120)),
                exit = shrinkVertically(tween(180), shrinkTowards = Alignment.Top) + fadeOut(tween(100))) {
                Box((if (!expanded) Modifier.clearAndSetSemantics {} else Modifier).padding(start = 20.dp, end = 12.dp, bottom = 9.dp, top = 3.dp)) {
                    Box {
                        ComposerActions(
                            canSend = canSend,
                            onSend = onSend,
                            onOpenPlusMenu = { plusMenuOpen = true },
                            onUnsupported = onUnsupported,
                            isRunning = isRunning,
                            isSending = isSending,
                            isStopping = isStopping,
                            onStop = onStop,
                            active = expanded,
                            modelBadge = modelBadge,
                            onOpenModelConfig = onOpenModelConfig,
                            modelEnabled = modelEnabled
                        )
                        ComposerPlusMenu(
                            expanded = plusMenuOpen && expanded,
                            onDismissRequest = { plusMenuOpen = false },
                            onTakePhoto = onTakePhoto,
                            onPickImage = onPickImage,
                            onPickFile = onPickFile
                        )
                    }
                }
            }
        }
    }
}

internal data class ComposerTrigger(val kind: Char, val query: String, val start: Int)

internal fun composerTrigger(value: String): ComposerTrigger? {
    val match = Regex("(?:^|\\s)([@/])([^\\s@/]*)$").find(value) ?: return null
    val markerIndex = match.range.first + if (match.value.first().isWhitespace()) 1 else 0
    return ComposerTrigger(value[markerIndex], value.substring(markerIndex + 1), markerIndex)
}

internal fun removeComposerTrigger(value: String): String {
    val trigger = composerTrigger(value) ?: return value
    return value.removeRange(trigger.start, value.length)
}

@Composable
private fun ComposerSuggestionPanel(
    trigger: ComposerTrigger,
    contexts: List<WorkspaceContextItem>,
    commands: List<SlashCommandDefinition>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onContext: (WorkspaceContextItem) -> Unit,
    onCommand: (SlashCommandDefinition) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
        .semantics { contentDescription = if (trigger.kind == '@') "电脑文件建议" else "命令建议" }) {
        Text(if (trigger.kind == '@') "引用电脑文件" else "运行命令", color = SecondaryInk,
            fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
        when {
            loading -> Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AccentBlue)
                Spacer(Modifier.width(10.dp)); Text("正在查找…", color = SecondaryInk, fontSize = 14.sp)
            }
            error != null -> Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(error, color = SecondaryInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("重试", color = AccentBlue) }
            }
            trigger.kind == '@' && contexts.isEmpty() || trigger.kind == '/' && commands.isEmpty() ->
                Text(if (trigger.query.isBlank()) "没有可用结果" else "没有匹配“${trigger.query}”的结果",
                    color = SecondaryInk, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 16.dp))
            trigger.kind == '@' -> contexts.take(6).forEach { item ->
                SuggestionRow(
                    icon = if (item.type == "directory") "folder" else "attach_file",
                    title = item.name,
                    subtitle = listOf(item.rootName, item.relativePath).filter(String::isNotBlank).joinToString(" · "),
                    description = "引用${if (item.type == "directory") "目录" else "文件"} ${item.relativePath}",
                    onClick = { onContext(item) },
                )
            }
            else -> commands.take(6).forEach { command ->
                SuggestionRow(
                    icon = "computer",
                    title = "/${command.info.name}",
                    subtitle = command.description.ifBlank { command.title },
                    description = "选择命令 /${command.info.name}",
                    onClick = { onCommand(command) },
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(icon: String, title: String, subtitle: String, description: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
        .quietClickable(RoundedCornerShape(14.dp), onClick = onClick)
        .semantics { contentDescription = description }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(AccentBlue.copy(alpha = .09f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center) {
            if (icon == "attach_file") Icon(Icons.Outlined.AttachFile, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
            else Symbol(icon, size = 20, color = AccentBlue)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = SecondaryInk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ComposerContextTray(
    contexts: List<WorkspaceContextItem>,
    command: SlashCommandDefinition?,
    onRemoveContext: (String) -> Unit,
    onRemoveCommand: () -> Unit,
) {
    LazyRow(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        command?.let { selected ->
            item("command:${selected.info.name}") {
                ComposerContextChip("computer", "/${selected.info.name}", "已选命令 /${selected.info.name}", onRemoveCommand)
            }
        }
        items(contexts, key = { it.uri }) { context ->
            ComposerContextChip(if (context.type == "directory") "folder" else "attach_file", context.relativePath,
                "已引用${if (context.type == "directory") "目录" else "文件"} ${context.relativePath}") {
                onRemoveContext(context.uri)
            }
        }
    }
}

@Composable
private fun ComposerContextChip(icon: String, label: String, description: String, onRemove: () -> Unit) {
    Surface(color = AccentBlue.copy(alpha = .08f), shape = RoundedCornerShape(14.dp),
        modifier = Modifier.heightIn(min = 48.dp).widthIn(max = 260.dp).semantics { contentDescription = description }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(12.dp))
            if (icon == "attach_file") Icon(Icons.Outlined.AttachFile, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
            else Symbol(icon, size = 18, color = AccentBlue)
            Spacer(Modifier.width(7.dp))
            Text(label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Box(Modifier.size(48.dp).semantics { contentDescription = "移除 $label" }
                .quietClickable(CircleShape, onClick = onRemove), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, null, tint = SecondaryInk, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ComposerActions(
    canSend: Boolean,
    onSend: () -> Unit,
    onOpenPlusMenu: () -> Unit,
    onUnsupported: () -> Unit,
    isRunning: Boolean,
    isSending: Boolean,
    isStopping: Boolean,
    onStop: () -> Unit,
    active: Boolean,
    modelBadge: String?,
    onOpenModelConfig: () -> Unit,
    modelEnabled: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ComposerIcon("add", "添加附件与操作", 32, onOpenPlusMenu, active)
        Spacer(Modifier.weight(1f))
        if (!modelBadge.isNullOrBlank()) {
            Text(
                text = modelBadge,
                color = Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = active && modelEnabled, onClick = onOpenModelConfig)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .semantics { contentDescription = "选择模型" }
            )
            Spacer(Modifier.width(4.dp))
        }
        ComposerIcon("mic", "语音输入，暂不可用", 27, onUnsupported, active)
        SendAction(canSend, onSend, isRunning, isSending, isStopping, onStop, active)
    }
}

@Composable
private fun AttachmentTray(images: List<PendingImage>, onRemove: (Uri) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(images, key = { it.uri.toString() }) { image ->
            Box(Modifier.animateItem(fadeInSpec = tween(120), placementSpec = tween(180), fadeOutSpec = null)) {
                AsyncImage(
                    model = image.bytes,
                    contentDescription = "待发送图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(58.dp).background(Color(0xFFF3F3F3), RoundedCornerShape(10.dp)),
                )
                Box(Modifier.size(48.dp).align(Alignment.TopEnd)
                    .semantics { contentDescription = "移除待发送图片" }
                    .quietClickable(CircleShape) { onRemove(image.uri) }, contentAlignment = Alignment.TopEnd) {
                    Icon(Icons.Filled.Close, null, tint = Ink, modifier = Modifier.size(22.dp).background(Color.White, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun SendAction(canSend: Boolean, onSend: () -> Unit, isRunning: Boolean, isSending: Boolean,
    isStopping: Boolean, onStop: () -> Unit, active: Boolean = true) {
    val queueing = isRunning && canSend
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(
            queueing && !isSending && !isStopping,
            enter = expandHorizontally(tween(160), expandFrom = Alignment.End) + fadeIn(tween(120)),
            exit = shrinkHorizontally(tween(140), shrinkTowards = Alignment.End) + fadeOut(tween(100)),
        ) {
            Box(
                Modifier.size(48.dp).semantics { contentDescription = "停止生成" }
                    .quietClickable(CircleShape, enabled = active, onClick = onStop),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(40.dp).background(AccentBlue, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
                }
            }
        }
        val stopsGeneration = isRunning && !queueing && !isSending
        val enabled = active && !isStopping && if (stopsGeneration) true else canSend
        Box(
            modifier = Modifier.size(48.dp)
                .quietClickable(CircleShape, enabled = enabled, onClick = if (stopsGeneration) onStop else onSend)
                .semantics {
                    contentDescription = when {
                        stopsGeneration -> "停止生成"
                        queueing || isSending && isRunning -> "排队发送消息"
                        else -> "发送消息"
                    }
                    stateDescription = if (isStopping) "正在停止" else if (isSending) "正在发送" else ""
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(44.dp).background(if (enabled || isStopping || isSending) AccentBlue else Color(0xFFD2D3D5), CircleShape), contentAlignment = Alignment.Center) {
                Crossfade(targetState = if (isStopping || isSending) "busy" else if (stopsGeneration) "stop" else "send",
                    animationSpec = androidx.compose.animation.core.tween(120), label = "send action") { action ->
                    when (action) {
                        "busy" -> CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        "stop" -> Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
                        else -> Symbol("arrow_upward", size = 26, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileAttachmentChip(file: PendingFile, onRemove: () -> Unit) {
    Surface(
        color = Color(0xFFF4F5F7),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "待发送文件，${file.displayName}，${formatFileSize(file.size)}"
        },
    ) {
        Row(Modifier.heightIn(min = 58.dp).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(AccentBlue.copy(alpha = .10f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(file.displayName, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${formatFileSize(file.size)} · ${file.mimeType}", color = SecondaryInk, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(48.dp).semantics { contentDescription = "移除文件 ${file.displayName}" }
                .quietClickable(CircleShape, onClick = onRemove), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = SecondaryInk, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun ComposerIcon(icon: String, label: String, size: Int, action: () -> Unit, enabled: Boolean = true) {
    Box(Modifier.size(48.dp).semantics { contentDescription = label }.quietClickable(CircleShape, enabled = enabled, onClick = action),
        contentAlignment = Alignment.Center) { Symbol(icon, size = size) }
}

@Composable
internal fun LoadFailure(error: String, loading: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(error, color = SecondaryInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(enabled = !loading, onClick = onRetry) { Text(if (loading) "重试中…" else "重试", color = AccentBlue) }
    }
}

@Composable
private fun ComposerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    placeholder: String,
    modifier: Modifier,
    expanded: Boolean,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = TextStyle(color = Ink, fontSize = 17.sp, lineHeight = 23.sp),
        maxLines = if (expanded) 4 else 1,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = Color(0xFF929292), fontSize = 17.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            }
        },
    )
}

@Composable
internal fun ConversationActionsMenu(expanded: Boolean, onDismiss: () -> Unit, title: String,
    pinned: Boolean, archived: Boolean, enabled: Boolean, onRename: () -> Unit,
    onPin: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit, showTitle: Boolean = true,
    onReview: (() -> Unit)? = null) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    if (!expanded && visibility.isIdle && !visibility.currentState) return
    var origin by remember { mutableStateOf(TransformOrigin(1f, 0f)) }
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val position = remember(margin) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
            layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            val below = anchorBounds.bottom + margin
            val opensBelow = below + popupContentSize.height <= windowSize.height - margin
            origin = TransformOrigin(if (layoutDirection == LayoutDirection.Ltr) 1f else 0f, if (opensBelow) 0f else 1f)
            val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
            val y = if (opensBelow) below else anchorBounds.top - popupContentSize.height - margin
            return IntOffset(x.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
                y.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)))
        }
    } }
    androidx.compose.ui.window.Popup(popupPositionProvider = position, onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = expanded)) {
        AnimatedVisibility(visibility, enter = EnterTransition.None, exit = ExitTransition.None) {
        val progress by transition.animateFloat(
            transitionSpec = { tween(if (targetState == EnterExitState.Visible) 160 else 100) }, label = "menu appearance") {
            if (it == EnterExitState.Visible) 1f else 0f
        }
        val shape = RoundedCornerShape(20.dp)
        androidx.compose.material3.Surface(shape = shape, color = Color.White,
            modifier = Modifier.width(220.dp).graphicsLayer {
                alpha = progress
                scaleX = .96f + .04f * progress
                scaleY = scaleX
                transformOrigin = origin
                // Keep the fading shadow outside the card's rectangular bounds.
                compositingStrategy = CompositingStrategy.ModulateAlpha
                this.shape = shape
                shadowElevation = 6.dp.toPx() * progress
                ambientShadowColor = Color.Black.copy(alpha = progress)
                spotShadowColor = ambientShadowColor
            }.then(if (!expanded) Modifier.clearAndSetSemantics {} else Modifier)) {
            Column(Modifier.padding(vertical = 6.dp)) {
                if (showTitle) Row(Modifier.fillMaxWidth().quietClickable(enabled = enabled && expanded,
                    pressedColor = Color(0xFFF1F1F1), onClick = onRename)
                    .semantics { contentDescription = "重命名" }.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color(0xFF8A8A8A), fontSize = 14.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp)); Symbol("edit", size = 18, color = SecondaryInk)
                }
                if (!archived) ConversationMenuRow("push_pin", if (pinned) "取消置顶" else "置顶", enabled && expanded, onPin)
                if (!showTitle) ConversationMenuRow("edit", "重命名", enabled && expanded, onRename)
                onReview?.let { ConversationMenuRow("edit_square", "审阅变更", enabled && expanded, it) }
                ConversationMenuRow(if (archived) "unarchive" else "archive", if (archived) "恢复会话" else "归档", enabled && expanded, onArchive)
                ConversationMenuRow("delete", "删除会话", enabled && expanded, onDelete, Color(0xFFCC2332))
            }
        }
        }
    }
}

@Composable
private fun ConversationMenuRow(icon: String, text: String, enabled: Boolean, onClick: () -> Unit, color: Color = Ink) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).quietClickable(enabled = enabled,
        pressedColor = Color(0xFFF1F1F1), onClick = onClick)
        .alpha(if (enabled) 1f else 0.45f).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Symbol(icon, size = 22, color = color); Spacer(Modifier.width(14.dp))
        Text(text, color = color, fontSize = 16.sp)
    }
}
