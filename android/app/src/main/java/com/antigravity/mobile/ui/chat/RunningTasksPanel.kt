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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.data.model.RunningTaskItem
import com.antigravity.mobile.ui.demo.AccentBlue
import com.antigravity.mobile.ui.demo.Ink
import com.antigravity.mobile.ui.demo.SecondaryInk
import com.antigravity.mobile.ui.demo.quietClickable
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun RunningTasksPanel(
    tasks: List<RunningTaskItem>,
    stoppingIds: Set<String>,
    error: String?,
    onStop: (RunningTaskItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    val now by produceState(System.currentTimeMillis(), tasks) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
            .background(Color(0xFFF4F7FB), shape)
            .animateContentSize(tween(180))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { stateDescription = "${tasks.size} 个后台任务正在执行" },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { contentDescription = if (expanded) "收起后台任务" else "展开后台任务" }
                .quietClickable(shape = RoundedCornerShape(14.dp)) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("后台任务", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("${tasks.size} 个正在执行", color = SecondaryInk, fontSize = 13.sp)
            }
            Text(if (expanded) "收起" else "展开", color = AccentBlue, fontSize = 14.sp)
        }
        error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = Color(0xFFB3261E), fontSize = 13.sp)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(180), expandFrom = Alignment.Top) + fadeIn(tween(120)),
            exit = shrinkVertically(tween(160), shrinkTowards = Alignment.Top) + fadeOut(tween(100)),
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tasks.forEach { task ->
                    val stopping = task.stableId in stoppingIds
                    Column(
                        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp))
                            .animateContentSize(tween(180)).padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(15.dp), color = AccentBlue, strokeWidth = 2.dp)
                            Text(
                                task.displayTitle,
                                modifier = Modifier.padding(start = 8.dp).weight(1f),
                                color = Ink,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            task.displayCommand.ifBlank { "等待任务输出…" },
                            color = SecondaryInk,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "运行中 · ${runningTaskDuration(task.startedAt, now)}",
                                modifier = Modifier.weight(1f),
                                color = SecondaryInk,
                                fontSize = 12.sp,
                            )
                            Box(
                                Modifier.heightIn(min = 48.dp).padding(start = 8.dp)
                                    .semantics { contentDescription = "停止后台任务 ${task.displayTitle}" }
                                    .quietClickable(RoundedCornerShape(12.dp), enabled = !stopping) { onStop(task) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (stopping) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(16.dp), color = Color(0xFFB3261E), strokeWidth = 2.dp)
                                        Text("正在停止…", color = SecondaryInk, fontSize = 13.sp, modifier = Modifier.padding(start = 7.dp))
                                    }
                                } else {
                                    Text("停止", color = Color(0xFFB3261E), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun runningTaskDuration(startedAt: String?, nowMs: Long): String {
    val start = startedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return "计时中"
    val seconds = ((nowMs - start).coerceAtLeast(0L) / 1_000L)
    val hours = seconds / 3_600
    val minutes = seconds % 3_600 / 60
    val rest = seconds % 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分"
        minutes > 0 -> "${minutes}分${rest}秒"
        else -> "${rest}秒"
    }
}
