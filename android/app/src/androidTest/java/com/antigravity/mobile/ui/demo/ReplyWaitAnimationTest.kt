package com.antigravity.mobile.ui.demo

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.ui.chat.ChatViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReplyWaitAnimationTest {
    private val scale = object : MotionDurationScale {
        var value by mutableFloatStateOf(1f)
        override val scaleFactor get() = value
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = scale)
    private val user = GatewayMessageItem(id = "u", text = "请检查这个问题")
    private val answer = GatewayMessageItem(id = "a", type = "agent", text = "**第一段正文**")
    private var messages by mutableStateOf(listOf(user))
    private var running by mutableStateOf(true)
    private var stopping by mutableStateOf(false)
    private var disconnected by mutableStateOf(false)
    private var approval by mutableStateOf(false)

    private fun mount() {
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.setContent {
            ConversationContent(vm, messages, null, running, false, false, false, {}, Modifier.fillMaxSize(),
                waitingForConnection = disconnected, isStopping = stopping, waitingForInteraction = approval)
        }
        compose.waitUntil(5_000) {
            if (!compose.mainClock.autoAdvance) compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("正在回复…").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun spinner() = compose.onNodeWithTag("reply-wait-ribbon", useUnmergedTree = true)
    private fun pixels(image: ImageBitmap) = IntArray(image.width * image.height).also { image.readPixels(it) }
    private fun frame() = pixels(spinner().captureToImage())

    @Test fun waitingMovesButFirstBodyNeverWaitsForTheFade() {
        // The test policy cancels infinite animations started while autoAdvance is enabled.
        compose.mainClock.autoAdvance = false
        try {
            mount()
            compose.mainClock.advanceTimeBy(1_000)
            val label = compose.onNodeWithText("正在回复…", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("reply-wait-motion", useUnmergedTree = true).assertExists()
            val list = compose.onNode(hasScrollToIndexAction())
            val first = pixels(list.captureToImage())
            val differences = (1..8).map {
                compose.mainClock.advanceTimeBy(160)
                val next = pixels(list.captureToImage())
                first.indices.count { first[it] != next[it] }
            }
            android.util.Log.i("ReplyWaitVerification", "Waiting list changed pixels=$differences")
            assertTrue("等待图标应随时间变化: $differences", differences.any { it > 20 })
            assertEquals(label, compose.onNodeWithText("正在回复…", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
            compose.runOnUiThread { messages = listOf(user, answer) }
            // Background parsing may take wall time, but the fade's clock stays at its first frame.
            compose.mainClock.advanceTimeByFrame()
            compose.waitUntil(5_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithText("第一段正文").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("第一段正文").assertIsDisplayed()
            spinner().assertExists()
            assertEquals(label.left, compose.onNodeWithText("正在回复…", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left, 1f)
            compose.mainClock.advanceTimeBy(320)
            spinner().assertDoesNotExist()
            compose.runOnUiThread { messages = listOf(user, answer.copy(text = "**后续正文**")) }
            compose.mainClock.autoAdvance = true
            compose.waitUntil(5_000) { compose.onAllNodesWithText("后续正文").fetchSemanticsNodes().isNotEmpty() }
            spinner().assertDoesNotExist()
            compose.runOnIdle { running = false }
            compose.onNodeWithText("正在回复…").assertDoesNotExist()
            compose.onNodeWithText("后续正文").assertIsDisplayed()
            compose.onNodeWithContentDescription("回到最新消息").assertDoesNotExist()
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun newTurnWaitsAndConfirmationHistoryAndCorrectionsDoNotReplayTheHandoff() {
        messages = listOf(answer)
        mount()
        spinner().assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread { messages = listOf(user, answer) }
            repeat(10) {
                compose.mainClock.advanceTimeByFrame()
                spinner().assertDoesNotExist()
            }
        } finally { compose.mainClock.autoAdvance = true }
        val next = user.copy(id = "local:next", text = "第二个问题")
        compose.runOnIdle { messages += next }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("第二个问题").fetchSemanticsNodes().isNotEmpty() }
        spinner().assertIsDisplayed()
        compose.runOnIdle { messages = listOf(user, answer, next.copy(id = "confirmed"),
            answer.copy(id = "thought", type = "thought")) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("执行过程 · 进行中").fetchSemanticsNodes().isNotEmpty() }
        spinner().assertIsDisplayed()
        val second = answer.copy(id = "second", text = "**第二轮正文**")
        compose.runOnIdle { messages += second }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("第二轮正文").fetchSemanticsNodes().isNotEmpty() }
        spinner().assertDoesNotExist()
        compose.runOnIdle { messages = listOf(user.copy(id = "older", text = "旧历史")) + messages.dropLast(1) + second.copy(text = "") }
        compose.waitForIdle()
        spinner().assertDoesNotExist()
        compose.runOnIdle { disconnected = true }
        compose.onNodeWithText("正在回复…").assertDoesNotExist()
        compose.runOnIdle { disconnected = false }
        compose.onNodeWithText("正在回复…").assertIsDisplayed()
        spinner().assertDoesNotExist()
    }

    @Test fun approvalDisconnectionStopAndFastCompletionLeaveNoWaitingLoop() {
        mount()
        compose.runOnIdle { approval = true }
        compose.onNodeWithText("正在回复…").assertDoesNotExist()
        spinner().assertDoesNotExist()
        compose.runOnIdle { stopping = true }
        compose.onNodeWithText("正在停止…").assertIsDisplayed()
        spinner().assertDoesNotExist()
        compose.runOnIdle { stopping = false }
        compose.onNodeWithText("正在停止…").assertDoesNotExist()
        compose.runOnIdle { approval = false }
        spinner().assertIsDisplayed()
        compose.runOnIdle { stopping = true }
        compose.onNodeWithText("正在停止…").assertIsDisplayed()
        spinner().assertDoesNotExist()
        compose.runOnIdle { stopping = false } // A failed stop returns to the actual running phase.
        spinner().assertIsDisplayed()
        compose.runOnIdle { disconnected = true }
        spinner().assertDoesNotExist()
        compose.runOnIdle { disconnected = false }
        spinner().assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread { running = false }
            compose.mainClock.advanceTimeBy(32)
            compose.runOnUiThread { running = true; messages += user.copy(id = "next") }
            compose.mainClock.advanceTimeBy(200)
        } finally { compose.mainClock.autoAdvance = true }
        spinner().assertIsDisplayed()
        compose.runOnIdle { messages += answer; running = false }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("第一段正文").fetchSemanticsNodes().isNotEmpty() }
        spinner().assertDoesNotExist()
        compose.onNodeWithText("正在回复…").assertDoesNotExist()
    }

    @Test fun disablingMotionMidWaitLeavesAStaticIndicatorAndImmediateBody() {
        mount()
        compose.runOnIdle { scale.value = 0f }
        compose.mainClock.autoAdvance = false
        try {
            compose.mainClock.advanceTimeBy(200)
            val still = frame()
            compose.mainClock.advanceTimeBy(320)
            assertArrayEquals(still, frame())
        } finally { compose.mainClock.autoAdvance = true }
        compose.runOnIdle { messages += answer }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("第一段正文").fetchSemanticsNodes().isNotEmpty() }
        spinner().assertDoesNotExist()
        compose.onNodeWithText("第一段正文").assertIsDisplayed()
    }

    @Test fun backgroundAndInvisibleIndicatorsAreStaticAndResumeOnlyWhileWaiting() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        var visible by mutableStateOf(true)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ReplyProgress(waiting = true, stopping = false, animate = visible)
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            for (background in listOf(true, false)) {
                compose.runOnUiThread {
                    owner.registry.currentState = if (background) Lifecycle.State.STARTED else Lifecycle.State.RESUMED
                    visible = background
                }
                compose.mainClock.advanceTimeBy(200)
                val still = frame()
                compose.mainClock.advanceTimeBy(320)
                assertArrayEquals(still, frame())
            }
            compose.runOnUiThread { visible = true }
            compose.mainClock.advanceTimeBy(200)
            val resumed = frame()
            compose.mainClock.advanceTimeBy(160)
            assertFalse(resumed.contentEquals(frame()))
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun finishingOffScreenKeepsTheHistoryAnchorAndNextTurnCanWait() {
        messages = (0 until 30).flatMap { index -> listOf(user.copy(id = "u$index", text = "历史问题 $index"),
            answer.copy(id = "a$index", text = "历史回复 $index")) } + user
        mount()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToIndex(0)
        compose.waitForIdle()
        compose.onNodeWithTag("reply-wait-motion", useUnmergedTree = true).assertDoesNotExist()
        val anchor = compose.onNodeWithText("历史问题 0").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { running = false }
        assertEquals(anchor, compose.onNodeWithText("历史问题 0").fetchSemanticsNode().boundsInRoot)
        list.performScrollToIndex(60)
        compose.onNodeWithText("正在回复…").assertDoesNotExist()
        compose.runOnIdle { running = true; messages += user.copy(id = "local:next", text = "新一轮") }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("新一轮").fetchSemanticsNodes().isNotEmpty() }
        spinner().assertIsDisplayed()
        compose.onNodeWithTag("reply-wait-motion", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun statusFitsNarrowScreensAndLargeTextWithoutMovingDuringHandoff() {
        var width by mutableStateOf(320)
        var fontScale by mutableFloatStateOf(1f)
        var waiting by mutableStateOf(true)
        val density = compose.activity.resources.displayMetrics.density
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                Box(Modifier.fillMaxSize()) {
                    ReplyProgress(waiting, false, false, Modifier.width(width.dp))
                }
            }
        }
        for (dp in listOf(320, 360, 412)) for (font in listOf(1f, 1.5f)) {
            compose.runOnIdle { width = dp; fontScale = font; waiting = true }
            val label = compose.onNodeWithText("正在回复…", useUnmergedTree = true).assertIsDisplayed()
            val bounds = label.fetchSemanticsNode().boundsInRoot
            val circle = spinner().fetchSemanticsNode().boundsInRoot
            assertTrue(circle.right <= bounds.left && bounds.right <= dp * density)
            compose.runOnIdle { waiting = false }
            assertEquals(bounds, label.fetchSemanticsNode().boundsInRoot)
            spinner().assertDoesNotExist()
        }
    }
}
