package com.antigravity.mobile.ui.demo

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.data.model.decodeImagePreview
import com.antigravity.mobile.ui.chat.ChatViewModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

@Composable
internal fun MessageImages(message: GatewayMessageItem, viewModel: ChatViewModel) {
    val attachmentImages = remember(message.attachments) {
        message.attachments.orEmpty().filter { it.mimeType.startsWith("image/") }
    }
    val count = maxOf(
        message.imageDataList.size,
        message.media?.size ?: 0,
        message.imageUrls?.size ?: 0,
        attachmentImages.size
    )
    if (count == 0) return
    var selected by remember(message.id) { mutableStateOf<Int?>(null) }
    val thumbnails = remember(message.imageDataList, message.media) {
        List(maxOf(message.imageDataList.size, message.media.orEmpty().size)) { index ->
            val remote = message.media?.getOrNull(index)?.let(::decodeImagePreview)
            remote ?: message.imageDataList.getOrNull(index)?.takeIf { it.isNotEmpty() }
        }
    }
    val originals = remember(message.imageUrls, attachmentImages, viewModel) {
        List(count) { index ->
            val fromUrl = message.imageUrls?.getOrNull(index)?.let(viewModel::mediaImageRequest)
            if (fromUrl != null) fromUrl
            else {
                val attUri = attachmentImages.getOrNull(index)?.uri
                if (!attUri.isNullOrBlank()) viewModel.linkedImageRequest(attUri) else null
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
        repeat(count) { index ->
            val thumbnail = thumbnails.getOrNull(index)
            val original = originals.getOrNull(index)
            var useFull by remember(thumbnail, original) { mutableStateOf(thumbnail == null) }
            var attempt by remember(message.id, index) { mutableIntStateOf(0) }
            var loading by remember(attempt) { mutableStateOf(true) }
            var failed by remember(attempt) { mutableStateOf(false) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3F3F3)),
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
        ImageViewer(
            model = originals.getOrNull(index) ?: thumbnails.getOrNull(index),
            loader = viewModel.mediaImageLoader,
            title = "聊天图片",
            onClose = { selected = null },
            index = index,
            count = count,
            onSelect = { selected = it },
            modelAt = { originals.getOrNull(it) ?: thumbnails.getOrNull(it) },
            thumbnailAt = { thumbnails.getOrNull(it) },
        )
    }
}

@Composable
internal fun ImageViewer(
    model: Any?,
    loader: ImageLoader,
    title: String,
    onClose: () -> Unit,
    index: Int = 0,
    count: Int = 1,
    onSelect: (Int) -> Unit = {},
    modelAt: (Int) -> Any? = { model },
    thumbnailAt: (Int) -> Any? = { null },
) {
    val pager = rememberPagerState(initialPage = index, pageCount = { count })
    val scope = rememberCoroutineScope()
    var zoomed by remember { mutableStateOf(false) }
    val height = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    fun select(page: Int) {
        if (page in 0 until count) scope.launch { pager.animateScrollToPage(page) }
    }
    LaunchedEffect(pager.settledPage) { zoomed = false; onSelect(pager.settledPage) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(height)
                .clip(RoundedCornerShape(20.dp)).background(Color.White)
        ) {
            HorizontalPager(
                pager,
                userScrollEnabled = !zoomed,
                modifier = Modifier.fillMaxSize().semantics {
                    customActions = buildList {
                        add(CustomAccessibilityAction("关闭图片") { onClose(); true })
                        if (pager.currentPage > 0) add(CustomAccessibilityAction("上一张图片") { select(pager.currentPage - 1); true })
                        if (pager.currentPage + 1 < count) add(CustomAccessibilityAction("下一张图片") { select(pager.currentPage + 1); true })
                    }
                }
            ) { page ->
                key(page, page == pager.settledPage) {
                    ZoomableImage(
                        model = modelAt(page),
                        loader = loader,
                        description = if (count == 1) title else "$title ${page + 1}/$count",
                        modifier = Modifier.fillMaxSize(),
                        thumbnailModel = thumbnailAt(page),
                        onZoom = { if (page == pager.currentPage) zoomed = it },
                        onEdgeSwipe = { direction -> select(page + direction) }
                    )
                }
            }
            // 右上角半透明关闭按钮
            Box(
                modifier = Modifier.padding(12.dp).align(Alignment.TopEnd).size(36.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .semantics { contentDescription = "关闭图片" }
                    .quietClickable(CircleShape, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun ZoomableImage(
    model: Any?,
    loader: ImageLoader,
    description: String,
    modifier: Modifier = Modifier,
    thumbnailModel: Any? = null,
    onZoom: (Boolean) -> Unit = {},
    onEdgeSwipe: (Int) -> Unit = {},
) {
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
    val zoomScope = rememberCoroutineScope()
    var zoomJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(model, attempt) { onDispose { zoomJob?.cancel() } }
    val fit = if (imageSize.width > 0 && imageSize.height > 0) min(viewport.width.toFloat() / imageSize.width, viewport.height.toFloat() / imageSize.height) else 1f
    val maxScale = if (fit > 0) max(8f, 2f / fit).coerceAtMost(64f) else 8f
    fun transform(zoom: Float, pan: Offset = Offset.Zero, focus: Offset = Offset.Zero) {
        val next = (scale * zoom).coerceIn(1f, maxScale)
        val moved = (offset - focus) * (next / scale) + focus + pan
        val x = max(0f, (imageSize.width * fit * next - viewport.width) / 2)
        val y = max(0f, (imageSize.height * fit * next - viewport.height) / 2)
        offset = Offset(moved.x.coerceIn(-x, x), moved.y.coerceIn(-y, y)); scale = next
        onZoom(scale > 1.01f)
    }
    fun animateZoom(target: Float, focus: Offset = Offset.Zero) {
        zoomJob?.cancel()
        zoomJob = zoomScope.launch {
            animate(scale, target.coerceIn(1f, maxScale), animationSpec = tween(180)) { value, _ ->
                transform(value / scale, focus = focus)
            }
        }
    }
    Box(
        modifier = modifier.clipToBounds().onSizeChanged { viewport = it }
            .pointerInput(model, attempt) {
                detectTapGestures(onDoubleTap = { point ->
                    if (!loading && !failed) animateZoom(if (scale > 1.01f) 1f else 2.5f,
                        focus = point - Offset(viewport.width / 2f, viewport.height / 2f))
                })
            }
            .pointerInput(model, attempt, viewport, imageSize) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    zoomJob?.cancel()
                    var edgeDrag = 0f
                    var totalPan = Offset.Zero
                    var totalZoom = 1f
                    var transforming = false
                    var multiple = false
                    do {
                        val event = awaitPointerEvent()
                        multiple = multiple || event.changes.count { it.pressed } > 1
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        totalPan += pan; totalZoom *= zoom
                        val centroid = event.calculateCentroid(useCurrent = false)
                        if (!transforming) transforming = (scale > 1.01f || multiple) &&
                            (totalPan.getDistance() > viewConfiguration.touchSlop || abs(1 - totalZoom) * viewport.width > viewConfiguration.touchSlop)
                        if (event.changes.any { it.pressed } && transforming && !loading && !failed && event.changes.none { it.isConsumed }) {
                            val previousX = offset.x
                            transform(zoom, pan, centroid - Offset(viewport.width / 2f, viewport.height / 2f))
                            if (!multiple && abs(pan.x) > abs(pan.y)) {
                                val overflow = pan.x - (offset.x - previousX)
                                edgeDrag = if (overflow * edgeDrag < 0) overflow else edgeDrag + overflow
                            } else edgeDrag = 0f
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (!multiple && abs(edgeDrag) > max(viewConfiguration.touchSlop * 4, viewport.width * 0.15f))
                        onEdgeSwipe(if (edgeDrag < 0) 1 else -1)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 缩略图底图降级（当原图正在加载或失败时垫底展示）
        if (thumbnailModel != null && (loading || failed)) {
            AsyncImage(
                model = thumbnailModel,
                imageLoader = loader,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
            )
        }
        key(model, attempt) {
            AsyncImage(
                model = fullSizeRequest,
                imageLoader = loader,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                    .semantics {
                        stateDescription = if (failed) "加载失败" else if (loading) "加载中" else "已加载，缩放 ${(scale * 100).toInt()}%"
                        customActions = listOf(
                            CustomAccessibilityAction("放大图片") { animateZoom(scale * 2); true },
                            CustomAccessibilityAction("恢复图片大小") { animateZoom(1f); true }
                        )
                    },
                onLoading = { loading = true; failed = false },
                onSuccess = {
                    loading = false; failed = false
                    imageSize = IntSize(it.result.drawable.intrinsicWidth.coerceAtLeast(1), it.result.drawable.intrinsicHeight.coerceAtLeast(1))
                },
                onError = { loading = false; failed = true }
            )
        }
        if (loading && thumbnailModel == null) CircularProgressIndicator(Modifier.size(28.dp), color = AccentBlue, strokeWidth = 2.dp)
        if (failed && thumbnailModel == null) IconButton(onClick = { zoomJob?.cancel(); transform(1f / scale); attempt++ }) {
            Icon(Icons.Default.Refresh, contentDescription = "重试加载图片", tint = AccentBlue)
        }
    }
}
