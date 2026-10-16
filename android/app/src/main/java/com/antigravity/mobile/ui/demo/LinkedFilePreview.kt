package com.antigravity.mobile.ui.demo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.imageLoader
import com.antigravity.mobile.data.model.ArtifactCommentDraftState
import com.antigravity.mobile.data.model.ArtifactTextSelection
import com.antigravity.mobile.data.model.FileContentResponse
import com.antigravity.mobile.ui.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.commonmark.node.Node
import java.net.URI

internal enum class FilePreviewKind { MARKDOWN, IMAGE, CODE, TEXT, UNSUPPORTED }

internal fun previewFileName(uri: String): String = try {
    URI(uri.replace(" ", "%20")).path.substringAfterLast('/').ifBlank { "文件" }
} catch (_: Exception) { uri.substringBefore('#').substringBefore('?').substringAfterLast('/') }

internal fun fileCodeLanguage(filename: String): String = when (val extension = filename.substringAfterLast('.', "").lowercase()) {
    "kt", "kts" -> "kotlin"
    "js", "mjs", "cjs", "jsx" -> "javascript"
    "ts", "tsx" -> "typescript"
    "py" -> "python"
    "rs" -> "rust"
    "sh", "zsh" -> "bash"
    "ps1" -> "powershell"
    "yml" -> "yaml"
    "cs" -> "csharp"
    "hpp", "cc", "h", "cxx" -> "cpp"
    "java", "json", "xml", "html", "css", "go", "c", "cpp", "sql", "swift", "dart", "yaml", "toml", "ini", "php", "lua", "diff", "properties" -> extension
    else -> if (filename.lowercase() == "dockerfile") "dockerfile" else ""
}

internal fun filePreviewKind(uri: String): FilePreviewKind {
    val filename = previewFileName(uri)
    return when (filename.substringAfterLast('.', "").lowercase()) {
        "md", "markdown", "mdown", "mkd" -> FilePreviewKind.MARKDOWN
        "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico", "avif", "heic", "heif" -> FilePreviewKind.IMAGE
        "pdf", "zip", "gz", "tar", "7z", "rar", "exe", "dll", "so", "ttf", "otf", "woff", "woff2", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "mp4", "mov", "webm", "mp3", "wav" -> FilePreviewKind.UNSUPPORTED
        else -> if (fileCodeLanguage(filename).isNotBlank()) FilePreviewKind.CODE else FilePreviewKind.TEXT
    }
}

internal fun filePreviewIcon(filename: String, kind: FilePreviewKind): Pair<ImageVector, Color> {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when {
        kind == FilePreviewKind.IMAGE -> Icons.Outlined.Image to Color(0xFFE65100)
        kind == FilePreviewKind.CODE -> Icons.Outlined.Code to Color(0xFF1565C0)
        kind == FilePreviewKind.MARKDOWN -> Icons.AutoMirrored.Outlined.Article to Color(0xFF2E7D32)
        ext in setOf("mp3", "wav", "m4a", "flac", "aac", "ogg") -> Icons.Outlined.AudioFile to Color(0xFFC2185B)
        ext in setOf("mp4", "mov", "webm", "mkv", "avi") -> Icons.Outlined.VideoFile to Color(0xFF7B1FA2)
        ext in setOf("zip", "rar", "7z", "tar", "gz") -> Icons.Outlined.FolderZip to Color(0xFFF57C00)
        ext == "pdf" -> Icons.Outlined.Description to Color(0xFFD32F2F)
        else -> Icons.Outlined.AttachFile to Color(0xFF455A64)
    }
}

