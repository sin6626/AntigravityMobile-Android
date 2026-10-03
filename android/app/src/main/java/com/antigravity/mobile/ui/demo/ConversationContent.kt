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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
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
import com.antigravity.mobile.ui.chat.ChatViewModel
import org.commonmark.parser.Parser
import org.commonmark.node.*
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.antigravity.mobile.data.service.MathSymbolProcessor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import android.provider.Settings
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ConversationContent(
    viewModel: ChatViewModel,
    messages: List<GatewayMessageItem>,
    streamingMessageId: String?,
    isRunning: Boolean,
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
    val scope = rememberCoroutineScope()
    var positioned by remember { mutableStateOf(false) }
    var expandedProcesses by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var renderItems by remember { mutableStateOf<List<ConversationRenderItem>>(emptyList()) }
    val newestLocalId = messages.lastOrNull { it.id.startsWith("local:") }?.id
    var lastScrolledLocalId by remember { mutableStateOf<String?>(null) }
    var historyAnchor by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val nearBottom by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let {
                it.index == listState.layoutInfo.totalItemsCount - 1 &&
                    it.offset + it.size <= listState.layoutInfo.viewportEndOffset - listState.layoutInfo.afterContentPadding + 80
            } ?: true
        }
    }
    var followLatest by remember { mutableStateOf(true) }
    var followAfterJump by remember { mutableStateOf(false) }
    var linkedFileUri by rememberSaveable { mutableStateOf<String?>(null) }
    val richImageRequest = remember(viewModel) { { target: String -> viewModel.linkedImageRequest(target) as Any } }
    val richLinkAction = remember(viewModel) {
        { target: String ->
            val url = linkedFileUri?.let { resolveDocumentLink(it, target) } ?: target
            when {
                url.startsWith("conversation://") -> {
                    linkedFileUri = null
                    url.removePrefix("conversation://").substringBefore('/').takeIf(String::isNotBlank)?.let(viewModel::openConversation)
                }
                url.startsWith("file://") -> linkedFileUri = url
            }
        }
    }
    LaunchedEffect(messages, streamingMessageId, isRunning) {
        followLatest = !positioned || (nearBottom && (expandedProcesses.isEmpty() || followAfterJump))
        val previous = renderItems
        val prepared = withContext(Dispatchers.Default) {
            buildConversationRenderItems(messages, streamingMessageId, previous, isRunning)
        }
        historyAnchor?.let { (key, offset) ->
            val index = prepared.indexOfFirst { it.key == key }
            if (index >= 0) {
                listState.requestScrollToItem(index + (if (hasMore) 1 else 0), offset)
                historyAnchor = null
                followLatest = false
            }
        }
        renderItems = prepared
    }
    if ((isLoading && messages.isEmpty()) || (messages.isNotEmpty() && renderItems.isEmpty())) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentBlue)
        }
        return
    }
    LaunchedEffect(renderItems, isRunning) {
        val newLocal = newestLocalId != null && newestLocalId != lastScrolledLocalId &&
            renderItems.any { it.message.id == newestLocalId }
        if (renderItems.isNotEmpty() && (!positioned || followLatest || newLocal)) {
            val lastIndex = (if (hasMore) 1 else 0) + renderItems.lastIndex + (if (isRunning) 1 else 0)
            listState.scrollToItem(lastIndex)
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let { listState.scrollBy(it.size.toFloat()) }
            positioned = true
            if (newLocal) lastScrolledLocalId = newestLocalId
        }
    }
    CompositionLocalProvider(
        LocalRichLinkAction provides richLinkAction,
        LocalRichImageRequest provides richImageRequest,
        LocalRichImageLoader provides viewModel.mediaImageLoader,
    ) {
        Box(modifier) {
        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize().alpha(if (positioned || messages.isEmpty()) 1f else 0f),
                state = listState,
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = bottomSpace),
            ) {
                if (hasMore) item(key = "load-older", contentType = "load-older") {
                    DisableSelection {
                        Text(if (isLoadingOlder) "正在加载…" else "加载更早消息",
                            modifier = Modifier.fillMaxWidth().quietClickable(enabled = !isLoadingOlder) {
                                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key != "load-older" }?.let {
                                    historyAnchor = it.key.toString() to -it.offset
                                }
                                onLoadOlder()
                            }
                                .padding(vertical = 10.dp), color = AccentBlue, fontSize = 15.sp)
                    }
                }
                itemsIndexed(renderItems, key = { _, item -> item.key },
                    contentType = { _, item -> if (item.process.isNotEmpty()) "process" else item.node?.javaClass?.name ?: item.message.type }) { index, item ->
                    val entryModifier = if (item.message.id.startsWith("local:")) {
                        var entered by rememberSaveable(item.key) { mutableStateOf(false) }
                        LaunchedEffect(item.key) { entered = true }
                        val alpha by animateFloatAsState(if (entered) 1f else 0f, tween(180), label = "sentMessage")
                        Modifier.graphicsLayer { this.alpha = alpha }
                    } else Modifier
                    Column(entryModifier.padding(top = if (item.blockIndex == 0) 25.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.process.isNotEmpty()) {
                            val expanded = item.key in expandedProcesses
                            val toggle = {
                                followAfterJump = false
                                expandedProcesses = if (expanded) expandedProcesses - item.key else expandedProcesses + item.key
                            }
                            if (item.processRunning) DisableSelection { ExecutionPanel(item, viewModel, expanded, toggle) }
                            else ExecutionPanel(item, viewModel, expanded, toggle)
                        } else if (item.streaming) DisableSelection {
                            MessageRow(item.message, viewModel, true, item.node, item.blockIndex == 0)
                        } else MessageRow(item.message, viewModel, false, item.node, item.blockIndex == 0)
                        if (item.canCopy) DisableSelection {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                CopyTextButton(item.message.effectiveText, "复制整条回复")
                            }
                        }
                    }
                }
                if (isRunning) item(key = "reply-progress", contentType = "reply-progress") {
                    DisableSelection {
                        Text("正在回复…", color = SecondaryInk, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
        AnimatedVisibility(visible = positioned && !nearBottom,
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = -bottomSpace),
            enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
            Surface(shape = CircleShape, color = Color.White, shadowElevation = 3.dp) {
                Box(Modifier.size(44.dp).semantics { contentDescription = "回到最新消息" }
                    .quietClickable(CircleShape) {
                        scope.launch {
                            followLatest = true
                            followAfterJump = true
                            val lastIndex = listState.layoutInfo.totalItemsCount - 1
                            if (lastIndex >= 0) {
                                listState.animateScrollToItem(lastIndex)
                                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let { listState.scrollBy(it.size.toFloat()) }
                            }
                        }
                    }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Ink)
                }
            }
        }
        }
        linkedFileUri?.let { uri -> LinkedFilePreview(uri, viewModel) { linkedFileUri = null } }
    }
}

