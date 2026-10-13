package com.antigravity.mobile.ui.demo

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.data.model.DesktopMcpServer
import com.antigravity.mobile.data.model.DesktopPermissionStatus
import com.antigravity.mobile.data.model.DesktopStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopStatusBottomSheet(
    onDismiss: () -> Unit,
    status: DesktopStatus?,
    loading: Boolean,
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(36.dp, 4.dp)
                .background(Color(0xFFDCDCDC), RoundedCornerShape(2.dp)))
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("电脑状态", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center))
                Box(
                    Modifier.align(Alignment.CenterEnd).size(48.dp)
                        .quietClickable(RoundedCornerShape(14.dp), enabled = !loading && !refreshing, onClick = onRefresh)
                        .semantics { contentDescription = if (refreshing) "正在刷新电脑状态" else "刷新电脑状态" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), color = AccentBlue, strokeWidth = 2.dp)
                    else Text("刷新", color = if (loading) SecondaryInk.copy(alpha = .4f) else AccentBlue,
                        fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Text("只读同步电脑的执行策略和 MCP 连接；修改仍在电脑端完成。",
                color = SecondaryInk, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 12.dp))
            Crossfade(targetState = status, animationSpec = tween(180), label = "desktop status") { value ->
                when {
                    value == null && loading -> LoadingDesktopStatus()
                    value == null -> DesktopStatusError(error ?: "电脑未返回状态", onRefresh)
                    else -> DesktopStatusContent(value, error, onRefresh)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("完成", color = AccentBlue, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun LoadingDesktopStatus() {
    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp), color = AccentBlue, strokeWidth = 2.dp)
            Text("正在读取电脑状态…", color = SecondaryInk, fontSize = 14.sp)
        }
    }
}

@Composable
private fun DesktopStatusContent(status: DesktopStatus, error: String?, onRetry: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 570.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PermissionCard(status.permission) }
        item {
            val ready = status.mcpServers.count { it.enabled && it.status.contains("READY", true) }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("MCP 服务器", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                Text(if (status.mcpServers.isEmpty()) "未安装" else "$ready/${status.mcpServers.size} 已连接",
                    color = SecondaryInk, fontSize = 13.sp)
            }
        }
        if (status.mcpServers.isEmpty()) item {
            Text("电脑当前没有安装 MCP 服务器", color = SecondaryInk, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().background(Color(0xFFF7F7F7), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 24.dp))
        }
        items(status.mcpServers, key = { it.name }) { McpServerCard(it) }
        if (error != null) item { DesktopStatusError(error, onRetry) }
        item { Spacer(Modifier.height(2.dp)) }
    }
}

@Composable
private fun PermissionCard(permission: DesktopPermissionStatus) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFF7F7F7), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("权限与审批", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        StatusValue("执行权限", permissionPresetLabel(permission.preset))
        Text(permissionSourceLabel(permission.source), color = SecondaryInk, fontSize = 12.sp)
        StatusValue("终端命令", terminalPolicyLabel(permission.terminalPolicy))
        StatusValue("工作区外文件", filePolicyLabel(permission.nonWorkspaceFilePolicy))
        StatusValue("终端沙箱", permission.sandboxEnabled?.let { if (it) "开启" else "关闭" } ?: "按电脑默认")
        StatusValue("方案审阅", if (permission.planReviewPolicy.equals("TURBO", true)) "始终继续" else "始终审阅")
    }
}

@Composable
private fun StatusValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = SecondaryInk, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun McpServerCard(server: DesktopMcpServer) {
    val color by animateColorAsState(mcpStatusColor(server), tween(180), label = "MCP status")
    val status = mcpStatusLabel(server)
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFF7F7F7), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = "${server.name.ifBlank { "未知服务器" }}，$status" },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(server.name.ifBlank { "未知服务器" }, color = Ink, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            Text(status, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text(mcpToolSummary(server), color = SecondaryInk, fontSize = 13.sp)
        if (server.requiresAuthentication) Text("需要在电脑端完成认证", color = Color(0xFFE58A00), fontSize = 12.sp)
        if (server.error.isNotBlank()) Text(server.error, color = Color(0xFFB3261E), fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun DesktopStatusError(message: String, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).background(Color(0xFFFFF2F1), RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = Color(0xFFB3261E), fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)
            .semantics { contentDescription = "重试读取电脑状态" }) { Text("重试", color = AccentBlue) }
    }
}

internal fun permissionPresetLabel(value: String): String = when (value.uppercase()) {
    "REQUEST_REVIEW" -> "请求审阅"
    "VETTED" -> "Vetted"
    "AUTO" -> "自动"
    "TURBO" -> "Turbo"
    else -> "默认"
}

private fun permissionSourceLabel(value: String): String = when (value.uppercase()) {
    "PERMISSION_PRESET" -> "来源：电脑全局权限预设"
    "EFFECTIVE_SETTINGS" -> "来源：由电脑终端与文件设置推导"
    else -> "来源：电脑默认设置"
}

private fun terminalPolicyLabel(value: String): String = when {
    value.contains("EAGER", true) -> "直接执行"
    value.endsWith("_AUTO", true) || value.equals("AUTO", true) -> "自动判断"
    value.contains("OFF", true) -> "每次审阅"
    value.contains("SANDBOX", true) -> "沙箱内继续"
    else -> "按电脑默认"
}

private fun filePolicyLabel(value: String): String = when {
    value.contains("ALLOW", true) -> "允许"
    value.contains("DENY", true) -> "拒绝"
    value.contains("ASK", true) -> "每次询问"
    else -> "按电脑默认"
}

private fun mcpStatusLabel(server: DesktopMcpServer): String = when {
    !server.enabled || server.status.contains("DISABLED", true) -> "已停用"
    server.status.contains("READY", true) -> "已连接"
    server.status.contains("PENDING", true) -> "连接中"
    server.status.contains("ERROR", true) -> "错误"
    else -> "未知"
}

private fun mcpStatusColor(server: DesktopMcpServer): Color = when (mcpStatusLabel(server)) {
    "已连接" -> Color(0xFF188038)
    "连接中" -> Color(0xFFE58A00)
    "错误" -> Color(0xFFB3261E)
    else -> SecondaryInk
}

private fun mcpToolSummary(server: DesktopMcpServer): String = when {
    !server.enabled -> "${server.toolCount} 个工具已停用"
    server.toolErrorCount > 0 -> "${server.enabledToolCount} 个工具可用 · ${server.toolErrorCount} 个工具错误"
    else -> "${server.enabledToolCount} 个工具可用"
}
