package com.antigravity.mobile.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.data.model.SubagentItem
import com.antigravity.mobile.ui.demo.AccentBlue
import com.antigravity.mobile.ui.demo.Ink
import com.antigravity.mobile.ui.demo.SecondaryInk
import com.antigravity.mobile.ui.demo.quietClickable

@Composable
fun SubagentsPanel(
    subagents: List<SubagentItem>,
    isLoading: Boolean,
    error: String?,
    onOpen: (SubagentItem) -> Unit,
    onRetry: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
            .background(Color(0xFFF4F7FB), shape)
            .animateContentSize(tween(180))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { stateDescription = if (error != null && subagents.isEmpty()) "子代理状态尚未同步" else "${subagents.size} 个直属子代理" },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { contentDescription = if (expanded) "收起子代理" else "展开子代理" }
                .quietClickable(RoundedCornerShape(14.dp)) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("子代理", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(if (error != null && subagents.isEmpty()) "状态尚未同步" else "${subagents.size} 个直属子代理", color = SecondaryInk, fontSize = 13.sp)
            }
            if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentBlue)
            else Text(if (expanded) "收起" else "展开", color = AccentBlue, fontSize = 14.sp)
        }
        error?.let {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("子代理状态暂未更新", Modifier.weight(1f), color = SecondaryInk, fontSize = 13.sp)
                Text(
                    "重试",
                    Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp)
                        .semantics { contentDescription = "重试加载子代理" }
                        .quietClickable(RoundedCornerShape(12.dp), onClick = onRetry),
                    color = AccentBlue,
                    fontSize = 14.sp,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded && subagents.isNotEmpty(),
            enter = expandVertically(tween(180), expandFrom = Alignment.Top) + fadeIn(tween(120)),
            exit = shrinkVertically(tween(160), shrinkTowards = Alignment.Top) + fadeOut(tween(100)),
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                subagents.forEach { subagent ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .semantics { contentDescription = "查看子代理 ${subagent.displayTitle}，${subagent.displayStatus}" }
                            .quietClickable(RoundedCornerShape(16.dp)) { onOpen(subagent) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(subagent.displayTitle, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                listOfNotNull(
                                    subagent.displayStatus,
                                    subagent.stepCount.takeIf { it > 0 }?.let { "$it 步" },
                                ).joinToString(" · "),
                                color = when (subagent.displayStatus) {
                                    "运行中" -> AccentBlue
                                    "等待处理" -> Color(0xFF9A6700)
                                    "执行失败" -> Color(0xFFB3261E)
                                    else -> SecondaryInk
                                },
                                fontSize = 13.sp,
                            )
                        }
                        Text("查看", color = AccentBlue, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SubagentReadOnlyBar() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
            .background(Color(0xFFF4F7FB), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .semantics { contentDescription = "子代理执行记录，仅供查看" },
    ) {
        Text("子代理执行记录", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text("当前可查看完整过程；直接发消息将在接口确认后开放", color = SecondaryInk, fontSize = 13.sp)
    }
}
