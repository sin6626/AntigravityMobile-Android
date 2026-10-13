package com.antigravity.mobile.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DesktopStatus(
    val permission: DesktopPermissionStatus = DesktopPermissionStatus(),
    val mcpServers: List<DesktopMcpServer> = emptyList(),
)

@Serializable
data class DesktopPermissionStatus(
    val preset: String = "DEFAULT",
    val source: String = "DEFAULT",
    val terminalPolicy: String = "",
    val nonWorkspaceFilePolicy: String = "",
    val sandboxEnabled: Boolean? = null,
    val planReviewPolicy: String = "ALWAYS",
)

@Serializable
data class DesktopMcpServer(
    val name: String = "",
    val status: String = "",
    val enabled: Boolean = true,
    val toolCount: Int = 0,
    val enabledToolCount: Int = 0,
    val toolErrorCount: Int = 0,
    val error: String = "",
    val requiresAuthentication: Boolean = false,
)