internal data class ConversationRenderItem(
    val message: GatewayMessageItem,
    val messageKey: String,
    val node: Node?,
    val blockIndex: Int,
    val streaming: Boolean,
    val key: String,
    val process: List<GatewayMessageItem> = emptyList(),
    val executionItems: List<ExecutionRenderItem> = emptyList(),
    val processRunning: Boolean = false,
) {
    val canCopy: Boolean
        get() = process.isEmpty() && !streaming && key == messageKey && message.isAgent && message.type != "thought" && message.effectiveText.isNotBlank()
}

@Composable
internal fun CopyTextButton(source: String, description: String) {
    val clipboard = LocalClipboardManager.current
    var copies by remember { mutableIntStateOf(0) }
    LaunchedEffect(copies) {
        if (copies > 0) {
            delay(1800)
            copies = 0
        }
    }
    Box(Modifier.size(44.dp).semantics {
        contentDescription = description
        role = Role.Button
        stateDescription = if (copies > 0) "已复制" else "未复制"
        liveRegion = LiveRegionMode.Polite
    }
        .quietClickable { clipboard.setText(AnnotatedString(source)); copies++ }, contentAlignment = Alignment.Center) {
        if (copies > 0) Icon(Icons.Default.Check, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
        else Symbol("content_copy", size = 20, color = SecondaryInk)
    }
}

internal fun buildConversationRenderItems(
    messages: List<GatewayMessageItem>,
    streamingMessageId: String?,
    previous: List<ConversationRenderItem> = emptyList(),
    isRunning: Boolean = false,
): List<ConversationRenderItem> {
    val existing = previous.groupBy { it.messageKey }
    val previousProcessKeys = previous.flatMap { item -> item.process.map { it.id to item.key } }.toMap()
    return buildList {
        fun appendMessage(message: GatewayMessageItem) {
            val messageKey = message.id.ifBlank { "${message.type}-${messages.indexOf(message)}" }
            val streaming = message.id == streamingMessageId
            val cached = existing[messageKey]
            if (cached?.firstOrNull()?.let { it.process.isEmpty() && it.message == message && it.streaming == streaming } == true) {
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
        var start = 0
        while (start < messages.size) {
            val end = (start + 1 until messages.size).firstOrNull { messages[it].isUser } ?: messages.size
            val turn = messages.subList(start, end)
            val user = turn.firstOrNull()?.takeIf { it.isUser }
            if (user != null) appendMessage(user)
            val body = if (user != null) turn.drop(1) else turn
            val answer = body.indexOfLast { it.isAgent && it.type != "thought" }
                .takeIf { idx -> idx >= 0 && body.drop(idx + 1).none { it.isTools || it.type == "thought" } } ?: -1
            val process = if (answer >= 0) body.take(answer) else body.take(body.indexOfLast { !it.isError } + 1)
            if (process.isNotEmpty()) {
                val key = process.firstNotNullOfOrNull { previousProcessKeys[it.id] }
                    ?: "process:${user?.id ?: process.first().id}"
                val running = isRunning && end == messages.size
                val cached = existing[key]?.singleOrNull()
                if (cached != null && cached.process == process && cached.processRunning == running) add(cached)
                else add(ConversationRenderItem(process.first(), key, null, 0, false, key,
                    process, buildExecutionRenderItems(process), running))
            }
            if (answer >= 0) body.drop(answer).forEach(::appendMessage)
            else body.drop(process.size).forEach(::appendMessage)
            start = end
        }
    }
}

@Composable
private fun MessageRow(message: GatewayMessageItem, viewModel: ChatViewModel, isStreaming: Boolean, markdownNode: Node? = null, showImages: Boolean = true) {
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

internal val LocalRichLinkAction = staticCompositionLocalOf<(String) -> Unit> { {} }

internal fun parseMarkdownBlocks(source: String): List<Node> =
    generateSequence(markdownCache.getOrParse(source).firstChild) { it.next }.toList()

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

internal data class ExecutionRenderItem(
    val key: String,
    val message: GatewayMessageItem,
    val heading: Boolean = false,
    val node: Node? = null,
    val detail: com.antigravity.mobile.data.model.GatewayStepDetail? = null,
    val images: Boolean = false,
)

internal fun buildExecutionRenderItems(messages: List<GatewayMessageItem>): List<ExecutionRenderItem> = buildList {
    messages.forEachIndexed { index, message ->
        val key = message.id.ifBlank { "step-$index" }
        add(ExecutionRenderItem("$key:heading", message, heading = true))
        if (message.isTools) {
            if (message.details.isNotEmpty()) message.details.forEachIndexed { detailIndex, detail ->
                add(ExecutionRenderItem("$key:tool:$detailIndex", message, detail = detail))
            } else add(ExecutionRenderItem("$key:legacy", message))
        } else if (message.isError) {
            add(ExecutionRenderItem("$key:error", message))
        } else {
            if (!message.media.isNullOrEmpty() || !message.imageUrls.isNullOrEmpty() || message.imageDataList.isNotEmpty())
                add(ExecutionRenderItem("$key:images", message, images = true))
            generateSequence(markdownCache.getOrParse(message.effectiveText).firstChild) { it.next }
                .forEachIndexed { block, node -> add(ExecutionRenderItem("$key:block:$block", message, node = node)) }
        }
    }
}

@Composable
private fun ExecutionPanel(item: ConversationRenderItem, viewModel: ChatViewModel, expanded: Boolean, onToggle: () -> Unit) {
    val thoughts = item.process.count { it.type == "thought" }
    val tools = item.process.filter { it.isTools }.sumOf { it.toolCount ?: it.details.size }
    val detailState = rememberLazyListState()
    Column(Modifier.fillMaxWidth().background(Color(0xFFF6F7F9), RoundedCornerShape(12.dp))) {
        DisableSelection {
            Row(Modifier.fillMaxWidth().semantics {
                contentDescription = "执行过程"
                stateDescription = if (expanded) "已展开" else "已折叠"
                role = Role.Button
            }.quietClickable(onClick = onToggle).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (item.processRunning) "执行过程 · 进行中" else "执行过程", color = Ink, fontSize = 15.sp)
                    Text("$thoughts 段思考 · $tools 项操作", color = SecondaryInk, fontSize = 12.sp)
                }
                Text(if (expanded) "⌄" else "›", color = SecondaryInk, fontSize = 20.sp)
            }
        }
        AnimatedVisibility(visible = expanded,
            enter = expandVertically(tween(220), expandFrom = Alignment.Top) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(220), shrinkTowards = Alignment.Top) + fadeOut(tween(150))) {
            // A bounded lazy viewport avoids composing an entire coding run on expansion.
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), state = detailState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(item.executionItems, key = { it.key }, contentType = { if (it.heading) "heading" else it.node?.javaClass?.name ?: "tool" }) { entry ->
                    when {
                        entry.images -> MessageImages(entry.message, viewModel)
                        entry.heading -> {
                            val duration = entry.message.duration.takeIf { entry.message.type == "thought" && it != "0s" && it.isNotBlank() }
                            Text(when {
                                entry.message.isTools -> "工具操作"
                                entry.message.type == "thought" -> "思考${duration?.let { " · $it" }.orEmpty()}"
                                entry.message.isError -> "执行异常"
                                else -> "进度说明"
                            }, color = SecondaryInk, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                        entry.node != null -> MessageRow(entry.message, viewModel, false, entry.node, false)
                        entry.detail != null -> {
                            val detail = entry.detail
                            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).padding(10.dp)) {
                                Text(detail.summary.ifBlank { detail.name }, color = Ink, fontSize = 14.sp)
                                if (detail.status.isNotBlank()) Text(if (detail.status == "CORTEX_STEP_STATUS_DONE") "已完成" else detail.status.removePrefix("CORTEX_STEP_STATUS_"), color = SecondaryInk, fontSize = 12.sp)
                                if (detail.command.isNotBlank()) CodeBlock(detail.command, "")
                            }
                        }
                        else -> Text(entry.message.toolNames?.joinToString(" · ") ?: entry.message.effectiveText,
                            color = if (entry.message.isError) Color(0xFFB3261E) else SecondaryInk, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MarkdownBlock(node: Node) {
    when (node) {
        is Heading -> MarkdownText(node, (27 - node.level * 2).sp, FontWeight.SemiBold)
        is Paragraph -> {
            val images = generateSequence(node.firstChild) { it.next }.filterIsInstance<Image>().toList()
            if (inlineText(node).text.contains('$')) MathParagraph(node)
            else if (images.isNotEmpty()) {
                if (node.firstChild !is Image || node.firstChild?.next != null) MarkdownText(node)
                images.forEach { image -> MarkdownImage(image.destination, image.title) }
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
        is org.commonmark.ext.gfm.tables.TableRow -> Row {
            var cell = node.firstChild
            while (cell != null) {
                MarkdownAnnotatedText(inlineText(cell), size = 16.sp,
                    weight = if (node.parent is org.commonmark.ext.gfm.tables.TableHead) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.width(180.dp).padding(8.dp))
                cell = cell.next
            }
        }
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
    MarkdownAnnotatedText(text, inlineContent = inline)
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
internal fun CodeBlock(code: String, language: String) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFF3F3F3), RoundedCornerShape(10.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(language, color = SecondaryInk, fontSize = 12.sp)
            DisableSelection { CopyTextButton(code, "复制代码") }
        }
        Text(rememberHighlightedCode(code.trimEnd(), language), color = Ink, fontSize = 14.sp, lineHeight = 21.sp,
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
private fun MarkdownAnnotatedText(value: AnnotatedString, size: androidx.compose.ui.unit.TextUnit = 18.sp,
    weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier,
    inlineContent: Map<String, InlineTextContent> = emptyMap()) {
    val uriHandler = LocalUriHandler.current
    val richLinkAction = LocalRichLinkAction.current
    val selectable = remember(value, uriHandler, richLinkAction) {
        buildAnnotatedString {
            append(value)
            value.getStringAnnotations("URL", 0, value.length).forEach { link ->
                addLink(LinkAnnotation.Clickable(link.item) {
                    if (link.item.startsWith("https://") || link.item.startsWith("http://")) uriHandler.openUri(link.item)
                    else richLinkAction(link.item)
                }, link.start, link.end)
            }
        }
    }
    val codeRanges = remember(value) { value.getStringAnnotations("INLINE_CODE", 0, value.length) }
    val layout = remember(value) { if (codeRanges.isEmpty()) null else mutableStateOf<TextLayoutResult?>(null) }
    // Draw code backgrounds before native selection highlights, not inside glyph painting.
    val background = if (layout == null) modifier else modifier.drawBehind {
        layout.value?.let { result ->
            codeRanges.forEach { drawPath(result.getPathForRange(it.start, it.end), Color(0xFFF2F2F2)) }
        }
    }
    Text(text = selectable, color = Ink, fontSize = size, lineHeight = (size.value * 1.5f).sp,
        fontWeight = weight, modifier = background, inlineContent = inlineContent,
        onTextLayout = { layout?.value = it })
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
            is Code -> {
                pushStringAnnotation("INLINE_CODE", "1")
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(node.literal) }
                pop()
            }
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