internal fun fileTypeBadge(filename: String, kind: FilePreviewKind): String {
    val ext = filename.substringAfterLast('.', "").uppercase()
    return when (kind) {
        FilePreviewKind.MARKDOWN -> "Markdown 文档"
        FilePreviewKind.IMAGE -> if (ext.isNotBlank()) "$ext 图像" else "图像"
        FilePreviewKind.CODE -> {
            val lang = fileCodeLanguage(filename)
            if (lang.isNotBlank()) "${lang.replaceFirstChar { it.uppercase() }} 源码" else if (ext.isNotBlank()) "$ext 代码" else "代码"
        }
        FilePreviewKind.TEXT -> if (ext.isNotBlank()) "$ext 文本" else "文本文件"
        FilePreviewKind.UNSUPPORTED -> when (ext.lowercase()) {
            "pdf" -> "PDF 文档"
            "doc", "docx" -> "Word 文档"
            "xls", "xlsx" -> "Excel 表格"
            "ppt", "pptx" -> "PPT 演示文稿"
            "zip", "rar", "7z", "tar", "gz" -> "压缩包"
            "mp4", "mov", "webm" -> "视频文件"
            "mp3", "wav", "m4a" -> "音频文件"
            else -> if (ext.isNotBlank()) "$ext 文件" else "二进制文件"
        }
    }
}

internal fun resolveDocumentLink(base: String, target: String): String = try {
    val resolved = URI(base.replace(" ", "%20")).resolve(target.replace(" ", "%20")).normalize()
    if (resolved.scheme == "file" && resolved.rawAuthority == null)
        "file://${resolved.rawPath}" + resolved.rawQuery?.let { "?$it" }.orEmpty() + resolved.rawFragment?.let { "#$it" }.orEmpty()
    else resolved.toString()
} catch (_: Exception) { target }

internal val LocalRichImageRequest = staticCompositionLocalOf<(String) -> Any> { { it } }
internal val LocalRichImageLoader = staticCompositionLocalOf<ImageLoader?> { null }

@Composable
internal fun MarkdownImage(uri: String, title: String?) {
    val request = LocalRichImageRequest.current
    var loading by remember(uri) { mutableStateOf(true) }
    var failed by remember(uri) { mutableStateOf(false) }
    var attempt by remember(uri) { mutableStateOf(0) }
    var expanded by remember(uri) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 400.dp), contentAlignment = Alignment.Center) {
        key(uri, attempt) { AsyncImage(model = remember(uri, request, attempt) { request(uri) },
            imageLoader = LocalRichImageLoader.current ?: LocalContext.current.imageLoader,
            contentDescription = title ?: "文档图片", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().quietClickable { expanded = true },
            onLoading = { loading = true; failed = false },
            onSuccess = { loading = false; failed = false },
            onError = { loading = false; failed = true }) }
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        if (failed) TextButton(onClick = { attempt++ }) { Text("图片加载失败，重试", color = AccentBlue) }
    }
    if (expanded) ImageViewer(request(uri), LocalRichImageLoader.current ?: LocalContext.current.imageLoader,
        title ?: "文档图片", { expanded = false })
}

