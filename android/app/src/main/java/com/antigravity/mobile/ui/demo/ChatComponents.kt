package com.antigravity.mobile.ui.demo

import android.net.Uri
import coil.compose.AsyncImage
import com.antigravity.mobile.ui.chat.PendingImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material3.Icon
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
    projectsSelected: Boolean,
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
        Row(
            modifier = Modifier.background(Color(0xFFF4F4F4), RoundedCornerShape(32.dp)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(88.dp).height(44.dp)
                    .background(if (projectsSelected) Color.Transparent else Color.White, RoundedCornerShape(28.dp))
                    .clickable(onClick = onChat),
                contentAlignment = Alignment.Center,
            ) { Text("聊天", fontSize = 18.sp, color = Ink) }
            Box(modifier = Modifier.width(88.dp).height(44.dp)
                .background(if (projectsSelected) Color.White else Color.Transparent, RoundedCornerShape(28.dp))
                .clickable(onClick = onProjects), contentAlignment = Alignment.Center) {
                Text("项目", fontSize = 18.sp, color = Ink)
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
    onMore: () -> Unit,
) {
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
            RoundIconButton("more_vert", "更多选项", onMore)
        }
    }
}

@Composable
internal fun Composer(
    activeConversation: Boolean,
    draft: String,
    attachments: List<PendingImage>,
    onDraftChange: (String) -> Unit,
    onAddImage: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    onUnsupported: () -> Unit,
) {
    val canSend = (draft.isNotBlank() || attachments.isNotEmpty()) && !isSending
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (activeConversation) 14.dp else 34.dp),
        shape = RoundedCornerShape(if (activeConversation) 30.dp else 36.dp),
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        if (activeConversation || attachments.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 15.dp, bottom = 8.dp)) {
                if (attachments.isNotEmpty()) AttachmentTray(attachments, onRemoveImage)
                ComposerTextField(draft, onDraftChange, onSend, canSend,
                    if (activeConversation) "回复 Multigravity" else "询问 Multigravity", Modifier.fillMaxWidth())
                Spacer(Modifier.height(18.dp))
                ComposerActions(canSend, onSend, onAddImage, onUnsupported)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol("add", modifier = Modifier.clickable(onClick = onAddImage), size = 32)
                Spacer(Modifier.width(12.dp))
                ComposerTextField(draft, onDraftChange, onSend, canSend,
                    "询问 Multigravity", Modifier.weight(1f))
                Symbol("mic", modifier = Modifier.clickable(onClick = onUnsupported), size = 26)
                Spacer(Modifier.width(15.dp))
                SendAction(canSend, onSend)
            }
        }
    }
}

@Composable
private fun ComposerActions(canSend: Boolean, onSend: () -> Unit, onAddImage: () -> Unit, onUnsupported: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Symbol("add", modifier = Modifier.clickable(onClick = onAddImage), size = 32)
        Spacer(Modifier.weight(1f))
        Symbol("mic", modifier = Modifier.clickable(onClick = onUnsupported), size = 27)
        Spacer(Modifier.width(22.dp))
        SendAction(canSend, onSend)
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
                    .clickable { onRemove(image.uri) }.padding(horizontal = 4.dp),
                    color = Ink, fontSize = 18.sp)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SendAction(canSend: Boolean, onSend: () -> Unit) {
    Box(
        modifier = Modifier.size(43.dp)
            .background(if (canSend) AccentBlue else Color(0xFFD2D3D5), CircleShape)
            .clickable(enabled = canSend, onClick = onSend)
            .semantics { contentDescription = "发送消息" },
        contentAlignment = Alignment.Center,
    ) { Symbol("arrow_upward", size = 26, color = Color.White) }
}

@Composable
private fun ComposerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    placeholder: String,
    modifier: Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = TextStyle(color = Ink, fontSize = 17.sp, lineHeight = 23.sp),
        maxLines = 4,
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
