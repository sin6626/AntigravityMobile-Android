package com.antigravity.mobile.ui.demo

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    "call" to "\uE0B0",
    "folder" to "\uE2C7",
    "more_horiz" to "\uE5D3",
    "more_vert" to "\uE5D4",
    "menu" to "\uE5D2",
    "mic" to "\uE029",
    "person" to "\uE7FD",
    "search" to "\uE8B6",
    "thumb_up" to "\uE817",
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
            Symbol(icon, size = iconSize)
        }
    }
}

@Composable
internal fun EmptyTopBar(onMenu: () -> Unit) {
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
                    .background(Color.White, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("聊天", fontSize = 18.sp, color = Ink) }
            Box(modifier = Modifier.width(88.dp).height(44.dp), contentAlignment = Alignment.Center) {
                Text("工作", fontSize = 18.sp, color = Ink)
            }
        }
        RoundIconButton("chat_bubble", "快捷入口", {}, size = 52.dp)
    }
}

@Composable
internal fun ConversationTopBar(onMenu: () -> Unit, onNewChat: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RoundIconButton("menu", "打开菜单", onMenu)
        Row(
            modifier = Modifier.background(Color.White, RoundedCornerShape(32.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton("edit_square", "新建聊天", onNewChat)
            RoundIconButton("more_vert", "更多选项", {})
        }
    }
}

@Composable
internal fun Composer(activeConversation: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (activeConversation) 14.dp else 34.dp),
        shape = RoundedCornerShape(if (activeConversation) 30.dp else 36.dp),
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        if (activeConversation) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 15.dp, bottom = 8.dp)) {
                Text("回复 Multigravity", color = Color(0xFF929292), fontSize = 17.sp)
                Spacer(Modifier.height(18.dp))
                ComposerActions()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol("add", size = 32)
                Spacer(Modifier.width(12.dp))
                Text("询问 Multigravity", modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = Color(0xFF929292), fontSize = 17.sp)
                Symbol("mic", size = 26)
                Spacer(Modifier.width(15.dp))
                BlueAction()
            }
        }
    }
}

@Composable
private fun ComposerActions() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Symbol("add", size = 32)
        Spacer(Modifier.weight(1f))
        Symbol("mic", size = 27)
        Spacer(Modifier.width(22.dp))
        BlueAction()
    }
}

@Composable
private fun BlueAction() {
    Box(
        modifier = Modifier.size(43.dp).background(AccentBlue, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Symbol("call", size = 26, color = Color.White) }
}
