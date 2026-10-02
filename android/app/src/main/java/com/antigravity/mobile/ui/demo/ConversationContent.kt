package com.antigravity.mobile.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.antigravity.mobile.data.model.FileContentResponse
import com.antigravity.mobile.ui.chat.ChatViewModel
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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.antigravity.mobile.data.service.MathSymbolProcessor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import android.provider.Settings

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ConversationContent(
    viewModel: ChatViewModel,
    messages: List<GatewayMessageItem>,
    streamingMessageId: String?,
    isLoading: Boolean,
    hasMore: Boolean,
    isLoadingOlder: Boolean,
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier,
    bottomSpace: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val listState = rememberLazyListState(cacheWindow = remember {
        LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 0.5f)
    })
    var renderItems by remember { mutableStateOf<List<ConversationRenderItem>>(emptyList()) }
    LaunchedEffect(messages, streamingMessageId) {
        val previous = renderItems
        renderItems = withContext(Dispatchers.Default) {
            buildConversationRenderItems(messages, streamingMessageId, previous)
        }
    }
    if ((isLoading && messages.isEmpty()) || (messages.isNotEmpty() && renderItems.isEmpty())) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentBlue)
        }
        return
    }
    LaunchedEffect(renderItems.lastOrNull()?.key, renderItems.lastOrNull()?.message?.effectiveText?.length) {
        if (renderItems.isNotEmpty() && listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset < 80
        ) listState.scrollToItem(0)
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = bottomSpace),
    ) {
        itemsIndexed(renderItems.asReversed(), key = { _, item -> item.key },
            contentType = { _, item -> item.node?.javaClass?.name ?: item.message.type }) { index, item ->
            Box(Modifier.padding(top = when {
                index == renderItems.lastIndex -> 0.dp
                item.blockIndex == 0 -> 25.dp
                else -> 12.dp
            })) {
                MessageRow(item.message, viewModel, item.streaming, item.node, item.blockIndex == 0)
            }
        }
        if (hasMore) {
            item {
                Text(
                    if (isLoadingOlder) "正在加载…" else "加载更早消息",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 25.dp).quietClickable(enabled = !isLoadingOlder, onClick = onLoadOlder)
                        .padding(vertical = 10.dp),
                    color = AccentBlue,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

internal data class ConversationRenderItem(
    val message: GatewayMessageItem,
    val messageKey: String,
    val node: Node?,
    val blockIndex: Int,
    val streaming: Boolean,
    val key: String,
)

internal fun buildConversationRenderItems(
    messages: List<GatewayMessageItem>,
    streamingMessageId: String?,
    previous: List<ConversationRenderItem> = emptyList(),
): List<ConversationRenderItem> {
    val existing = previous.groupBy { it.messageKey }
    return buildList {
        messages.forEachIndexed { index, message ->
            val messageKey = message.id.ifBlank { "${message.type}-$index" }
            val streaming = message.id == streamingMessageId
            val cached = existing[messageKey]
            if (cached?.firstOrNull()?.let { it.message == message && it.streaming == streaming } == true) {
                addAll(cached)
            } else {
                val blocks = if (message.isAgent && message.type != "thought" && !streaming && message.effectiveText.isNotBlank()) {
                    // ponytail: top-level blocks are lazy; split individual huge tables/lists only if traces show they remain slow.
                    generateSequence(markdownCache.getOrParse(message.effectiveText.trim()).firstChild) { it.next }.toList()
                } else emptyList()
                if (blocks.isEmpty()) add(ConversationRenderItem(message, messageKey, null, 0, streaming, messageKey))
                else blocks.forEachIndexed { blockIndex, node ->
                    // Retain the bottom item's identity when a streaming reply becomes Markdown.
                    val key = if (blockIndex == blocks.lastIndex) messageKey else "$messageKey:markdown:$blockIndex"
                    add(ConversationRenderItem(message, messageKey, node, blockIndex, false, key))
                }
            }
        }
    }
}

@Composable
private fun MessageRow(message: GatewayMessageItem, viewModel: ChatViewModel, isStreaming: Boolean, markdownNode: Node? = null, showImages: Boolean = true) {
    val text = message.effectiveText.trim()
    val scope = rememberCoroutineScope()
    var linkedFile by remember(message.id) { mutableStateOf<FileContentResponse?>(null) }
    var linkedFileError by remember(message.id) { mutableStateOf<String?>(null) }
    var loadingFile by remember(message.id) { mutableStateOf(false) }
    CompositionLocalProvider(LocalRichLinkAction provides { url ->
        when {
            url.startsWith("conversation://") -> url.removePrefix("conversation://").substringBefore('/').takeIf(String::isNotBlank)?.let(viewModel::openConversation)
            url.startsWith("file://") -> scope.launch {
                loadingFile = true
                linkedFileError = null
                viewModel.fetchLinkedFile(url).fold(onSuccess = { linkedFile = it }, onFailure = { linkedFileError = it.message ?: "文件读取失败" })
                loadingFile = false
            }
        }
    }) {
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
            if (showImages) MessageImages(message, viewModel)
            if (markdownNode != null) MarkdownBlock(markdownNode)
            else if (text.isNotBlank()) {
                if (isStreaming) StreamingMessageText(message.id, text) else MarkdownBody(text)
            }
        }
    }
    }
    if (linkedFile != null || linkedFileError != null || loadingFile) {
        Dialog(onDismissRequest = { linkedFile = null; linkedFileError = null; loadingFile = false }) {
            Column(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(linkedFile?.filename ?: "文件", color = Ink, fontWeight = FontWeight.Bold)
                Text(linkedFileError ?: if (loadingFile) "加载中…" else linkedFile?.content.orEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                    color = Ink, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text("关闭", color = AccentBlue, modifier = Modifier.quietClickable { linkedFile = null; linkedFileError = null; loadingFile = false })
            }
        }
    }
}

@Composable
private fun StreamingMessageText(id: String, source: String) {
    val context = LocalContext.current
    val animate = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    var shown by rememberSaveable(id) { mutableStateOf("") }
    val reveal = remember(id) { StreamingTextReveal(shown) }
    LaunchedEffect(id, source, animate) {
        reveal.receive(source, animate, System.nanoTime() / 1_000_000)
        shown = reveal.shown
        while (reveal.hasPending) {
            delay(16)
            reveal.advance(System.nanoTime() / 1_000_000)
            shown = reveal.shown
        }
    }
    Text(shown, color = Ink, fontSize = 18.sp, lineHeight = 27.sp)
}

private val LocalRichLinkAction = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
private fun MarkdownBody(source: String) {
    val document = remember(source) { markdownCache.getOrParse(source) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        var node = document.firstChild
        while (node != null) {
            MarkdownBlock(node)
            node = node.next
        }
    }
}

private val markdownCache = object {
    private val documents = object : LinkedHashMap<String, Node>(80, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Node>): Boolean = size > 80
    }

    fun getOrParse(source: String): Node {
        synchronized(documents) { documents[source]?.let { return it } }
        val parser = Parser.builder().extensions(
            listOf(TablesExtension.create(), StrikethroughExtension.create())
        ).build()
        val parsed = parser.parse(normalizeBlockMath(source))
        synchronized(documents) { documents[source] = parsed }
        return parsed
    }
}

@Composable
private fun StepPanel(message: GatewayMessageItem) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
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
                    val statusLabel = detail.status.removePrefix("CORTEX_STEP_STATUS_").lowercase()
                    Text("${detail.summary.ifBlank { detail.name }}${statusLabel.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}", color = Ink, fontSize = 14.sp)
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
            if (inlineText(node).text.contains('$')) MathParagraph(node)
            else if (images.isNotEmpty()) {
                if (node.firstChild !is Image || node.firstChild?.next != null) MarkdownText(node)
                images.forEach { image -> AsyncImage(model = image.destination, contentDescription = image.title, modifier = Modifier.fillMaxWidth()) }
            } else MarkdownText(node)
        }
        is FencedCodeBlock -> when (node.info.trim().lowercase()) {
            "mermaid" -> MermaidBlock(node.literal)
            "katex" -> MathBlock(node.literal)
            "carousel" -> CarouselBlock(node.literal)
            else -> CodeBlock(node.literal, node.info)
        }
        is IndentedCodeBlock -> CodeBlock(node.literal, "")
        is BlockQuote -> AlertBlock(node)
        is BulletList -> MarkdownChildren(node)
        is OrderedList -> {
            var index = node.startNumber
            var child = node.firstChild
            while (child != null) {
                Row { Text("${index++}.  ", color = Ink); Column { MarkdownChildren(child) } }
                child = child.next
            }
        }
        is ListItem -> MarkdownListItem(node)
        is org.commonmark.ext.gfm.tables.TableBlock -> Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { MarkdownChildren(node) }
        is org.commonmark.ext.gfm.tables.TableHead, is org.commonmark.ext.gfm.tables.TableBody -> MarkdownChildren(node)
        is org.commonmark.ext.gfm.tables.TableRow -> Row { var cell = node.firstChild; while (cell != null) { Text(inlineText(cell), modifier = Modifier.widthIn(min = 110.dp).padding(8.dp), color = Ink); cell = cell.next } }
        is ThematicBreak -> Spacer(Modifier.fillMaxWidth().height(1.dp).background(SecondaryInk))
        else -> MarkdownChildren(node)
    }
}

