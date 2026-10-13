package com.antigravity.mobile.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SubagentItem(
    val id: String,
    val parentConversationId: String,
    val rootConversationId: String? = null,
    val title: String = "",
    val status: String = "",
    val stepCount: Int = 0,
    val lastModifiedTime: String? = null,
    val nestingDepth: Int = 0,
    val needsInput: Boolean = false,
    val hasError: Boolean = false,
) {
    val displayTitle: String get() = ConversationItem.sanitizeTitle(title).let {
        if (it == "未命名会话") "未命名子代理" else it
    }
    val displayStatus: String get() = when {
        needsInput || status.contains("WAIT", true) || status.contains("ACTION", true) -> "等待处理"
        hasError || status.contains("ERROR", true) || status.contains("FAIL", true) -> "执行失败"
        status.contains("RUNNING", true) -> "运行中"
        else -> "已完成"
    }
}

@Serializable
data class SubagentsResponse(val subagents: List<SubagentItem> = emptyList())
