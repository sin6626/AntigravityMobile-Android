package com.antigravity.mobile.ui.demo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.antigravity.mobile.data.model.ConversationItem
import com.antigravity.mobile.data.model.ProjectItem

@Composable
internal fun DemoDrawer(
    modifier: Modifier,
    conversations: List<ConversationItem>,
    projects: List<ProjectItem>,
    isLoading: Boolean,
    isLoadingProjects: Boolean,
    onOpenConversation: (String) -> Unit,
    onOpenProjectConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onPairing: () -> Unit,
    onArchived: () -> Unit,
    selectedConversationId: String? = null,
    conversationsError: String? = null, projectsError: String? = null,
    onRetryConversations: () -> Unit = {}, onRetryProjects: () -> Unit = {},
) {
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var projectsOpen by remember { mutableStateOf(false) }
    var expandedProjects by remember { mutableStateOf(setOf<String>()) }
    Column(modifier = modifier.background(Color.White).padding(start = 28.dp, end = 22.dp)) {
        val backdrop = rememberGraphicsLayer()
        val density = LocalDensity.current
        var headerHeight by remember { mutableStateOf(105.dp) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().recordBackdrop(backdrop, headerHeight).verticalScroll(rememberScrollState()).padding(top = headerHeight)) {
                DrawerMenuItem("chat_bubble", "聊天", onNewChat)
                DrawerMenuItem("archive", "已归档", onArchived)
                DrawerMenuItem("folder", "项目") { projectsOpen = !projectsOpen }
                projectsError?.let { LoadFailure(it, isLoadingProjects, onRetryProjects) }
                conversationsError?.let { LoadFailure(it, isLoading, onRetryConversations) }
                if (projectsOpen || searchText.isNotBlank()) {
                    val visibleProjects = projects.filter { project ->
                        searchText.isBlank() || project.name.contains(searchText, ignoreCase = true) ||
                            conversations.any { it.belongsTo(project, projects) &&
                                it.displayTitle.contains(searchText, ignoreCase = true) }
                    }
                    if (isLoadingProjects && visibleProjects.isEmpty()) {
                        Text("正在加载项目…", modifier = Modifier.padding(start = 45.dp, top = 8.dp),
                            color = SecondaryInk, fontSize = 15.sp)
                    } else if (visibleProjects.isEmpty()) {
                        Text(if (searchText.isBlank()) "暂无项目" else "没有匹配的项目", modifier = Modifier.padding(start = 45.dp, top = 8.dp),
                            color = SecondaryInk, fontSize = 15.sp)
                    }
                    visibleProjects.distinctBy { it.navigationKey() }.forEach { project ->
                        val projectKey = project.navigationKey()
                        val projectChats = conversations.filter { it.belongsTo(project, projects) && !it.isSubagent }
                        DrawerProject(project.name, expandedProjects.contains(projectKey)) {
                            expandedProjects = if (projectKey in expandedProjects)
                                expandedProjects - projectKey else expandedProjects + projectKey
                        }
                        AnimatedVisibility(projectKey in expandedProjects || searchText.isNotBlank() &&
                            projectChats.any { it.displayTitle.contains(searchText, ignoreCase = true) },
                            enter = expandVertically(tween(200), expandFrom = Alignment.Top) + fadeIn(tween(150)),
                            exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Top) + fadeOut(tween(120))) {
                            Column {
                            val visibleChats = if (searchText.isBlank() || project.name.contains(searchText, ignoreCase = true))
                                projectChats else projectChats.filter { it.displayTitle.contains(searchText, ignoreCase = true) }
                            if (visibleChats.isEmpty()) {
                                Text("暂无对话", modifier = Modifier.padding(start = 45.dp, top = 8.dp, bottom = 8.dp),
                                    color = SecondaryInk, fontSize = 14.sp)
                            }
                            visibleChats.forEach { item ->
                                DrawerConversation(item.displayTitle, indent = 45.dp, current = item.id == selectedConversationId) {
                                    onOpenProjectConversation(item.id)
                                }
                            }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(27.dp))
                val pinned = conversations.filter { it.isPinned && it.displayTitle.contains(searchText, true) }
                if (pinned.isNotEmpty()) {
                    DrawerSection("置顶")
                    pinned.forEach { item -> DrawerConversation(item.displayTitle, current = item.id == selectedConversationId) {
                        if (item.isPureChat) onOpenConversation(item.id) else onOpenProjectConversation(item.id)
                    } }
                    Spacer(Modifier.height(20.dp))
                }
                DrawerSection("最近")
                val filtered = conversations.filter { it.isPureChat && !it.isSubagent && !it.isPinned &&
                    it.displayTitle.contains(searchText, ignoreCase = true) }
                if (isLoading && filtered.isEmpty()) {
                    Text("正在加载会话…", color = SecondaryInk, fontSize = 16.sp)
                } else if (filtered.isEmpty()) {
                    if (conversationsError == null) Text(if (searchText.isBlank()) "暂无最近会话" else "没有匹配的最近会话", color = SecondaryInk, fontSize = 16.sp)
                }
                filtered.forEach { item ->
                    DrawerConversation(item.displayTitle, current = item.id == selectedConversationId) { onOpenConversation(item.id) }
                }
            }
            FrostedTopBar(backdrop, headerHeight) {
                Column(Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } }) {
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
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            singleLine = true,
                            decorationBox = { inner ->
                                Row(Modifier.background(Color(0xFFF3F3F3), RoundedCornerShape(20.dp))
                                    .heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                                        if (searchText.isEmpty()) Text("搜索会话", color = SecondaryInk, fontSize = 16.sp)
                                        inner()
                                    }
                                    if (searchText.isNotEmpty()) IconButton(onClick = { searchText = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "清除搜索", tint = SecondaryInk, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                        )
                    }

                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 25.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.background(AccentBlue, RoundedCornerShape(28.dp))
                    .quietClickable(RoundedCornerShape(28.dp), onClick = onPairing)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol("computer", size = 24, color = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("配对", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
            Box(Modifier.size(48.dp).semantics { contentDescription = "打开配对设置" }
                .quietClickable(CircleShape, onClick = onPairing), contentAlignment = Alignment.Center) {
                Box(Modifier.size(42.dp).background(Color(0xFFE8E8E8), CircleShape),
                    contentAlignment = Alignment.Center) { Symbol("person", size = 26, color = SecondaryInk) }
            }
        }
    }
}

@Composable
private fun DrawerMenuItem(icon: String, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp).quietClickable(onClick = onClick),
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
private fun DrawerProject(title: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 51.dp).semantics { stateDescription = if (expanded) "已展开" else "已折叠" }.quietClickable(onClick = onClick)
            .padding(start = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol("folder", size = 21, color = SecondaryInk)
        Spacer(Modifier.width(11.dp))
        Text(title, modifier = Modifier.weight(1f), color = Ink, fontSize = 16.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (expanded) "−" else "+", color = SecondaryInk, fontSize = 20.sp)
    }
}

@Composable
private fun DrawerConversation(title: String, indent: androidx.compose.ui.unit.Dp = 0.dp, current: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { selected = current }
            .background(if (current) Color(0xFFF1F1F1) else Color.Transparent, RoundedCornerShape(14.dp)).quietClickable(onClick = onClick)
            .padding(start = indent + 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), contentAlignment = Alignment.CenterStart) {
    Text(
        title,
        color = Ink,
        fontSize = 17.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    }
}

// ponytail: rows without ID/URI/path use their name; identical names need stable backend IDs to distinguish them.
internal fun ProjectItem.navigationKey(): String =
    listOf(id, uri.ifBlank { path }.trimEnd('/')).takeIf { it.any(String::isNotBlank) }?.joinToString("\u0000") ?: "name:$name"

internal fun ConversationItem.belongsTo(project: ProjectItem, allProjects: List<ProjectItem>): Boolean {
    if (isPureChat) return false
    if (!projectId.isNullOrBlank() && projectId == project.id) return true
    if (workspaceUri.isNotBlank() && project.uri.isNotBlank() &&
        workspaceUri.trimEnd('/').equals(project.uri.trimEnd('/'), ignoreCase = true)) return true
    return projectId.isNullOrBlank() && workspaceUri.isBlank() &&
        workspaceName.equals(project.name, ignoreCase = true) &&
        allProjects.count { it.name.equals(project.name, ignoreCase = true) } == 1
}
