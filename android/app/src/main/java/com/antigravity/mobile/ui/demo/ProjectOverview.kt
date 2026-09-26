package com.antigravity.mobile.ui.demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.data.model.ConversationItem
import com.antigravity.mobile.data.model.ProjectItem

@Composable
internal fun ProjectOverview(
    projects: List<ProjectItem>,
    conversations: List<ConversationItem>,
    isLoading: Boolean,
    onOpenConversation: (String) -> Unit,
    expandedProjects: Set<String>,
    onToggleProject: (String) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text("项目", modifier = Modifier.padding(start = 28.dp, top = 29.dp, bottom = 12.dp),
                color = Ink, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        }
        if (projects.isEmpty()) {
            item {
                Text(if (isLoading) "正在加载项目…" else "暂无项目",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                    color = SecondaryInk, fontSize = 16.sp)
            }
        }
        itemsIndexed(projects, key = { index, project -> "$index:${project.id}:${project.uri}" }) { index, project ->
            val projectKey = "$index:${project.id}:${project.uri}"
            val projectChats = conversations.filter { !it.isSubagent && it.belongsTo(project, projects) }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onToggleProject(projectKey)
                    }.padding(horizontal = 28.dp, vertical = 17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Symbol("folder", size = 25)
                    Spacer(Modifier.width(15.dp))
                    Text(project.name, modifier = Modifier.weight(1f), color = Ink, fontSize = 18.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (projectKey in expandedProjects) "−" else "+", color = SecondaryInk,
                        fontSize = 23.sp)
                }
                if (projectKey in expandedProjects) {
                    if (projectChats.isEmpty()) {
                        Text("暂无对话", modifier = Modifier.padding(start = 68.dp, bottom = 13.dp),
                            color = SecondaryInk, fontSize = 15.sp)
                    }
                    projectChats.forEach { chat ->
                        Text(chat.displayTitle, modifier = Modifier.fillMaxWidth()
                            .clickable { onOpenConversation(chat.id) }
                            .padding(start = 68.dp, end = 28.dp, top = 12.dp, bottom = 12.dp),
                            color = Ink, fontSize = 16.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
