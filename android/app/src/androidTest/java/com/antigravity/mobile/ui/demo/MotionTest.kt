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
