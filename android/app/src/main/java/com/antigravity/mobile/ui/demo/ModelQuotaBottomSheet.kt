package com.antigravity.mobile.ui.demo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.data.model.ModelQuotaBucket
import com.antigravity.mobile.data.model.ModelQuotaGroup
import com.antigravity.mobile.data.model.ModelQuotaSummary
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal fun formatQuotaResetTime(
    value: String?,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String? {
    val reset = value?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    val minutes = Duration.between(now, reset).toMinutes()
    val relative = when {
        minutes <= 0 -> "即将恢复"
        minutes < 60 -> "${minutes.coerceAtLeast(1)} 分钟后恢复"
        minutes < 24 * 60 -> "${minutes / 60} 小时${(minutes % 60).takeIf { it > 0 }?.let { " $it 分钟" }.orEmpty()}后恢复"
        else -> "${minutes / (24 * 60)} 天${((minutes % (24 * 60)) / 60).takeIf { it > 0 }?.let { " $it 小时" }.orEmpty()}后恢复"
    }
    val local = reset.atZone(zoneId)
    val pattern = if (local.toLocalDate() == now.atZone(zoneId).toLocalDate()) "HH:mm" else "M月d日 HH:mm"
    return "$relative · ${local.format(DateTimeFormatter.ofPattern(pattern, Locale.SIMPLIFIED_CHINESE))}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelQuotaBottomSheet(
    onDismiss: () -> Unit,
    summary: ModelQuotaSummary?,
    loading: Boolean,
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
        }
    }
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
                Text("模型额度", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center))
                Box(
                    Modifier.align(Alignment.CenterEnd).size(48.dp)
                        .quietClickable(RoundedCornerShape(14.dp), enabled = !loading && !refreshing, onClick = onRefresh)
                        .semantics { contentDescription = if (refreshing) "正在刷新模型额度" else "刷新模型额度" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), color = AccentBlue, strokeWidth = 2.dp)
                    else Text("刷新", color = if (loading) SecondaryInk.copy(alpha = .4f) else AccentBlue,
                        fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Text("同组模型共享额度；短任务和更省资源的模型通常消耗更慢。",
                color = SecondaryInk, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 12.dp))
            when {
                loading && summary == null -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = AccentBlue, strokeWidth = 2.dp)
                        Text("正在读取电脑额度…", color = SecondaryInk, fontSize = 14.sp)
                    }
                }
                summary == null -> QuotaError(error ?: "电脑未返回可用额度", onRefresh)
                else -> LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (summary.groups.none { it.buckets.isNotEmpty() }) item {
                        Text("当前账号没有可展示的模型额度", color = SecondaryInk, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp))
                    }
                    items(summary.groups.filter { it.buckets.isNotEmpty() }) { group ->
                        QuotaGroup(group, Instant.ofEpochMilli(nowMillis))
                    }
                    if (error != null) item { QuotaError(error, onRefresh) }
                    item { Spacer(Modifier.height(2.dp)) }
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
private fun QuotaGroup(group: ModelQuotaGroup, now: Instant) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFF7F7F7), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(quotaGroupLabel(group), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        group.buckets.forEach { QuotaBucketRow(it, now) }
    }
}

@Composable
private fun QuotaBucketRow(bucket: ModelQuotaBucket, now: Instant) {
    val fraction = bucket.remainingFraction?.coerceIn(0.0, 1.0)?.toFloat()
    val animated by animateFloatAsState(fraction ?: 0f, tween(220), label = "quota progress")
    val targetColor = when {
        fraction == null || bucket.disabled -> SecondaryInk.copy(alpha = .35f)
        fraction <= .10f -> Color(0xFFD93025)
        fraction <= .25f -> Color(0xFFE58A00)
        else -> Color(0xFF188038)
    }
    val color by animateColorAsState(targetColor, tween(180), label = "quota color")
    val label = quotaBucketLabel(bucket)
    val percent = fraction?.let { "${(it * 100).roundToInt().coerceIn(0, 100)}%" } ?: "—"
    val reset = formatQuotaResetTime(bucket.resetTime, now)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics {
            contentDescription = "$label，剩余 $percent${reset?.let { "，$it" }.orEmpty()}"
        },
        verticalArrangement = Arrangement.Center,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (bucket.disabled) SecondaryInk else Ink, fontSize = 14.sp,
                modifier = Modifier.weight(1f))
            Text(if (bucket.disabled) "暂不可用" else percent, color = color, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFFE2E2E2), RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(animated).height(5.dp).background(color, RoundedCornerShape(3.dp)))
        }
        reset?.let {
            Text(it, color = SecondaryInk, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun QuotaError(message: String, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).background(Color(0xFFFFF2F1), RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = Color(0xFFB3261E), fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)
            .semantics { contentDescription = "重试刷新模型额度" }) { Text("重试", color = AccentBlue) }
    }
}

private fun quotaGroupLabel(group: ModelQuotaGroup): String = when {
    group.displayName.contains("Gemini", true) -> "Gemini 模型"
    group.displayName.contains("Claude", true) || group.displayName.contains("GPT", true) -> "Claude 与 GPT 模型"
    else -> group.displayName.ifBlank { "模型组" }
}

private fun quotaBucketLabel(bucket: ModelQuotaBucket): String = when (bucket.window.lowercase()) {
    "weekly" -> "每周额度"
    "5h" -> "5 小时额度"
    else -> bucket.displayName.ifBlank { bucket.bucketId.ifBlank { "共享额度" } }
}
