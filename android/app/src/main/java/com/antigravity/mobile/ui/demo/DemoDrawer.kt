package com.antigravity.mobile.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DemoDrawer(
    modifier: Modifier,
    onOpenConversation: () -> Unit,
    onNewChat: () -> Unit,
) {
    Column(modifier = modifier.background(Color.White).padding(start = 28.dp, end = 22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 33.dp, bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Multigravity", modifier = Modifier.weight(1f), color = Ink, fontSize = 27.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            RoundIconButton("search", "搜索", {}, size = 50.dp)
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            DrawerMenuItem("chat_bubble", "聊天", onNewChat)
            DrawerMenuItem("folder", "项目", {})
            DrawerMenuItem("computer", "远程控制", {})
            Spacer(Modifier.height(27.dp))
            DrawerSection("置顶")
            DrawerConversation("Android 界面设计", onOpenConversation)
            DrawerConversation("网关连接说明", onOpenConversation)
            Spacer(Modifier.height(29.dp))
            DrawerSection("最近")
            DrawerConversation("示例对话", onOpenConversation)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 25.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.background(AccentBlue, RoundedCornerShape(28.dp))
                    .clickable(onClick = onNewChat).padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol("edit_square", size = 24, color = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("聊天", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier.size(42.dp).background(Color(0xFFE8E8E8), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Symbol("person", size = 26, color = SecondaryInk) }
        }
    }
}

@Composable
private fun DrawerMenuItem(icon: String, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol(icon, size = 28)
        Spacer(Modifier.width(18.dp))
        Text(title, color = Ink, fontSize = 19.sp)
    }
}

@Composable
private fun DrawerSection(title: String) {
    Text(title, modifier = Modifier.padding(bottom = 13.dp), color = Ink, fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold)
}

@Composable
private fun DrawerConversation(title: String, onClick: () -> Unit) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick)
            .padding(top = 12.dp),
        color = Ink,
        fontSize = 17.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
