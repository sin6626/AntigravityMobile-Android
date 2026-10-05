package com.antigravity.mobile.ui.demo

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.ui.chat.ChatViewModel
import com.antigravity.mobile.ui.chat.PendingImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = object : androidx.compose.ui.MotionDurationScale {
        override val scaleFactor get() = if (InstrumentationRegistry.getArguments().getString("motionDisabled") == "true") 0f else 1f
    })
    private val baseline get() = InstrumentationRegistry.getArguments().getString("motionBaseline") == "true"

    @Test fun grayHighlightNeverDarkensDuringPressOrRelease() {
        val highlighted = mutableStateOf(false)
        compose.setContent {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White)) {
                Box(Modifier.fillMaxWidth().quietClickable(pressedColor = androidx.compose.ui.graphics.Color(0xFFF1F1F1),
                    highlighted = highlighted.value, onClick = {})) { androidx.compose.material3.Text("灰底过渡") }
            }
        }
        fun gray() = compose.onNodeWithText("灰底过渡").captureToImage().toPixelMap().let { it[it.width - 4, it.height / 2].red }
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread { highlighted.value = true }
            compose.mainClock.advanceTimeByFrame()
            repeat(7) {
                compose.mainClock.advanceTimeBy(16)
                assertTrue("Gray entry must stay between white and #F1F1F1, was ${gray()}", gray() >= 240f / 255f)
            }
            compose.runOnUiThread { highlighted.value = false }
            compose.mainClock.advanceTimeByFrame()
            repeat(7) {
                compose.mainClock.advanceTimeBy(16)
                assertTrue("Gray exit must stay between #F1F1F1 and white, was ${gray()}", gray() >= 240f / 255f)
            }
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun composerShadowStaysAttachedDuringWidthMorphing() {
        val active = mutableStateOf(false)
        var bounds = androidx.compose.ui.geometry.Rect.Zero
        val density = compose.activity.resources.displayMetrics.density
        compose.setContent {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White),
                contentAlignment = Alignment.Center) {
                Composer(Modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
                    active.value, "", emptyList(), {}, {}, {}, {}, false, {})
            }
        }
        fun shadowDarkness(): Int {
            compose.waitForIdle()
            val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            val darkness = (1..5).maxOf { gap ->
                255 - android.graphics.Color.red(bitmap.getPixel(bounds.center.x.toInt(), (bounds.bottom + gap * density).toInt()))
            }
            bitmap.recycle()
            return darkness
        }
        assertTrue("Resting composer must keep its shadow", shadowDarkness() > 4)
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread { active.value = true }
            compose.mainClock.advanceTimeBy(64)
            Thread.sleep(200)
            assertTrue("Native shadow must follow the changing width without disappearing", shadowDarkness() > 4)
            screenshot("composer-shadow-width-morphing.png")
            compose.runOnUiThread { active.value = false }
            compose.mainClock.advanceTimeBy(64)
            Thread.sleep(200)
            assertTrue("Reverse morph must retain the shadow", shadowDarkness() > 4)
            compose.mainClock.advanceTimeBy(1_000)
            Thread.sleep(200)
            assertTrue("Restored composer must keep its shadow", shadowDarkness() > 4)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun enteredContentRemainsVisibleWhenDrawerDelaysAnotherEntrance() {
        val ready = mutableStateOf(false)
        val visit = mutableStateOf("A")
        compose.setContent {
            Box(Modifier.fillMaxSize().then(contentEntrance(visit.value, ready = ready.value))) {
                androidx.compose.material3.Text("正文保留")
            }
        }
        compose.onNodeWithText("正文保留").assertDoesNotExist()
        compose.runOnIdle { ready.value = true }
        compose.onNodeWithText("正文保留").assertIsDisplayed()
        compose.runOnIdle { ready.value = false }
        compose.onNodeWithText("正文保留").assertIsDisplayed()
        compose.runOnIdle { visit.value = "B" }
        compose.onNodeWithText("正文保留").assertDoesNotExist()
        compose.runOnIdle { ready.value = true }
        compose.onNodeWithText("正文保留").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread { visit.value = "C" }
            compose.mainClock.advanceTimeBy(64)
            compose.runOnUiThread { ready.value = false }
            compose.mainClock.advanceTimeBy(200)
            compose.onNodeWithText("正文保留").assertIsDisplayed()
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun composerKeepsOneFieldAndGrowsToFourLines() {
        val draft = mutableStateOf("第一行")
        val images = mutableStateOf((1..4).map { PendingImage(Uri.parse("test://$it"), byteArrayOf(), "image/png") })
        compose.setContent { Composer(activeConversation = true, draft = draft.value, attachments = images.value,
            onDraftChange = { draft.value = it }, onAddImage = {},
            onRemoveImage = { uri -> images.value = images.value.filterNot { it.uri == uri } },
            onSend = {}, isSending = false, onUnsupported = {}) }
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        val oneLine = field.fetchSemanticsNode().boundsInRoot.height
        field.performTextReplacement("第一行\n第二行\n第三行\n第四行")
        compose.waitForIdle()
        val fourLines = field.fetchSemanticsNode().boundsInRoot.height
        android.util.Log.i("MOTION_CHECK", "field heights: $oneLine -> $fourLines")
        screenshot(if (baseline) "round3-baseline-composer.png" else "round3-composer.png")
        if (!baseline) assertTrue("Four lines must have room to display", fourLines > oneLine * 2.5f)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        val oldThirdX = compose.onAllNodesWithContentDescription("待发送图片").fetchSemanticsNodes()[2].boundsInRoot.left
        compose.onAllNodesWithContentDescription("移除待发送图片")[1].performClick()
        compose.onAllNodesWithContentDescription("待发送图片").assertCountEquals(3)
        val newThirdX = compose.onAllNodesWithContentDescription("待发送图片").fetchSemanticsNodes()[1].boundsInRoot.left
        assertTrue(newThirdX < oldThirdX)
        repeat(3) { compose.onAllNodesWithContentDescription("移除待发送图片")[0].performClick() }
        field.assertIsFocused().assertTextEquals("第一行\n第二行\n第三行\n第四行")
        compose.onAllNodesWithContentDescription("添加图片").assertCountEquals(1)
        compose.activity.runOnUiThread { compose.activity.window.decorView.clearFocus() }
    }

    @Test fun menuShadowFollowsOpeningClosingAndReversal() {
        val open = mutableStateOf(false)
        compose.setContent {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.fillMaxWidth(.85f), contentAlignment = Alignment.TopEnd) {
                    RoundIconButton("more_vert", "打开测试菜单", { open.value = true })
                    ConversationActionsMenu(open.value, { open.value = false }, "阴影验收", false, false, true, {}, {}, {}, {})
                }
            }
        }
        val density = compose.activity.resources.displayMetrics.density
        var menuRow: androidx.compose.ui.semantics.SemanticsNode? = null
        fun shadow(): Int {
            compose.waitForIdle()
            Thread.sleep(150) // Present this held transition frame in the native window.
            val node = if (open.value) compose.onNodeWithText("删除会话").fetchSemanticsNode().also { menuRow = it }
                else checkNotNull(menuRow)
            val row = node.boundsInWindow.translate(node.positionOnScreen - node.positionInWindow)
            val scale = row.width / (220f * density)
            val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            val result = (1..8).maxOf { gap -> 255 - android.graphics.Color.red(bitmap.getPixel(
                row.center.x.toInt(), (row.bottom + (6 * scale + gap) * density).toInt())) }
            bitmap.recycle()
            return result
        }
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread { open.value = true }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(80)
            val early = shadow()
            screenshot("menu-shadow-opening-early.png")
            compose.mainClock.advanceTimeBy(48)
            val later = shadow()
            screenshot("menu-shadow-opening-later.png")
            compose.mainClock.advanceTimeBy(96)
            val resting = shadow()
            screenshot("menu-shadow-resting.png")
            assertTrue("Shadow must already be visible during entry: $early / $later / $resting", early > 0)
            assertTrue("Shadow must grow during entry: $early / $later / $resting", later > early && resting >= later)
            compose.runOnUiThread { open.value = false }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(32)
            val closing = shadow()
            screenshot("menu-shadow-closing.png")
            assertTrue("Shadow must fade before dismissal: $closing / $resting", closing in 1 until resting)
            compose.runOnUiThread { open.value = true }
            compose.mainClock.advanceTimeByFrame()
            val reversing = shadow()
            screenshot("menu-shadow-reversing.png")
            assertTrue("Reversal must keep a partial shadow: $reversing / $resting", reversing in 1 until resting)
            compose.mainClock.advanceTimeBy(240)
            assertTrue(kotlin.math.abs(shadow() - resting) <= 2)
            compose.runOnUiThread { open.value = false }
            compose.mainClock.advanceTimeBy(240)
            compose.onNodeWithText("删除会话").assertDoesNotExist()
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun menuExitStopsActionsAndCanReverseBeforeFinishing() {
        val open = mutableStateOf(false)
        var pins = 0
        compose.setContent { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            RoundIconButton("more_vert", "打开测试菜单", { open.value = true })
            ConversationActionsMenu(open.value, { open.value = false }, "动效测试对话", false, false, true,
                {}, { pins++; open.value = false }, {}, {})
        } }
        compose.runOnIdle { open.value = true }
        compose.onNodeWithText("置顶").assertExists()
        screenshot(if (baseline) "round3-baseline-menu.png" else "round3-menu.png")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("置顶").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        if (!baseline) compose.onNodeWithText("置顶").assertDoesNotExist()
        assertEquals(1, pins)
        compose.runOnUiThread { open.value = true }
        compose.mainClock.advanceTimeBy(220)
        compose.onNodeWithText("置顶").assertIsEnabled()
        compose.runOnUiThread { open.value = false }
        compose.mainClock.advanceTimeBy(220)
        compose.onNodeWithText("置顶").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test fun streamFormattingKeepsLatestVisibleAndDoesNotPullHistory() {
        val viewModel = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        val source = "## 标题\n\n正文 **加粗** 与链接。\n\n```kotlin\nval answer = 42\n```\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\n末尾锚点"
        val old = (1..30).flatMap { listOf(GatewayMessageItem(id = "u$it", text = "问题 $it"),
            GatewayMessageItem(id = "a$it", type = "agent", text = "历史回复 $it\n\n更多历史 $it")) }
        val messages = mutableStateOf(old + GatewayMessageItem(id = "live", type = "agent", text = source))
        val running = mutableStateOf(true)
        compose.setContent { ConversationContent(viewModel, messages.value, if (running.value) "live" else null,
            running.value, false, false, false, {}, Modifier.fillMaxSize()) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("末尾锚点", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { running.value = false }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("末尾锚点").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithText("末尾锚点").assertIsDisplayed()
        compose.onNodeWithContentDescription("回到最新消息").assertDoesNotExist()
        screenshot("round3-stream-finished.png")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        val historyY = compose.onNodeWithText("历史回复 1").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle {
            running.value = true
            messages.value = old + GatewayMessageItem(id = "live", type = "agent", text = source + "\n\n追加正文")
        }
        compose.waitForIdle()
        compose.runOnIdle { running.value = false }
        compose.waitForIdle()
        assertEquals(historyY, compose.onNodeWithText("历史回复 1").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithContentDescription("回到最新消息").assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = java.io.File(compose.activity.getExternalFilesDir(null), name)
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
