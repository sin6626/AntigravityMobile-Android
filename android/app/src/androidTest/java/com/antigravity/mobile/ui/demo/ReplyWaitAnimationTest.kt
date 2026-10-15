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
    private fun labelFrame(): IntArray {
        val image = compose.onNodeWithText("正在回复…", useUnmergedTree = true).captureToImage()
        // Exclude the inline dots so their motion cannot make a static label pass.
        val width = (image.width * .7f).toInt()
        return IntArray(width * image.height).also { image.readPixels(it, width = width, stride = width) }
    }
    private fun dotsFrame() = pixels(compose.onNodeWithTag("reply-progress-dots", useUnmergedTree = true).captureToImage())

    @Test fun textShimmersDuringWaitingAndOutputButStopsWhenStopping() {
        compose.mainClock.autoAdvance = false
        try {
            mount()
            val label = compose.onNodeWithText("正在回复…", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            for (beforeBody in listOf(true, false)) {
                if (!beforeBody) compose.runOnUiThread { messages += answer }
                compose.mainClock.advanceTimeBy(320)
                val first = labelFrame()
                val firstDots = dotsFrame()
                val firstRibbon = frame()
                var dotsMove = false
                var ribbonMoves = false
                val differences = (1..8).map {
                    compose.mainClock.advanceTimeBy(160)
                    dotsMove = dotsMove || !firstDots.contentEquals(dotsFrame())
                    ribbonMoves = ribbonMoves || !firstRibbon.contentEquals(frame())
                    val next = labelFrame()
                    first.indices.count { first[it] != next[it] }
                }
                assertTrue("文字自身必须有流光，等待=$beforeBody: $differences", differences.any { it > 20 })
                assertTrue("三个点和光带在正文输出时也必须运动", dotsMove && ribbonMoves)
                assertEquals(label.left, compose.onNodeWithText("正在回复…", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left)
            }
            spinner().assertIsDisplayed()
            compose.runOnUiThread { stopping = true }
            compose.mainClock.advanceTimeBy(320)
            val stopped = compose.onNodeWithText("正在停止…", useUnmergedTree = true)
            val first = pixels(stopped.captureToImage())
            val stoppedRibbon = frame()
            compose.mainClock.advanceTimeBy(640)
            assertArrayEquals(first, pixels(stopped.captureToImage()))
            assertArrayEquals(stoppedRibbon, frame())
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun firstBodyAndLaterChunksKeepTheWholeIndicatorUntilCompletion() {
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
            // The first body is displayed independently of the indicator's cycle.
            compose.mainClock.advanceTimeByFrame()
            compose.waitUntil(5_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithText("第一段正文").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("第一段正文").assertIsDisplayed()
            spinner().assertExists()
            assertEquals(label.left, compose.onNodeWithText("正在回复…", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left, 1f)
            compose.mainClock.advanceTimeBy(320)
            spinner().assertIsDisplayed()
            compose.runOnUiThread { messages = listOf(user, answer.copy(text = "**后续正文**")) }
            compose.mainClock.autoAdvance = true
            compose.waitUntil(5_000) { compose.onAllNodesWithText("后续正文").fetchSemanticsNodes().isNotEmpty() }
            spinner().assertIsDisplayed()
            compose.runOnIdle { running = false }
            compose.onNodeWithText("正在回复…").assertDoesNotExist()
            compose.onNodeWithText("后续正文").assertIsDisplayed()
            compose.onNodeWithContentDescription("回到最新消息").assertDoesNotExist()
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun historyConfirmationAndBodyCorrectionsKeepTheActiveIndicator() {
        messages = listOf(answer)
        mount()
        spinner().assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread { messages = listOf(user, answer) }
            repeat(10) {
                compose.mainClock.advanceTimeByFrame()
                spinner().assertIsDisplayed()
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
        spinner().assertIsDisplayed()
        compose.runOnIdle { messages = listOf(user.copy(id = "older", text = "旧历史")) + messages.dropLast(1) + second.copy(text = "") }
        compose.waitForIdle()
        spinner().assertIsDisplayed()
        compose.runOnIdle { disconnected = true }
        compose.onNodeWithText("正在回复…").assertDoesNotExist()
        compose.runOnIdle { disconnected = false }
        compose.onNodeWithText("正在回复…").assertIsDisplayed()
        spinner().assertIsDisplayed()
    }

    @Test fun approvalDisconnectionStopAndFastCompletionLeaveNoWaitingLoop() {
        mount()
        compose.runOnIdle { approval = true }
        compose.onNodeWithText("正在回复…").assertDoesNotExist()
        spinner().assertDoesNotExist()
        compose.runOnIdle { stopping = true }
        compose.onNodeWithText("正在停止…").assertIsDisplayed()
        spinner().assertIsDisplayed()
        compose.onNodeWithTag("reply-wait-motion", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { stopping = false }
        compose.onNodeWithText("正在停止…").assertDoesNotExist()
        compose.runOnIdle { approval = false }
        spinner().assertIsDisplayed()
        compose.runOnIdle { stopping = true }
        compose.onNodeWithText("正在停止…").assertIsDisplayed()
        spinner().assertIsDisplayed()
        compose.onNodeWithTag("reply-wait-motion", useUnmergedTree = true).assertDoesNotExist()
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
            val stillText = labelFrame()
            val stillDots = dotsFrame()
            compose.mainClock.advanceTimeBy(320)
            assertArrayEquals(still, frame())
            assertArrayEquals(stillText, labelFrame())
            assertArrayEquals(stillDots, dotsFrame())
        } finally { compose.mainClock.autoAdvance = true }
        compose.runOnIdle { messages += answer }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("第一段正文").fetchSemanticsNodes().isNotEmpty() }
        spinner().assertIsDisplayed()
        compose.onNodeWithText("第一段正文").assertIsDisplayed()
    }

    @Test fun backgroundAndInvisibleIndicatorsFreezeAndResumeWithoutReset() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        var visible by mutableStateOf(true)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ReplyProgress(stopping = false, animate = visible)
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
                val stillText = labelFrame()
                val stillDots = dotsFrame()
                compose.mainClock.advanceTimeBy(320)
                assertArrayEquals(still, frame())
                assertArrayEquals(stillText, labelFrame())
                assertArrayEquals(stillDots, dotsFrame())
            }
            val initial = frame()
            compose.runOnUiThread { visible = true }
            compose.mainClock.advanceTimeBy(200)
            val resumed = frame()
            compose.mainClock.advanceTimeBy(800)
            assertFalse(resumed.contentEquals(frame()))
            val beforePause = frame()
            compose.runOnUiThread { visible = false }
            // The pause is applied on the next composition frame, which may advance the clock once.
            compose.mainClock.advanceTimeByFrame()
            val paused = frame()
            assertTrue("暂停应保留当前位置，而不是回到初始帧",
                paused.indices.count { paused[it] != beforePause[it] } <
                    paused.indices.count { paused[it] != initial[it] })
            compose.mainClock.advanceTimeBy(320)
            assertArrayEquals(paused, frame())
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

    @Test fun statusAndDotsFitNarrowScreensAndLargeTextWithoutShiftingOnStop() {
        var width by mutableStateOf(320)
        var fontScale by mutableFloatStateOf(1f)
        val density = compose.activity.resources.displayMetrics.density
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                Box(Modifier.fillMaxSize()) {
                    ReplyProgress(stopping, false, Modifier.width(width.dp))
                }
            }
        }
        for (dp in listOf(320, 360, 412)) for (font in listOf(1f, 1.5f)) {
            compose.runOnIdle { width = dp; fontScale = font; stopping = false }
            val label = compose.onNodeWithText("正在回复…", useUnmergedTree = true).assertIsDisplayed()
            val bounds = label.fetchSemanticsNode().boundsInRoot
            val circle = spinner().fetchSemanticsNode().boundsInRoot
            assertTrue(circle.right <= bounds.left && bounds.right <= dp * density)
            val dots = compose.onNodeWithTag("reply-progress-dots", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(dots.right <= bounds.right && dots.bottom <= bounds.bottom)
            compose.runOnIdle { stopping = true }
            assertEquals(bounds, compose.onNodeWithText("正在停止…", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
            spinner().assertIsDisplayed()
        }
    }
}
