package com.antigravity.mobile.ui.demo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.data.model.PlanReviewPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkModeBottomSheet(
    onDismiss: () -> Unit,
    planningSelected: Boolean,
    currentCommandName: String?,
    onSelectPlanning: (Boolean) -> Unit,
    preference: PlanReviewPreference?,
    loadingPreference: Boolean,
    updatingPreference: Boolean,
    selectingMode: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSelectPolicy: (String) -> Unit,
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
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("工作方式", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
            Text("这条消息怎么开始", color = SecondaryInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            WorkModeRow(
                title = "直接执行",
                subtitle = "按当前要求开始处理",
                selected = !planningSelected,
                enabled = !selectingMode,
                description = "工作方式，直接执行",
                onClick = { onSelectPlanning(false) },
            )
            WorkModeRow(
                title = "先规划",
                subtitle = currentCommandName?.takeUnless { it.equals("plan", true) }
                    ?.let { "将替换当前 /$it 命令，先产出可审阅方案" }
                    ?: "使用电脑端 /plan 命令，先产出可审阅方案",
                selected = planningSelected,
                enabled = !selectingMode,
                description = "工作方式，先规划",
                busy = selectingMode,
                onClick = { onSelectPlanning(true) },
            )

            Text("计划审阅策略 · 电脑全局", color = SecondaryInk, fontSize = 12.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
            when {
                loadingPreference && preference == null -> Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = AccentBlue, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("正在读取电脑设置…", color = SecondaryInk, fontSize = 14.sp)
                }
                preference != null -> {
                    WorkModeRow(
                        title = "总是询问",
                        subtitle = "方案完成后由你确认，再继续执行",
                        selected = preference.policy == "ALWAYS",
                        enabled = !updatingPreference,
                        description = "计划审阅策略，总是询问",
                        busy = updatingPreference && preference.policy != "ALWAYS",
                        onClick = { onSelectPolicy("ALWAYS") },
                    )
                    WorkModeRow(
                        title = "自动继续",
                        subtitle = "方案完成后直接执行，会跳过人工确认",
                        selected = preference.policy == "TURBO",
                        enabled = !updatingPreference,
                        warning = true,
                        description = "计划审阅策略，自动继续",
                        busy = updatingPreference && preference.policy != "TURBO",
                        onClick = { onSelectPolicy("TURBO") },
                    )
                }
            }
            error?.let {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, color = Color(0xFFB3261E), fontSize = 13.sp, lineHeight = 18.sp,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetry, enabled = !loadingPreference && !updatingPreference && !selectingMode) {
                        Text("重试", color = AccentBlue)
                    }
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("完成", color = AccentBlue, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun WorkModeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    description: String,
    busy: Boolean = false,
    warning: Boolean = false,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        if (selected) (if (warning) Color(0xFFFFF4E5) else AccentBlue.copy(alpha = .08f)) else Color(0xFFF7F7F7),
        tween(180), label = "work mode background",
    )
    val outline by animateColorAsState(
        if (selected) (if (warning) Color(0xFFE58A00) else AccentBlue) else Color.Transparent,
        tween(180), label = "work mode outline",
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clip(RoundedCornerShape(18.dp))
            .background(background).border(1.dp, outline, RoundedCornerShape(18.dp))
            .quietClickable(RoundedCornerShape(18.dp), enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
                role = Role.RadioButton
            }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Ink else SecondaryInk.copy(alpha = .55f),
                fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = SecondaryInk, fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = AccentBlue, strokeWidth = 2.dp)
        else Box(
            Modifier.size(20.dp).border(1.5.dp, if (selected) outline else Color(0xFFB9B9B9), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).background(outline, CircleShape))
        }
    }
}
