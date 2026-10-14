package com.antigravity.mobile.ui.demo

import com.antigravity.mobile.data.service.ConnectionStatus
import com.antigravity.mobile.ui.chat.ChatUiState
import org.junit.Assert.*
import org.junit.Test

class ChatConnectionNoticeTest {
    private val connected = ChatUiState(isPaired = true, selectedConversationId = "A",
        connectionStatus = ConnectionStatus.CONNECTED)

    @Test fun offlineAndTransportFailuresHaveOneReadableNotice() {
        assertEquals("暂无网络连接，草稿已保留", chatConnectionIssue(connected.copy(networkAvailable = false,
            messagesError = "HTTP 530", subagentsError = "HTTP 530")))
        for (error in listOf("HTTP 530", "HTTP 502", "timeout", "SSLHandshakeException", "UnknownHostException")) {
            assertEquals("聊天暂未同步，请重试", chatConnectionIssue(connected.copy(messagesError = error)))
        }
        assertEquals("连接暂时中断，正在重连", chatConnectionIssue(connected.copy(connectionStatus = ConnectionStatus.FAILED)))
    }

    @Test fun authAndFeatureOnlyFailuresDoNotClaimAConnectionFailure() {
        assertNull(chatConnectionIssue(connected.copy(isPaired = false, messagesError = "HTTP 401", networkAvailable = false)))
        assertEquals("子代理状态暂未同步", chatConnectionIssue(connected.copy(subagentsError = "HTTP 500")))
        assertNull(chatConnectionIssue(connected))
        assertFalse(connected.copy(networkAvailable = false).canSendOverConnection)
        assertFalse(connected.copy(messagesError = "HTTP 530").canSendOverConnection)
        assertTrue(connected.copy(subagentsError = "HTTP 500").canSendOverConnection)
        assertTrue(connected.copy(connectionStatus = ConnectionStatus.FAILED).canSendOverConnection)
        assertEquals("正在重新连接…", chatConnectionIssue(connected.copy(isRetryingConnection = true)))
    }
}
