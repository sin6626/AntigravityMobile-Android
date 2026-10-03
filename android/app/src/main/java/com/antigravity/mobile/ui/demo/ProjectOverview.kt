package com.antigravity.mobile.ui.demo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    error: String? = null,
    onRetry: () -> Unit = {},
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
        if (error != null) item { LoadFailure(error, isLoading, onRetry) }
        if (projects.isEmpty() && error == null) {
            item {
                Text(if (isLoading) "正在加载项目…" else "暂无项目",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                    color = SecondaryInk, fontSize = 16.sp)
            }
        }
        items(projects.distinctBy { it.navigationKey() }, key = { it.navigationKey() }) { project ->
            val projectKey = project.navigationKey()
            val projectChats = conversations.filter { !it.isSubagent && it.belongsTo(project, projects) }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (projectKey in expandedProjects) "已展开" else "已折叠" }.quietClickable {
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
                AnimatedVisibility(projectKey in expandedProjects,
                    enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                    if (projectChats.isEmpty()) {
                        Text("暂无对话", modifier = Modifier.padding(start = 68.dp, bottom = 13.dp),
                            color = SecondaryInk, fontSize = 15.sp)
                    }
                    projectChats.forEach { chat ->
                        Text(chat.displayTitle, modifier = Modifier.fillMaxWidth()
                            .quietClickable { onOpenConversation(chat.id) }
                            .padding(start = 68.dp, end = 28.dp, top = 12.dp, bottom = 12.dp),
                            color = Ink, fontSize = 16.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    }
                }
            }
        }
    }
}
