package com.antigravity.mobile.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
        "pdf", "zip", "gz", "tar", "7z", "rar", "exe", "dll", "so", "ttf", "otf", "woff", "woff2", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "mp4", "mov", "mp3", "wav" -> FilePreviewKind.UNSUPPORTED
        else -> if (fileCodeLanguage(filename).isNotBlank()) FilePreviewKind.CODE else FilePreviewKind.TEXT
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
internal fun LinkedFilePreview(uri: String, viewModel: ChatViewModel, onDismiss: () -> Unit) {
    val kind = remember(uri) { filePreviewKind(uri) }
    if (kind == FilePreviewKind.IMAGE) {
        ImageViewer(viewModel.linkedImageRequest(uri.substringBefore('#')), viewModel.mediaImageLoader,
            "文件图片预览", onDismiss)
        return
    }
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
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .height((LocalConfiguration.current.screenHeightDp * 0.85f).dp)
            .background(Color.White, RoundedCornerShape(20.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DisableSelection {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(file?.filename ?: previewFileName(uri), color = Ink, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (kind != FilePreviewKind.IMAGE && kind != FilePreviewKind.UNSUPPORTED) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            file?.let { CopyTextButton(it.content, "复制文件原文") }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    kind == FilePreviewKind.IMAGE -> FileImagePreview(uri, viewModel, Modifier.fillMaxSize())
                    kind == FilePreviewKind.UNSUPPORTED -> Text("暂不支持预览此文件类型", color = SecondaryInk)
                    error != null -> Column {
                        Text(error.orEmpty(), color = Color(0xFFB3261E))
                        TextButton(onClick = { attempt++ }) { Text("重试", color = AccentBlue) }
                    }
                    file != null -> CompositionLocalProvider(LocalRichImageRequest provides imageRequest) {
                        FileTextPreview(file!!, Modifier.fillMaxSize())
                    }
                    else -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
            }
            DisableSelection {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("关闭", color = AccentBlue) }
                }
            }
        }
    }
}

@Composable
internal fun FileTextPreview(file: FileContentResponse, modifier: Modifier = Modifier) {
    val kind = filePreviewKind(file.filename)
    var blocks by remember(file.content) { mutableStateOf<List<Node>?>(null) }
    LaunchedEffect(file.content, kind) {
        if (kind == FilePreviewKind.MARKDOWN) blocks = withContext(Dispatchers.Default) { parseMarkdownBlocks(file.content) }
    }
    if (kind == FilePreviewKind.MARKDOWN && blocks == null) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentBlue) }
        return
    }
    SelectionContainer(modifier) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                kind == FilePreviewKind.MARKDOWN -> itemsIndexed(blocks.orEmpty()) { _, block -> MarkdownBlock(block) }
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
