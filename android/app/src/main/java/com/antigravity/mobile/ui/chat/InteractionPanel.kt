package com.antigravity.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.data.model.InteractionOption
import com.antigravity.mobile.data.model.PendingInteraction
import com.antigravity.mobile.ui.demo.AccentBlue
import com.antigravity.mobile.ui.demo.Ink
import com.antigravity.mobile.ui.demo.SecondaryInk
import com.antigravity.mobile.ui.demo.quietClickable

@Composable
fun PlanReviewPanel(
    filename: String,
    conversationTitle: String,
    isProceeding: Boolean,
    error: String?,
    onView: () -> Unit,
    onProceed: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
            .background(Color(0xFFF4F7FB), shape)
            .border(1.dp, Color(0xFFE1E8F0), shape)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Text("方案等待审阅", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text("来自：${conversationTitle.ifBlank { "当前会话" }}", color = SecondaryInk, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("文件：$filename", color = SecondaryInk, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFB3261E), fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(48.dp)
                    .quietClickable(shape = RoundedCornerShape(14.dp), onClick = onView),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Text("查看方案", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Box(
                Modifier.weight(1f).height(48.dp)
                    .background(if (isProceeding) AccentBlue.copy(alpha = .55f) else AccentBlue, RoundedCornerShape(14.dp))
                    .semantics { stateDescription = if (isProceeding) "正在提交" else "可以继续执行" }
                    .quietClickable(shape = RoundedCornerShape(14.dp), enabled = !isProceeding, onClick = onProceed),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                if (isProceeding) {
                    CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("继续执行", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun InteractionPanel(
    interaction: PendingInteraction,
    isSubmitting: Boolean,
    onChoose: (InteractionOption?, String) -> Unit,
) {
    var writeIn by remember(interaction.stepIndex) { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
            .background(Color(0xFFF6F6F6), RoundedCornerShape(22.dp))
            .heightIn(max = 310.dp).verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(interaction.title.ifBlank { "需要你的选择" }, color = Ink, fontSize = 17.sp)
        if (interaction.description.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(interaction.description, color = SecondaryInk, fontSize = 14.sp)
        }
        if (interaction.target.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(interaction.target, color = SecondaryInk, fontSize = 13.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (interaction.isMultiSelect) {
            Spacer(Modifier.height(12.dp))
            Text("该多选问题请在电脑端处理", color = SecondaryInk, fontSize = 14.sp)
            return@Column
        }
        Spacer(Modifier.height(12.dp))
        interaction.options.forEach { option ->
            Text(
                option.text,
                modifier = Modifier.fillMaxWidth()
                    .quietClickable(enabled = !isSubmitting) { onChoose(option, writeIn) }
                    .padding(vertical = 10.dp),
                color = if (option.isDeny) Color(0xFFB3261E) else Ink,
                fontSize = 15.sp,
            )
        }
        if (interaction.hasWriteIn) {
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = writeIn,
                onValueChange = { writeIn = it },
                modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                decorationBox = { inner ->
                    androidx.compose.foundation.layout.Box {
                        if (writeIn.isBlank()) {
                            Text(interaction.writeInPlaceholder.ifBlank { "填写回复" },
                                color = SecondaryInk, fontSize = 14.sp)
                        }
                        inner()
                    }
                },
            )
            if (interaction.type == "ask_question" && writeIn.isNotBlank()) {
                Text("发送自定义回复", modifier = Modifier.quietClickable(enabled = !isSubmitting) {
                    onChoose(null, writeIn)
                }.padding(vertical = 12.dp), color = Color(0xFF0A84FF), fontSize = 15.sp)
            }
        }
    }
}