@Composable
private fun CarouselBlock(source: String) {
    val slides = remember(source) { source.split(Regex("(?m)^\\s*<!-- slide -->\\s*$")).map(String::trim).filter(String::isNotBlank) }
    var selected by remember(source) { mutableStateOf(0) }
    if (slides.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF2F5FA), RoundedCornerShape(12.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MarkdownBody(slides[selected])
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("‹ 上一页", color = AccentBlue, fontSize = 15.sp, modifier = Modifier.quietClickable(enabled = selected > 0) { selected-- })
            Text("${selected + 1} / ${slides.size}", color = SecondaryInk, fontSize = 14.sp)
            Text("下一页 ›", color = AccentBlue, fontSize = 15.sp, modifier = Modifier.quietClickable(enabled = selected < slides.lastIndex) { selected++ })
        }
    }
}

@Composable
private fun AlertBlock(node: BlockQuote) {
    val first = node.firstChild as? Paragraph
    val content = first?.let(::inlineText)
    val marker = Regex("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)](?:\\n)?", RegexOption.IGNORE_CASE).find(content?.text.orEmpty())
    val label = marker?.groupValues?.get(1)?.uppercase()
    val background = when (label) {
        "TIP" -> Color(0xFFEAF7EE)
        "IMPORTANT" -> Color(0xFFEEEAFE)
        "WARNING", "CAUTION" -> Color(0xFFFFF2E6)
        else -> Color(0xFFF2F5FA)
    }
    Column(modifier = Modifier.fillMaxWidth().background(background, RoundedCornerShape(8.dp)).padding(12.dp)) {
        if (label != null) Text(label, fontWeight = FontWeight.Bold, color = Ink, fontSize = 14.sp)
        if (marker != null && content != null) {
            val body = content.subSequence(marker.range.last + 1, content.length)
            if (body.isNotBlank()) MarkdownAnnotatedText(body)
            var child = first.next
            while (child != null) { MarkdownBlock(child); child = child.next }
        } else MarkdownChildren(node)
    }
}