@Composable
internal fun LinkedFilePreview(
    uri: String,
    viewModel: ChatViewModel,
    listState: LazyListState? = null,
    fullScreen: Boolean = false,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val isPlanReview = fullScreen && state.canProceed && state.proceedArtifactUri == uri
    val kind = remember(uri) { filePreviewKind(uri) }
    val imageRequest = remember(uri, viewModel) { { target: String -> viewModel.linkedImageRequest(resolveDocumentLink(uri, target)) as Any } }
    var file by remember(uri) { mutableStateOf<FileContentResponse?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var attempt by remember(uri) { mutableStateOf(0) }
    LaunchedEffect(uri, attempt) {
        if (kind != FilePreviewKind.IMAGE && kind != FilePreviewKind.UNSUPPORTED) {
            error = null
            val result = viewModel.fetchLinkedFile(uri)
            ensureActive()
            result.fold(onSuccess = { file = it }, onFailure = { error = it.message ?: "文件读取失败" })
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val container = if (fullScreen) {
            Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding().padding(18.dp)
        } else {
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().fillMaxWidth()
                .heightIn(min = 360.dp, max = (LocalConfiguration.current.screenHeightDp * 0.86f).dp)
                .background(Color.White, RoundedCornerShape(24.dp)).padding(18.dp)
        }
        Column(container,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DisableSelection {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val displayName = file?.filename ?: previewFileName(uri)
                    val (icon, tint) = remember(displayName, kind) { filePreviewIcon(displayName, kind) }
                    Box(
                        Modifier.size(38.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            color = Ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = fileTypeBadge(displayName, kind),
                            color = SecondaryInk,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    if (kind != FilePreviewKind.IMAGE && kind != FilePreviewKind.UNSUPPORTED) {
                        file?.let {
                            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                CopyTextButton(it.content, "复制文件原文")
                            }
                        }
                    }
                    Box(
                        Modifier.size(36.dp).background(Color(0xFFF1F3F5), CircleShape)
                            .semantics { contentDescription = "关闭文件预览" }
                            .quietClickable(CircleShape, onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (isPlanReview) {
                Text(
                    "点选要修改的段落即可定位评论；反馈会先交给代理修订，不会开始执行。",
                    color = SecondaryInk,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    kind == FilePreviewKind.IMAGE -> FileImagePreview(uri, viewModel, Modifier.fillMaxSize())
                    kind == FilePreviewKind.UNSUPPORTED -> Text(
                        unsupportedPreviewHint(previewFileName(uri)),
                        color = SecondaryInk,
                        lineHeight = 22.sp,
                    )
                    error != null -> Column {
                        Text(error.orEmpty(), color = Color(0xFFB3261E))
                        TextButton(onClick = { attempt++ }) { Text("重试", color = AccentBlue) }
                    }
                    file != null -> CompositionLocalProvider(LocalRichImageRequest provides imageRequest, LocalRichImageLoader provides viewModel.mediaImageLoader) {
                        FileTextPreview(
                            file = file!!,
                            modifier = Modifier.fillMaxSize(),
                            listState = listState,
                            commentSelection = state.planCommentDraft.activeSelection.takeIf { isPlanReview },
                            onCommentSelection = if (isPlanReview) viewModel::selectPlanComment else null,
                        )
                    }
                    else -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
            }
            LinkedFileActions(
                uri = uri,
                filename = file?.filename ?: previewFileName(uri),
                showOpen = true,
                viewModel = viewModel,
            )
            if (isPlanReview) {
                AnimatedVisibility(
                    visible = state.planCommentDraft.comments.isNotEmpty() ||
                        state.planCommentDraft.activeSelection != null || state.planCommentError != null,
                ) {
                    PlanCommentEditor(
                        draft = state.planCommentDraft,
                        isSubmitting = state.isSubmittingPlanComments,
                        error = state.planCommentError,
                        onCommentChange = viewModel::setPlanComment,
                        onAdd = viewModel::addPlanComment,
                        onRemove = viewModel::removePlanComment,
                        onCancelSelection = viewModel::clearPlanCommentSelection,
                    )
                }
                DisableSelection {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        val readyCount = state.planCommentDraft.comments.size +
                            if (state.planCommentDraft.activeSelection != null && state.planCommentDraft.activeComment.isNotBlank()) 1 else 0
                        TextButton(
                            enabled = readyCount > 0 && !state.isSubmittingPlanComments && !state.isProceedingArtifact,
                            onClick = viewModel::submitPlanComments,
                            modifier = Modifier.semantics {
                                stateDescription = when {
                                    state.isSubmittingPlanComments -> "正在提交"
                                    readyCount == 0 -> "需要先添加评论"
                                    else -> "可以提交"
                                }
                            },
                        ) {
                            if (state.isSubmittingPlanComments) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentBlue)
                            } else Text("提交反馈${if (readyCount > 0) "（$readyCount）" else ""}", color = AccentBlue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FileTextPreview(
    file: FileContentResponse,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    commentSelection: ArtifactTextSelection? = null,
    onCommentSelection: ((ArtifactTextSelection) -> Unit)? = null,
) {
    val kind = filePreviewKind(file.filename)
    val defaultListState = rememberLazyListState()
    var blocks by remember(file.content) { mutableStateOf<List<Node>?>(null) }
    LaunchedEffect(file.content, kind) {
        if (kind == FilePreviewKind.MARKDOWN) blocks = withContext(Dispatchers.Default) { parseMarkdownBlocks(file.content) }
    }
    if (kind == FilePreviewKind.MARKDOWN && blocks == null) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentBlue) }
        return
    }
    SelectionContainer(modifier) {
        LazyColumn(
            modifier = Modifier.semantics { contentDescription = "文件内容" },
            state = listState ?: defaultListState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                kind == FilePreviewKind.MARKDOWN -> itemsIndexed(blocks.orEmpty()) { _, block ->
                    val target = remember(file.content, block) { planTextSelection(file.content, block) }
                    if (onCommentSelection != null && target != null) {
                        val selected = commentSelection?.startLine == target.startLine && commentSelection.endLine == target.endLine
                        val background by animateColorAsState(
                            if (selected) AccentBlue.copy(alpha = .10f) else Color.Transparent,
                            tween(160),
                            label = "plan-comment-selection",
                        )
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .background(background, shape)
                                .border(1.dp, if (selected) AccentBlue.copy(alpha = .55f) else Color.Transparent, shape)
                                .semantics { contentDescription = planLineLabel(target) }
                                .quietClickable(shape = shape) { onCommentSelection(target) }
                                .padding(10.dp),
                        ) { MarkdownBlock(block) }
                    } else MarkdownBlock(block)
                }
                kind == FilePreviewKind.CODE -> item { CodeBlock(file.content, fileCodeLanguage(file.filename)) }
                file.content.any { it == '\u0000' } -> item { Text("此文件不是可预览的文本", color = SecondaryInk) }
                else -> item { Text(file.content, color = Ink, fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 21.sp) }
            }
        }
    }
}

@Composable
private fun FileImagePreview(uri: String, viewModel: ChatViewModel, modifier: Modifier) {
    ZoomableImage(remember(uri) { viewModel.linkedImageRequest(uri.substringBefore('#')) }, viewModel.mediaImageLoader, "文件图片预览", modifier)
}

internal fun planTextSelection(source: String, node: Node): ArtifactTextSelection? {
    val spans = node.sourceSpans.takeIf { it.isNotEmpty() } ?: return null
    val startLine = spans.minOf { it.lineIndex }
    val endLine = spans.maxOf { it.lineIndex } + 1
    val lines = source.split('\n')
    if (startLine !in lines.indices || endLine <= startLine) return null
    val content = lines.subList(startLine, endLine.coerceAtMost(lines.size)).joinToString("\n") { it.removeSuffix("\r") }
    return content.takeIf { it.isNotBlank() }?.let { ArtifactTextSelection(it, startLine, endLine) }
}

private fun planLineLabel(selection: ArtifactTextSelection): String =
    if (selection.endLine == selection.startLine + 1) "评论第 ${selection.startLine + 1} 行"
    else "评论第 ${selection.startLine + 1} 至 ${selection.endLine} 行"

@Composable
private fun PlanCommentEditor(
    draft: ArtifactCommentDraftState,
    isSubmitting: Boolean,
    error: String?,
    onCommentChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onCancelSelection: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 260.dp)
            .background(Color(0xFFF5F8FC), RoundedCornerShape(16.dp))
            .animateContentSize(tween(180))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (draft.comments.isNotEmpty()) {
            Text("待提交评论 ${draft.comments.size} 条", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            draft.comments.forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(planLineLabel(item.selection), color = AccentBlue, fontSize = 12.sp)
                        Text(item.comment, color = Ink, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(enabled = !isSubmitting, onClick = { onRemove(index) }) {
                        Text("移除", color = Color(0xFFB3261E), fontSize = 13.sp)
                    }
                }
            }
        }
        AnimatedVisibility(draft.activeSelection != null) {
            draft.activeSelection?.let { selection ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(planLineLabel(selection), color = AccentBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(selection.content.replace('\n', ' ').trim(), color = SecondaryInk, fontSize = 12.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    BasicTextField(
                        value = draft.activeComment,
                        onValueChange = onCommentChange,
                        enabled = !isSubmitting,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 15.sp, lineHeight = 21.sp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp)
                            .background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp)
                            .semantics { contentDescription = "方案评论内容" },
                        decorationBox = { inner ->
                            Box {
                                if (draft.activeComment.isBlank()) Text("告诉代理这里需要怎样修改", color = SecondaryInk, fontSize = 15.sp)
                                inner()
                            }
                        },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(enabled = !isSubmitting, onClick = onCancelSelection) { Text("取消", color = SecondaryInk) }
                        TextButton(enabled = draft.activeComment.isNotBlank() && !isSubmitting, onClick = onAdd) {
                            Text("加入评论", color = AccentBlue, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = Color(0xFFB3261E), fontSize = 13.sp) }
    }
}
