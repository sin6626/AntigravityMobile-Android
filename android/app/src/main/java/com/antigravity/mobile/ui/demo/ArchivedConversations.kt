package com.antigravity.mobile.ui.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.data.model.ConversationItem

@Composable
internal fun ArchivedConversations(conversations: List<ConversationItem>, loading: Boolean,
    busy: Set<String>, onBack: () -> Unit, onRefresh: () -> Unit, onOpen: (String) -> Unit,
    onRename: (ConversationItem) -> Unit, onRestore: (String) -> Unit, onDelete: (String) -> Unit,
    entranceReady: Boolean = true) {
    var query by remember { mutableStateOf("") }
    val entrance = contentEntrance("archive", (-10).dp, entranceReady)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("arrow_back", "返回", onBack)
            Text("已归档", color = Ink, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onRefresh, enabled = !loading) { Text("刷新", color = AccentBlue) }
        }
        OutlinedTextField(query, { query = it }, placeholder = { Text("搜索归档会话") }, singleLine = true,
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                Icon(Icons.Default.Close, contentDescription = "清除搜索", tint = SecondaryInk, modifier = Modifier.size(18.dp))
            } },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedBorderColor = AccentBlue, unfocusedContainerColor = androidx.compose.ui.graphics.Color(0xFFF3F3F3),
                focusedContainerColor = androidx.compose.ui.graphics.Color(0xFFF3F3F3)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp))
        val filtered = conversations.filter { it.isArchived && it.displayTitle.contains(query, true) }
        if (filtered.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().then(entrance), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(color = AccentBlue)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (query.isBlank()) "暂无归档会话" else "没有匹配的会话", color = SecondaryInk)
                TextButton(onClick = onRefresh) { Text("重新加载", color = AccentBlue) }
            }
        } else LazyColumn(Modifier.weight(1f).then(entrance), contentPadding = PaddingValues(22.dp)) {
            items(filtered, key = { it.id }) { item ->
                var menuOpen by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).quietClickable { onOpen(item.id) }.padding(vertical = 12.dp)) {
                        Text(item.displayTitle, color = Ink, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.workspaceName != "Chat") Text(item.workspaceName, color = SecondaryInk, fontSize = 13.sp)
                    }
                    Box {
                        RoundIconButton("more_vert", "归档会话操作", { menuOpen = true })
                        ConversationActionsMenu(menuOpen, { menuOpen = false }, item.displayTitle, item.isPinned, true,
                            item.id !in busy, { menuOpen = false; onRename(item) }, {},
                            { menuOpen = false; onRestore(item.id) }, { menuOpen = false; onDelete(item.id) })
                    }
                }
            }
        }
    }
}
