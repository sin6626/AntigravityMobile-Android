package com.antigravity.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.data.model.InteractionOption
import com.antigravity.mobile.data.model.PendingInteraction
import com.antigravity.mobile.ui.demo.Ink
import com.antigravity.mobile.ui.demo.SecondaryInk
import com.antigravity.mobile.ui.demo.quietClickable

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
