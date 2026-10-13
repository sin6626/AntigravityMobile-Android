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
import com.antigravity.mobile.data.model.QueuedMessageItem
import com.antigravity.mobile.ui.demo.AccentBlue
import com.antigravity.mobile.ui.demo.Ink
import com.antigravity.mobile.ui.demo.SecondaryInk
import com.antigravity.mobile.ui.demo.quietClickable

@Composable
fun QueuedMessagesPanel(
    messages: List<QueuedMessageItem>,
    busyIds: Set<String>,
    error: String?,
    onSendNow: (QueuedMessageItem) -> Unit,
    onEdit: (QueuedMessageItem) -> Unit,
    onDelete: (QueuedMessageItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
            .background(Color(0xFFF4F7FB), shape)
            .animateContentSize(tween(180))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { stateDescription = "${messages.size} 条消息等待发送" },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { contentDescription = if (expanded) "收起排队消息" else "展开排队消息" }
                .quietClickable(shape = RoundedCornerShape(14.dp)) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("排队消息", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("${messages.size} 条等待当前任务空闲", color = SecondaryInk, fontSize = 13.sp)
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
                Modifier.fillMaxWidth().heightIn(max = 292.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                messages.forEach { message ->
                    val busy = message.id in busyIds
                    Column(
                        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp))
                            .animateContentSize(tween(180)).padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            message.text.ifBlank { "图片消息" },
                            color = Ink,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val attachmentCount = maxOf(message.media.orEmpty().size, message.imageUrls.orEmpty().size)
                        if (attachmentCount > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text("$attachmentCount 张图片", color = SecondaryInk, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        if (busy) {
                            Row(
                                Modifier.fillMaxWidth().height(48.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = AccentBlue, strokeWidth = 2.dp)
                                Text("处理中…", color = SecondaryInk, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                            }
                        } else {
                            Row(Modifier.fillMaxWidth()) {
                                QueueAction("立即发送", message, AccentBlue, onSendNow, Modifier.weight(1f))
                                QueueAction("编辑", message, Ink, onEdit, Modifier.weight(1f))
                                QueueAction("删除", message, Color(0xFFB3261E), onDelete, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueAction(
    label: String,
    message: QueuedMessageItem,
    color: Color,
    action: (QueuedMessageItem) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier.heightIn(min = 48.dp)
            .semantics { contentDescription = "$label ${message.text.ifBlank { "图片消息" }}" }
            .quietClickable(shape = RoundedCornerShape(12.dp)) { action(message) },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
