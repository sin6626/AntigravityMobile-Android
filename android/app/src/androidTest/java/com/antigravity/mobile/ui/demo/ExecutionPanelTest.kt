package com.antigravity.mobile.ui.demo

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.ui.chat.ChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExecutionPanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun headerStaysStillDuringEveryAnimationFrameAndLiveAppend() {
        val user = GatewayMessageItem(id = "user", text = "question")
        val thought = GatewayMessageItem(id = "thought", type = "thought",
            text = (1..80).joinToString("\n\n") { "Thought paragraph $it" })
        val answer = GatewayMessageItem(id = "answer", type = "agent",
            text = (1..40).joinToString("\n\n") { "Answer paragraph $it" })
        val messages = mutableStateOf(listOf(user, thought, answer))
        val running = mutableStateOf(false)
        val olderUser = GatewayMessageItem(id = "older-user", text = "Earlier question")
        val olderAnswer = GatewayMessageItem(id = "older-answer", type = "agent", text = "Earlier answer")
        val viewModel = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.setContent {
            ConversationContent(viewModel, messages.value, null, running.value, false,
                true, false, { messages.value = listOf(olderUser, olderAnswer) + messages.value }, Modifier.fillMaxSize())
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.waitForIdle()
        val header = compose.onNodeWithContentDescription("执行过程")
        val top = header.fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance = false
        fun checkFrames() {
            repeat(18) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                assertEquals("Header moved during an animation frame", top,
                    header.fetchSemanticsNode().boundsInRoot.top, 1f)
            }
        }
        header.performClick()
        checkFrames()
        compose.onNodeWithText("加载更早消息").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Earlier answer").fetchSemanticsNodes().isNotEmpty()
        }
        checkFrames()
        compose.runOnIdle {
            running.value = true
            messages.value = listOf(olderUser, olderAnswer, user, thought,
                GatewayMessageItem(id = "tool", type = "tools", toolCount = 1, text = "Read file"),
                answer.copy(text = answer.text + "\n\nAnswer updated while the process is expanded"))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("执行过程").fetchSemanticsNodes().isNotEmpty() }
        checkFrames()
        header.performClick()
        checkFrames()
        header.performClick()
        repeat(4) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        header.performClick()
        checkFrames()
    }
}
