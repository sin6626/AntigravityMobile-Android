package com.antigravity.mobile.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.antigravity.mobile.data.service.MathSymbolProcessor
import org.commonmark.parser.Parser
import org.commonmark.node.*
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import android.webkit.WebView
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

@Composable
internal fun ConversationContent(
    viewModel: ChatViewModel,
    messages: List<GatewayMessageItem>,
    isLoading: Boolean,
    hasMore: Boolean,
    isLoadingOlder: Boolean,
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier,
    bottomSpace: androidx.compose.ui.unit.Dp = 24.dp,
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
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = bottomSpace),
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
                    modifier = Modifier.fillMaxWidth().quietClickable(enabled = !isLoadingOlder, onClick = onLoadOlder)
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
    } else if (message.isTools || message.type == "thought") {
        StepPanel(message)
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
    val document = remember(source) { markdownParser.parse(source) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        var node = document.firstChild
        while (node != null) {
            MarkdownBlock(node)
            node = node.next
        }
    }
}

private val markdownParser = Parser.builder().extensions(
    listOf(TablesExtension.create(), StrikethroughExtension.create())
).build()

@Composable
private fun StepPanel(message: GatewayMessageItem) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "${message.title.ifBlank { if (message.type == "thought") "Thought" else "Worked" }}${message.duration.takeIf { it.isNotBlank() }?.let { " for $it" } ?: ""}  ${if (expanded) "⌄" else "›"}",
            color = SecondaryInk, fontSize = 15.sp,
            modifier = Modifier.quietClickable { expanded = !expanded }
        )
        if (expanded) {
            if (message.type == "thought") MarkdownBody(message.effectiveText)
            else if (message.details.isNotEmpty()) message.details.forEach { detail ->
                Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF3F3F3), RoundedCornerShape(10.dp)).padding(10.dp)) {
                    Text("${detail.summary.ifBlank { detail.name }}${detail.status.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}", color = Ink, fontSize = 14.sp)
                    if (detail.command.isNotBlank()) Text(detail.command, color = SecondaryInk, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            } else Text(message.toolNames?.joinToString(" · ") ?: message.effectiveText, color = SecondaryInk)
        }
    }
}

@Composable
private fun MarkdownBlock(node: Node) {
    when (node) {
        is Heading -> MarkdownText(node, (27 - node.level * 2).sp, FontWeight.SemiBold)
        is Paragraph -> {
            val images = generateSequence(node.firstChild) { it.next }.filterIsInstance<Image>().toList()
            if (images.isNotEmpty()) {
                if (node.firstChild !is Image || node.firstChild?.next != null) MarkdownText(node)
                images.forEach { image -> AsyncImage(model = image.destination, contentDescription = image.title, modifier = Modifier.fillMaxWidth()) }
            } else MarkdownText(node)
        }
        is FencedCodeBlock -> if (node.info.trim().equals("mermaid", ignoreCase = true)) MermaidBlock(node.literal) else CodeBlock(node.literal, node.info)
        is IndentedCodeBlock -> CodeBlock(node.literal, "")
        is BlockQuote -> Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF2F5FA), RoundedCornerShape(8.dp)).padding(12.dp)) { MarkdownChildren(node) }
        is BulletList -> MarkdownChildren(node)
        is OrderedList -> {
            var index = node.startNumber
            var child = node.firstChild
            while (child != null) {
                Row { Text("${index++}.  ", color = Ink); Column { MarkdownChildren(child) } }
                child = child.next
            }
        }
        is ListItem -> Row { Text("•  ", color = Ink); Column { MarkdownChildren(node) } }
        is org.commonmark.ext.gfm.tables.TableBlock -> Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { MarkdownChildren(node) }
        is org.commonmark.ext.gfm.tables.TableHead, is org.commonmark.ext.gfm.tables.TableBody -> MarkdownChildren(node)
        is org.commonmark.ext.gfm.tables.TableRow -> Row { var cell = node.firstChild; while (cell != null) { Text(inlineText(cell), modifier = Modifier.widthIn(min = 110.dp).padding(8.dp), color = Ink); cell = cell.next } }
        is ThematicBreak -> Spacer(Modifier.fillMaxWidth().height(1.dp).background(SecondaryInk))
        else -> MarkdownChildren(node)
    }
}

@Composable
private fun MarkdownChildren(node: Node) {
    var child = node.firstChild
    while (child != null) { MarkdownBlock(child); child = child.next }
}

@Composable
private fun CodeBlock(code: String, language: String) {
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF3F3F3), RoundedCornerShape(10.dp)).horizontalScroll(rememberScrollState()).padding(12.dp)) {
        if (language.isNotBlank()) Text(language, color = SecondaryInk, fontSize = 12.sp)
        Text(code.trimEnd(), color = Ink, fontSize = 14.sp, fontFamily = FontFamily.Monospace, softWrap = false)
    }
}

@Composable
private fun MermaidBlock(source: String) {
    val quoted = remember(source) { JSONObject.quote(source) }
    AndroidView(
        factory = { context -> WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            loadDataWithBaseURL("file:///android_asset/", """
                <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;background:#f3f3f3;overflow:auto"><div id="diagram"></div>
                <script src="mermaid.min.js"></script><script>
                mermaid.initialize({startOnLoad:false,securityLevel:'strict',theme:'default'});
                mermaid.render('diagram-svg', $quoted).then(({svg}) => { document.getElementById('diagram').innerHTML = svg; });
                </script></body></html>
            """.trimIndent(), "text/html", "UTF-8", null)
        } }, modifier = Modifier.fillMaxWidth().height(280.dp)
    )
}

@Composable
private fun MarkdownText(node: Node, size: androidx.compose.ui.unit.TextUnit = 18.sp, weight: FontWeight = FontWeight.Normal) {
    val value = remember(node) { inlineText(node) }
    val uriHandler = LocalUriHandler.current
    ClickableText(text = value, style = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = size, lineHeight = (size.value * 1.5f).sp, fontWeight = weight), onClick = { offset ->
        value.getStringAnnotations("URL", offset, offset).firstOrNull()?.item?.let { url ->
            if (url.startsWith("https://") || url.startsWith("http://")) uriHandler.openUri(url)
        }
    })
}

private fun inlineText(parent: Node): AnnotatedString = buildAnnotatedString {
    fun appendNode(node: Node) {
        when (node) {
            is org.commonmark.node.Text -> append(MathSymbolProcessor.process(node.literal))
            is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFF2F2F2))) { append(node.literal) }
            is SoftLineBreak, is HardLineBreak -> append("\n")
            is Image -> append(node.title.ifBlank { node.destination })
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { var c = node.firstChild; while (c != null) { appendNode(c); c = c.next } }
            is Emphasis -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { var c = node.firstChild; while (c != null) { appendNode(c); c = c.next } }
            is Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { var c = node.firstChild; while (c != null) { appendNode(c); c = c.next } }
            is Link -> { pushStringAnnotation("URL", node.destination); withStyle(SpanStyle(color = AccentBlue, textDecoration = TextDecoration.Underline)) { var c = node.firstChild; while (c != null) { appendNode(c); c = c.next } }; pop() }
            else -> { var c = node.firstChild; while (c != null) { appendNode(c); c = c.next } }
        }
    }
    var child = parent.firstChild
    while (child != null) { appendNode(child); child = child.next }
}
