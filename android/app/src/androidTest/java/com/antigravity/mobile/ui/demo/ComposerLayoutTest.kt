package com.antigravity.mobile.ui.demo

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
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

    @Test fun composerMovesOneSendButtonContinuouslyAndCanReverseMidTransition() {
        var clearFocus: () -> Unit = {}
        compose.setContent {
            val focus = LocalFocusManager.current
            clearFocus = { focus.clearFocus() }
            Box(Modifier.fillMaxSize()) {
                Composer(modifier = Modifier.width(360.dp).testTag("composer"), activeConversation = true,
                    draft = "", attachments = emptyList(), onDraftChange = {}, onRemoveImage = {},
                    onSend = {}, isSending = false, onUnsupported = {}, modelBadge = "Gemini 3.8 Flash 高",
                    workModeLabel = "直接")
            }
        }
        val resting = compose.onNodeWithContentDescription("发送消息").fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance = false
        try {
            compose.onNode(hasSetTextAction()).performClick().assertIsFocused()
            compose.mainClock.advanceTimeByFrame()
            val positions = mutableListOf<Float>()
            repeat(6) {
                compose.mainClock.advanceTimeBy(16)
                compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
                compose.onAllNodesWithContentDescription("发送消息").assertCountEquals(1)
                val bounds = compose.onNodeWithContentDescription("发送消息").fetchSemanticsNode().boundsInRoot
                assertTrue("Send button must remain square: $bounds", kotlin.math.abs(bounds.width - bounds.height) < 2)
                positions += bounds.top
            }
            assertTrue("Opening must have intermediate layout frames: $positions", positions.distinct().size > 2)
            val partial = positions.last()
            compose.runOnUiThread { clearFocus() }
            compose.mainClock.advanceTimeByFrame()
            val reversed = compose.onNodeWithContentDescription("发送消息").fetchSemanticsNode().boundsInRoot.top
            assertTrue("Reversal must continue at the current position", kotlin.math.abs(reversed - partial) < 20)
            compose.mainClock.advanceTimeBy(400)
            val returned = compose.onNodeWithContentDescription("发送消息").fetchSemanticsNode().boundsInRoot.top
            assertTrue("Closing must return to its resting position", kotlin.math.abs(returned - resting) < 2)
        } finally { compose.mainClock.autoAdvance = true }
    }

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

    @Test fun queueActionStaysSquareWhileTheStopSlotClosesAndReverses() {
        val draft = mutableStateOf("排队消息")
        val density = compose.activity.resources.displayMetrics.density
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Composer(modifier = Modifier.width(320.dp), activeConversation = true,
                    draft = draft.value, attachments = emptyList(), onDraftChange = { draft.value = it },
                    onRemoveImage = {}, onSend = {}, isSending = false, onUnsupported = {}, isRunning = true,
                    modelBadge = "Gemini 3.8 Flash 高", workModeLabel = "直接")
            }
        }
        compose.onNodeWithContentDescription("排队发送消息").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread { draft.value = "" }
            repeat(14) {
                compose.mainClock.advanceTimeByFrame()
                val bounds = compose.onNodeWithContentDescription("停止生成").fetchSemanticsNode().boundsInRoot
                assertTrue("Closing must keep one full square action: $bounds",
                    kotlin.math.abs(bounds.width - bounds.height) < 2 && bounds.width >= 48 * density - 1)
                compose.onAllNodesWithContentDescription("停止生成").assertCountEquals(1)
                if (it == 2) compose.runOnUiThread { draft.value = "重新排队" }
                if (it == 6) compose.runOnUiThread { draft.value = "" }
                if (it in 3..5) {
                    val send = compose.onNodeWithContentDescription("排队发送消息").fetchSemanticsNode().boundsInRoot
                    assertTrue("Reversal must retain a square send button: $send", kotlin.math.abs(send.width - send.height) < 2)
                }
            }
        } finally { compose.mainClock.autoAdvance = true }
    }
}
