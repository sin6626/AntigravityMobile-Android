package com.antigravity.mobile.ui.demo

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.ui.chat.ChatViewModel

@Composable
internal fun MessageImages(message: GatewayMessageItem, viewModel: ChatViewModel) {
    val count = maxOf(message.media?.size ?: 0, message.imageUrls?.size ?: 0)
    if (count == 0) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        repeat(count) { index ->
            val media = message.media?.getOrNull(index)
            val rawUrl = message.imageUrls?.getOrNull(index)
            val thumbnail = remember(media) {
                try {
                    media?.substringAfter("base64,")?.let { Base64.decode(it, Base64.DEFAULT) }
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            val fullRequest = remember(rawUrl) { rawUrl?.let(viewModel::mediaImageRequest) }
            var useFull by remember(thumbnail, fullRequest) { mutableStateOf(thumbnail == null) }
            var expanded by remember { mutableStateOf(false) }
            val preview = if (useFull) fullRequest ?: thumbnail else thumbnail
            if (preview != null) {
                var loading by remember(preview) { mutableStateOf(true) }
                var failed by remember(preview) { mutableStateOf(false) }
                Box(Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3F3F3)),
                    contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = preview,
                        imageLoader = viewModel.mediaImageLoader,
                        contentDescription = "聊天图片，点击放大",
                        contentScale = ContentScale.Crop,
                        onLoading = { loading = true; failed = false },
                        onSuccess = { loading = false; failed = false },
                        onError = {
                            loading = false
                            failed = true
                            if (!useFull && fullRequest != null) useFull = true
                        },
                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp))
                            .quietClickable(RoundedCornerShape(10.dp)) { expanded = true },
                    )
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    if (failed) Text("图片加载失败", color = SecondaryInk, fontSize = 12.sp)
                }
                if (expanded) {
                    var fullFailed by remember(fullRequest, thumbnail) { mutableStateOf(false) }
                    Dialog(onDismissRequest = { expanded = false }) {
                        if (fullFailed) {
                            Text("图片加载失败，点击关闭", color = Ink,
                                modifier = Modifier.background(Color.White, RoundedCornerShape(10.dp))
                                    .quietClickable { expanded = false }.padding(20.dp))
                        } else {
                            AsyncImage(
                                model = fullRequest ?: thumbnail,
                                imageLoader = viewModel.mediaImageLoader,
                                contentDescription = "聊天图片，点击关闭",
                                contentScale = ContentScale.Fit,
                                onError = { fullFailed = true },
                                modifier = Modifier.fillMaxWidth().heightIn(max = 650.dp)
                                    .quietClickable { expanded = false },
                            )
                        }
                    }
                }
            }
        }
    }
}
