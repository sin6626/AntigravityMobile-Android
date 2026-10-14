package com.antigravity.mobile.ui.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.data.service.ConnectionStatus
import com.antigravity.mobile.ui.chat.ChatUiState

internal fun chatConnectionIssue(state: ChatUiState): String? = when {
    !state.isPaired -> null
    !state.networkAvailable -> "暂无网络连接，草稿已保留"
    state.isRetryingConnection -> "正在重新连接…"
    state.messagesError != null -> "聊天暂未同步，请重试"
    state.selectedConversationId == null && state.conversationsError != null -> "暂时连接不上电脑，请重试"
    state.selectedConversationId != null && state.connectionStatus == ConnectionStatus.FAILED -> "连接暂时中断，正在重连"
    state.subagentsError != null && state.subagents.isEmpty() -> "子代理状态暂未同步"
    else -> null
}

@Composable
internal fun ChatConnectionNotice(title: String, retrying: Boolean, onRetry: () -> Unit) {
    Surface(color = Color(0xFFF3F5F8), shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(modifier = Modifier.padding(start = 16.dp, end = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(title, color = SecondaryInk, fontSize = 14.sp,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            TextButton(onClick = onRetry, enabled = !retrying,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "重试连接" }) {
                if (retrying) CircularProgressIndicator(modifier = Modifier.size(18.dp),
                    color = AccentBlue, strokeWidth = 2.dp)
                else Text("重试", color = AccentBlue, fontSize = 14.sp)
            }
        }
    }
}
