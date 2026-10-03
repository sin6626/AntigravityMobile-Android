package com.antigravity.mobile.ui.demo

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.ui.chat.ChatViewModel
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun MessageImages(message: GatewayMessageItem, viewModel: ChatViewModel) {
    val count = maxOf(message.media?.size ?: 0, message.imageUrls?.size ?: 0)
    if (count == 0) return
    var selected by remember(message.id) { mutableStateOf<Int?>(null) }
    val thumbnails = remember(message.media) { message.media.orEmpty().map { data ->
        try { Base64.decode(data.substringAfter("base64,"), Base64.DEFAULT) } catch (_: IllegalArgumentException) { null }
    } }
    val originals = remember(message.imageUrls, viewModel) { message.imageUrls.orEmpty().map(viewModel::mediaImageRequest) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
        repeat(count) { index ->
            val thumbnail = thumbnails.getOrNull(index)
            val original = originals.getOrNull(index)
            var useFull by remember(thumbnail, original) { mutableStateOf(thumbnail == null) }
            var attempt by remember(message.id, index) { mutableIntStateOf(0) }
            var loading by remember(attempt) { mutableStateOf(true) }
            var failed by remember(attempt) { mutableStateOf(false) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).background(androidx.compose.ui.graphics.Color(0xFFF3F3F3)),
                    contentAlignment = Alignment.Center) {
                    key(attempt, useFull) {
                        AsyncImage(model = if (useFull) original ?: thumbnail else thumbnail, imageLoader = viewModel.mediaImageLoader,
                            contentDescription = "聊天图片，点击放大", contentScale = ContentScale.Crop,
                            onLoading = { loading = true; failed = false }, onSuccess = { loading = false; failed = false },
                            onError = { loading = false; failed = true; if (!useFull && original != null) useFull = true },
                            modifier = Modifier.fillMaxSize().quietClickable { selected = index })
                    }
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = AccentBlue, strokeWidth = 2.dp)
                    if (failed) Text("图片加载失败", color = SecondaryInk, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                }
                if (failed) TextButton(onClick = { attempt++ }) { Text("重试", color = AccentBlue) }
            }
        }
    }
    selected?.let { index ->
        ImageViewer(originals.getOrNull(index) ?: thumbnails.getOrNull(index), viewModel.mediaImageLoader,
            "聊天图片", { selected = null }, index, count, { selected = it })
    }
}

@Composable
internal fun ImageViewer(model: Any?, loader: ImageLoader, title: String, onClose: () -> Unit,
    index: Int = 0, count: Int = 1, onSelect: (Int) -> Unit = {}) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .height((LocalConfiguration.current.screenHeightDp * 0.85f).dp)
            .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(20.dp)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (count > 1) "$title · ${index + 1}/$count" else title, color = Ink, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("关闭", color = AccentBlue) }
            }
            key(index, model) { ZoomableImage(model, loader, title, Modifier.weight(1f)) }
            if (count > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = index > 0, onClick = { onSelect(index - 1) }, colors = ButtonDefaults.textButtonColors(contentColor = AccentBlue)) { Text("上一张") }
                TextButton(enabled = index + 1 < count, onClick = { onSelect(index + 1) }, colors = ButtonDefaults.textButtonColors(contentColor = AccentBlue)) { Text("下一张") }
            }
        }
    }
}

@Composable
internal fun ZoomableImage(model: Any?, loader: ImageLoader, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fullSizeRequest = remember(model, context) {
        (if (model is ImageRequest) model.newBuilder() else ImageRequest.Builder(context).data(model))
            .size(Size.ORIGINAL).build()
    }
    var attempt by remember(model) { mutableIntStateOf(0) }
    var loading by remember(model, attempt) { mutableStateOf(true) }
    var failed by remember(model, attempt) { mutableStateOf(false) }
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember(model) { mutableStateOf(IntSize.Zero) }
    val fit = if (imageSize.width > 0 && imageSize.height > 0) min(viewport.width.toFloat() / imageSize.width, viewport.height.toFloat() / imageSize.height) else 1f
    val maxScale = if (fit > 0) max(8f, 2f / fit).coerceAtMost(64f) else 8f
    fun transform(zoom: Float, pan: Offset = Offset.Zero, focus: Offset = Offset.Zero) {
        val next = (scale * zoom).coerceIn(1f, maxScale)
        val moved = (offset - focus) * (next / scale) + focus + pan
        val x = max(0f, (imageSize.width * fit * next - viewport.width) / 2)
        val y = max(0f, (imageSize.height * fit * next - viewport.height) / 2)
        offset = Offset(moved.x.coerceIn(-x, x), moved.y.coerceIn(-y, y)); scale = next
    }
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).onSizeChanged { viewport = it }
            .pointerInput(model, attempt, viewport, imageSize) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (!loading && !failed) transform(zoom, pan, centroid - Offset(viewport.width / 2f, viewport.height / 2f))
                }
            }, contentAlignment = Alignment.Center) {
            key(model, attempt) {
                AsyncImage(model = fullSizeRequest, imageLoader = loader, contentDescription = description, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                        .semantics { stateDescription = if (failed) "加载失败" else if (loading) "加载中" else "已加载" },
                    onLoading = { loading = true; failed = false }, onSuccess = {
                        loading = false; failed = false
                        imageSize = IntSize(it.result.drawable.intrinsicWidth.coerceAtLeast(1), it.result.drawable.intrinsicHeight.coerceAtLeast(1))
                    }, onError = { loading = false; failed = true })
            }
            if (loading) CircularProgressIndicator(Modifier.size(28.dp), color = AccentBlue, strokeWidth = 2.dp)
            if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("图片加载失败", color = SecondaryInk)
                TextButton(onClick = { scale = 1f; offset = Offset.Zero; attempt++ }) { Text("重试", color = AccentBlue) }
            }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(enabled = !loading && !failed && scale > 1f, onClick = { transform(0.5f) }, colors = ButtonDefaults.textButtonColors(contentColor = AccentBlue)) { Text("缩小") }
            Text("${(scale * 100).toInt()}%", color = SecondaryInk, modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically))
            TextButton(enabled = !loading && !failed && scale < maxScale, onClick = { transform(2f) }, colors = ButtonDefaults.textButtonColors(contentColor = AccentBlue)) { Text("放大") }
            TextButton(onClick = { scale = 1f; offset = Offset.Zero }, enabled = scale > 1f, colors = ButtonDefaults.textButtonColors(contentColor = AccentBlue)) { Text("重置") }
        }
    }
}
