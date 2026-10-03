package com.antigravity.mobile.ui.demo

import android.net.Uri
import coil.compose.AsyncImage
import com.antigravity.mobile.ui.chat.PendingImage
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.Crossfade
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import com.antigravity.mobile.R

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
    Text(
        text = symbolCodepoints.getValue(name),
        modifier = modifier,
        color = color,
        maxLines = 1,
        style = TextStyle(
            fontFamily = symbolFont,
            fontSize = size.sp,
            lineHeight = size.sp,
        ),
    )
}

@Composable
internal fun RoundIconButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    size: Dp = 52.dp,
    iconSize: Int = 29,
    background: Color = Color.White,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size).semantics { contentDescription = label },
        shape = CircleShape,
        color = background,
        contentColor = Ink,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon == "arrow_back") {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null,
                    modifier = Modifier.size(iconSize.dp))
            } else {
                Symbol(icon, size = iconSize)
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
        RoundIconButton("menu", "打开菜单", onMenu, size = 52.dp)
        val density = LocalDensity.current
        Box(Modifier.width(184.dp).height(52.dp).background(Color(0xFFF4F4F4), RoundedCornerShape(32.dp))) {
            Box(Modifier.padding(4.dp).width(88.dp).height(44.dp)
                .graphicsLayer { translationX = with(density) { (88.dp * tabPosition.coerceIn(0f, 1f)).toPx() } }
                .background(Color.White, RoundedCornerShape(28.dp)))
            Row(Modifier.padding(4.dp)) {
                Box(Modifier.width(88.dp).height(44.dp).quietClickable(RoundedCornerShape(28.dp), onClick = onChat),
                    contentAlignment = Alignment.Center) { Text("聊天", fontSize = 18.sp, color = Ink) }
                Box(Modifier.width(88.dp).height(44.dp).quietClickable(RoundedCornerShape(28.dp), onClick = onProjects),
                    contentAlignment = Alignment.Center) { Text("项目", fontSize = 18.sp, color = Ink) }
            }
        }
        RoundIconButton("chat_bubble", "语音聊天", onUnsupported, size = 52.dp)
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
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RoundIconButton(
            if (returnToProjects) "arrow_back" else "menu",
            if (returnToProjects) "返回项目列表" else "打开菜单",
            onLeading,
        )
        Row(
            modifier = Modifier.background(Color.White, RoundedCornerShape(32.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton("edit_square", "新建聊天", onNewChat)
            Box {
                RoundIconButton("more_vert", "更多选项", { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("重命名") }, enabled = actionsEnabled,
                        onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("删除会话", color = Color(0xFFB3261E)) }, enabled = actionsEnabled,
                        onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}

@Composable
internal fun Composer(
    modifier: Modifier = Modifier,
    activeConversation: Boolean,
    draft: String,
    attachments: List<PendingImage>,
    onDraftChange: (String) -> Unit,
    onAddImage: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    onUnsupported: () -> Unit,
    isRunning: Boolean = false,
    isStopping: Boolean = false,
    onStop: () -> Unit = {},
) {
    val canSend = (draft.isNotBlank() || attachments.isNotEmpty()) && !isSending && !isRunning && !isStopping
    var focused by remember { mutableStateOf(false) }
    val expanded = focused || attachments.isNotEmpty()
    val horizontalPadding by animateDpAsState(
        if (activeConversation || expanded) 14.dp else 34.dp,
        animationSpec = spring(stiffness = 350f), label = "composer width")
    val corner by animateDpAsState(if (expanded) 30.dp else 36.dp,
        animationSpec = spring(stiffness = 350f), label = "composer corner")
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        shape = RoundedCornerShape(corner),
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize(animationSpec = spring(stiffness = 350f))) {
            AnimatedVisibility(attachments.isNotEmpty(), enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()) {
                Box(Modifier.padding(start = 20.dp, top = 12.dp)) { AttachmentTray(attachments, onRemoveImage) }
            }
            Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(!expanded, enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Symbol("add", modifier = Modifier.quietClickable(CircleShape, onClick = onAddImage), size = 32)
                        Spacer(Modifier.width(12.dp))
                    }
                }
                ComposerTextField(draft, onDraftChange, onSend, canSend,
                    if (activeConversation) "回复 Multigravity" else "询问 Multigravity",
                    Modifier.weight(1f).onFocusChanged { focused = it.isFocused }, expanded)
                AnimatedVisibility(!expanded, enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Symbol("mic", modifier = Modifier.quietClickable(CircleShape, onClick = onUnsupported), size = 26)
                        Spacer(Modifier.width(15.dp))
                        SendAction(canSend, onSend, isRunning, isSending, isStopping, onStop)
                    }
                }
            }
            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()) {
                Box(Modifier.padding(start = 20.dp, end = 12.dp, bottom = 9.dp, top = 3.dp)) {
                    ComposerActions(canSend, onSend, onAddImage, onUnsupported, isRunning, isSending, isStopping, onStop)
                }
            }
        }
    }
}

@Composable
private fun ComposerActions(canSend: Boolean, onSend: () -> Unit, onAddImage: () -> Unit, onUnsupported: () -> Unit,
    isRunning: Boolean, isSending: Boolean, isStopping: Boolean, onStop: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Symbol("add", modifier = Modifier.quietClickable(CircleShape, onClick = onAddImage), size = 32)
        Spacer(Modifier.weight(1f))
        Symbol("mic", modifier = Modifier.quietClickable(CircleShape, onClick = onUnsupported), size = 27)
        Spacer(Modifier.width(22.dp))
        SendAction(canSend, onSend, isRunning, isSending, isStopping, onStop)
    }
}

@Composable
private fun AttachmentTray(images: List<PendingImage>, onRemove: (Uri) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        images.forEach { image ->
            Box {
                AsyncImage(
                    model = image.uri,
                    contentDescription = "待发送图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(58.dp).background(Color(0xFFF3F3F3), RoundedCornerShape(10.dp)),
                )
                Text("×", modifier = Modifier.align(Alignment.TopEnd)
                    .background(Color.White, CircleShape)
                    .quietClickable(CircleShape) { onRemove(image.uri) }.padding(horizontal = 4.dp),
                    color = Ink, fontSize = 18.sp)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SendAction(canSend: Boolean, onSend: () -> Unit, isRunning: Boolean, isSending: Boolean,
    isStopping: Boolean, onStop: () -> Unit) {
    val enabled = !isStopping && (if (isRunning) !isSending else canSend)
    Box(
        modifier = Modifier.size(44.dp)
            .background(if (enabled || isStopping) AccentBlue else Color(0xFFD2D3D5), CircleShape)
            .quietClickable(CircleShape, enabled = enabled, onClick = if (isRunning) onStop else onSend)
            .semantics {
                contentDescription = if (isRunning) "停止生成" else "发送消息"
                stateDescription = if (isStopping) "正在停止" else if (isSending) "正在发送" else ""
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = if (isStopping || isSending) "busy" else if (isRunning) "stop" else "send",
            animationSpec = androidx.compose.animation.core.tween(120), label = "send action") { action ->
            when (action) {
                "busy" -> CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                "stop" -> Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
                else -> Symbol("arrow_upward", size = 26, color = Color.White)
            }
        }
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