@Composable
private fun MarkdownListItem(node: ListItem) {
    val first = node.firstChild as? Paragraph
    val content = first?.let(::inlineText)
    val marker = Regex("^\\[([xX ])]\\s*").find(content?.text.orEmpty())
    Row {
        Text(if (marker == null) "•  " else if (marker.groupValues[1].isBlank()) "☐  " else "☑  ", color = Ink)
        Column {
            if (marker != null && content != null) {
                MarkdownAnnotatedText(content.subSequence(marker.range.last + 1, content.length))
                var child = first.next
                while (child != null) { MarkdownBlock(child); child = child.next }
            } else MarkdownChildren(node)
        }
    }
}

@Composable
private fun MathParagraph(node: Paragraph) {
    val source = remember(node) { inlineText(node) }
    val formulas = remember(source) { findInlineMath(source) }
    val density = LocalDensity.current
    val bitmaps = formulas.map { rememberMathBitmap(it.expression, with(density) { 18.sp.toPx() }, false) }
    val text = buildAnnotatedString {
        var cursor = 0
        formulas.forEachIndexed { index, formula ->
            append(source.subSequence(cursor, formula.start))
            if (bitmaps[index] == null) append(MathSymbolProcessor.cleanMathExpression(formula.expression))
            else appendInlineContent("formula-$index", formula.expression)
            cursor = formula.endExclusive
        }
        append(source.subSequence(cursor, source.length))
    }
    val inline = bitmaps.mapIndexedNotNull { index, bitmap ->
        bitmap ?: return@mapIndexedNotNull null
        val scale = density.density * density.fontScale
        "formula-$index" to InlineTextContent(
            Placeholder((bitmap.width / scale).sp, (bitmap.height / scale).sp, PlaceholderVerticalAlign.Center)
        ) { ComposeImage(bitmap.asImageBitmap(), contentDescription = formulas[index].expression, modifier = Modifier.fillMaxSize()) }
    }.toMap()
    Text(text, color = Ink, fontSize = 18.sp, lineHeight = 27.sp, inlineContent = inline)
}

