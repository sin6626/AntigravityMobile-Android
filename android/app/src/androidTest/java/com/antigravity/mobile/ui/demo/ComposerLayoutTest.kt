package com.antigravity.mobile.ui.demo

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antigravity.mobile.ui.chat.PendingImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComposerLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun fullToolbarKeepsFixedActionsAtNarrowWidthsAndLargeFonts() {
        val width = mutableStateOf(360)
        val fontScale = mutableStateOf(1f)
        val running = mutableStateOf(false)
        val density = compose.activity.resources.displayMetrics.density
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale.value)) {
                Box(Modifier.fillMaxSize()) {
                    Composer(modifier = Modifier.width(width.value.dp), activeConversation = true,
                        draft = "待发送", attachments = listOf(PendingImage(Uri.parse("test://image"), byteArrayOf(), "image/png")),
                        onDraftChange = {}, onRemoveImage = {}, onSend = {}, isSending = false, onUnsupported = {},
                        modelBadge = "Gemini 3.8 Flash 高", workModeLabel = "直接", isRunning = running.value)
                }
            }
        }
        for (queued in listOf(false, true)) for (scale in listOf(1f, 1.15f, 1.3f, 1.5f)) {
            for (dp in listOf(320, 360, 384, 412)) {
                compose.runOnIdle { width.value = dp; fontScale.value = scale; running.value = queued }
                val labels = listOf("添加附件与操作", "语音输入", if (queued) "排队发送消息" else "发送消息") +
                    if (queued) listOf("停止生成") else emptyList()
                labels.forEach { label ->
                    val node = compose.onNodeWithContentDescription(label).assertIsDisplayed()
                    val bounds = node.fetchSemanticsNode().boundsInRoot
                    assertTrue("$label width=$dp font=$scale queued=$queued $bounds",
                        bounds.width >= 48 * density - 1 && bounds.height >= 48 * density - 1 &&
                            kotlin.math.abs(bounds.width - bounds.height) < 2)
                }
            }
        }
    }
}
