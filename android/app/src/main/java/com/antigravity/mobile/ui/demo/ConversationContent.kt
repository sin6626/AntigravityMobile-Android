package com.antigravity.mobile.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.ui.chat.ChatViewModel

@Composable
internal fun ConversationContent(
    viewModel: ChatViewModel,
    messages: List<GatewayMessageItem>,
    isLoading: Boolean,
    hasMore: Boolean,
    isLoadingOlder: Boolean,
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.id, messages.lastOrNull()?.effectiveText?.length) {
        if (messages.isNotEmpty() && listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset < 80
        ) listState.scrollToItem(0)
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp),
    ) {
        if (isLoading && messages.isEmpty()) {
            item { CircularProgressIndicator(color = AccentBlue) }
        }
        itemsIndexed(messages.asReversed(), key = { index, message -> message.id.ifBlank { "${message.type}-$index" } }) { _, message ->
            MessageRow(message, viewModel)
        }
        if (hasMore) {
            item {
                Text(
                    if (isLoadingOlder) "正在加载…" else "加载更早消息",
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !isLoadingOlder, onClick = onLoadOlder)
                        .padding(vertical = 10.dp),
                    color = AccentBlue,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun MessageRow(message: GatewayMessageItem, viewModel: ChatViewModel) {
    val text = message.effectiveText.trim()
    if (message.isUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                modifier = Modifier.background(Color(0xFFE5F2FF), RoundedCornerShape(23.dp))
                    .padding(horizontal = 17.dp, vertical = 13.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MessageImages(message, viewModel)
                if (text.isNotBlank()) Text(text, color = Color(0xFF163E63),
                    fontSize = 17.sp, lineHeight = 26.sp)
            }
        }
    } else if (message.isTools) {
        Text(
            text = message.toolNames?.joinToString(" · ") ?: "工具执行中",
            color = SecondaryInk,
            fontSize = 14.sp,
        )
    } else if (message.isError) {
        Text(text.ifBlank { "请求失败" }, color = Color(0xFFB3261E), fontSize = 16.sp)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MessageImages(message, viewModel)
            if (text.isNotBlank()) MarkdownBody(text)
        }
    }
}

@Composable
private fun MarkdownBody(source: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        source.split(Regex("\\n\\s*\\n")).forEachIndexed { index, rawBlock ->
            if (index > 0) Spacer(Modifier.height(13.dp))
            val block = rawBlock.trim()
            val headingLevel = block.takeWhile { it == '#' }.length
            val isHeading = headingLevel in 1..4 && block.getOrNull(headingLevel) == ' '
            val isCode = block.startsWith("```")
            val visible = when {
                isHeading -> block.drop(headingLevel).trimStart()
                isCode -> block.removePrefix("```").substringAfter('\n', "").removeSuffix("```").trimEnd()
                else -> block.replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
            }
            Text(
                text = styledText(visible),
                modifier = Modifier.fillMaxWidth().then(
                    if (isCode) Modifier.background(Color(0xFFF3F3F3), RoundedCornerShape(12.dp))
                        .padding(12.dp) else Modifier
                ),
                color = Ink,
                fontSize = if (isHeading) (24 - headingLevel).sp else if (isCode) 15.sp else 18.sp,
                lineHeight = if (isHeading) 30.sp else 29.sp,
                fontWeight = if (isHeading) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = if (isCode) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}

private fun styledText(source: String) = buildAnnotatedString {
    val marker = Regex("\\*\\*([^*]+)\\*\\*|`([^`]+)`")
    var cursor = 0
    marker.findAll(source).forEach { match ->
        append(source.substring(cursor, match.range.first))
        val bold = match.groups[1]?.value
        val code = match.groups[2]?.value
        if (bold != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
        } else if (code != null) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFF2F2F2))) {
                append(code)
            }
        }
        cursor = match.range.last + 1
    }
    append(source.substring(cursor))
}