@Composable
private fun MathBlock(expression: String) {
    val density = LocalDensity.current
    val bitmap = rememberMathBitmap(expression, with(density) { 19.sp.toPx() }, true)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        if (bitmap == null) Text(MathSymbolProcessor.cleanMathExpression(expression), color = Ink, fontSize = 19.sp)
        else ComposeImage(bitmap.asImageBitmap(), contentDescription = expression,
            modifier = Modifier.width(with(density) { bitmap.width.toDp() }).height(with(density) { bitmap.height.toDp() }))
    }
}

private fun normalizeBlockMath(source: String): String {
    val lines = source.lines()
    val output = StringBuilder()
    var index = 0
    var fence = ""
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            if (fence.isEmpty()) fence = trimmed.take(3) else if (trimmed.startsWith(fence)) fence = ""
        }
        val close = when { trimmed == "\$\$" -> "\$\$"; trimmed == "\\[" -> "\\]"; else -> "" }
        if (fence.isEmpty() && close.isNotEmpty()) {
            val end = (index + 1 until lines.size).firstOrNull { lines[it].trim() == close }
            if (end != null) {
                output.append("\n```katex\n")
                for (lineIndex in index + 1 until end) output.append(lines[lineIndex]).append('\n')
                output.append("```\n")
                index = end + 1
                continue
            }
        }
        output.append(if (fence.isEmpty()) line.replace("\\$", "\uE000") else line).append('\n')
        index++
    }
    return output.toString()
}

@Composable
private fun MarkdownChildren(node: Node) {
    var child = node.firstChild
    while (child != null) { MarkdownBlock(child); child = child.next }
}

@Composable
private fun CodeBlock(code: String, language: String) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFF3F3F3), RoundedCornerShape(10.dp)).padding(12.dp)) {
        if (language.isNotBlank()) Text(language, color = SecondaryInk, fontSize = 12.sp)
        Text(code.trimEnd(), color = Ink, fontSize = 14.sp, lineHeight = 21.sp,
            fontFamily = FontFamily.Monospace, softWrap = false,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()))
    }
}

@Composable
private fun MermaidBlock(source: String) {
    val bitmap = rememberMermaidBitmap(source)
    val density = LocalDensity.current
    if (bitmap == null) CodeBlock(source, "mermaid")
    else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        ComposeImage(bitmap.asImageBitmap(), contentDescription = "Mermaid 图表",
            modifier = Modifier.width(with(density) { bitmap.width.toDp() }).height(with(density) { bitmap.height.toDp() }))
    }
}

@Composable
private fun MarkdownText(node: Node, size: androidx.compose.ui.unit.TextUnit = 18.sp, weight: FontWeight = FontWeight.Normal) {
    val value = remember(node) { inlineText(node) }
    MarkdownAnnotatedText(value, size, weight)
}

@Composable
private fun MarkdownAnnotatedText(value: AnnotatedString, size: androidx.compose.ui.unit.TextUnit = 18.sp, weight: FontWeight = FontWeight.Normal) {
    val uriHandler = LocalUriHandler.current
    val richLinkAction = LocalRichLinkAction.current
    ClickableText(text = value, style = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = size, lineHeight = (size.value * 1.5f).sp, fontWeight = weight), onClick = { offset ->
        value.getStringAnnotations("URL", offset, offset).firstOrNull()?.item?.let { url ->
            if (url.startsWith("https://") || url.startsWith("http://")) uriHandler.openUri(url)
            else richLinkAction(url)
        }
    })
}

private fun inlineText(parent: Node): AnnotatedString = buildAnnotatedString {
    fun appendNode(node: Node) {
        when (node) {
            is org.commonmark.node.Text -> {
                val parts = node.literal.split('\uE000')
                parts.forEachIndexed { index, part ->
                    if (index > 0) { pushStringAnnotation("ESCAPED_DOLLAR", "1"); append("$"); pop() }
                    append(part)
                }
            }
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
