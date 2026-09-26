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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.data.model.ConversationItem

@Composable
internal fun DemoDrawer(
    modifier: Modifier,
    conversations: List<ConversationItem>,
    isLoading: Boolean,
    onOpenConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onUnsupported: () -> Unit,
) {
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    Column(modifier = modifier.background(Color.White).padding(start = 28.dp, end = 22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 33.dp, bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Multigravity", modifier = Modifier.weight(1f), color = Ink, fontSize = 27.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            RoundIconButton("search", "搜索", { searchOpen = !searchOpen }, size = 50.dp)
        }
        if (searchOpen) {
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    .background(Color(0xFFF3F3F3), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                singleLine = true,
                decorationBox = { inner ->
                    Box {
                        if (searchText.isBlank()) Text("搜索会话", color = SecondaryInk, fontSize = 16.sp)
                        inner()
                    }
                },
            )
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            DrawerMenuItem("chat_bubble", "聊天", onNewChat)
            DrawerMenuItem("folder", "项目", onUnsupported)
            DrawerMenuItem("computer", "远程控制", onUnsupported)
            Spacer(Modifier.height(27.dp))
            DrawerSection("最近")
            val filtered = conversations.filter { it.displayTitle.contains(searchText, ignoreCase = true) }
            if (isLoading && filtered.isEmpty()) {
                Text("正在加载会话…", color = SecondaryInk, fontSize = 16.sp)
            } else if (filtered.isEmpty()) {
                Text("暂无会话", color = SecondaryInk, fontSize = 16.sp)
            }
            filtered.forEach { item ->
                DrawerConversation(item.displayTitle) { onOpenConversation(item.id) }
            }
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
