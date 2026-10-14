package com.antigravity.mobile.ui.demo

import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.ui.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Collections

class StreamingRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun longRepliesKeepUpdatingAndFinishWithoutMovingTheTail() {
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        val messages = mutableStateOf(emptyList<GatewayMessageItem>())
        val running = mutableStateOf(true)
        val frames = Collections.synchronizedList(mutableListOf<Long>())
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION)) }
        compose.runOnUiThread { compose.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper())) }
        compose.setContent { ConversationContent(vm, messages.value, if (running.value) "live" else null,
            running.value, false, false, false, {}, Modifier.fillMaxSize()) }
        val block = "## 长文标题\n\n" + "已经输出的内容持续排版并保持可读。".repeat(12) +
            "\n\n```kotlin\nval answer = 42\n```\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\n\$\$\nx^2 + y^2\n\$\$\n\n```mermaid\ngraph TD\nA-->B\n```\n\n"
        try {
            for (size in listOf(2_000, 10_000, 50_000)) {
                val source = block.repeat((size + block.length - 1) / block.length)
                val beforePss = Debug.getPss()
                val parseMs = (1..6).map { iteration ->
                    runBlocking(Dispatchers.Default) {
                        val started = System.nanoTime()
                        val items = buildConversationRenderItems(listOf(GatewayMessageItem(id = "live", type = "agent",
                            text = source + "结尾 $iteration")), "live", isRunning = true)
                        assertTrue(items.first().streaming)
                        (System.nanoTime() - started) / 1_000_000.0
                    }
                }
                val updateMs = mutableListOf<Long>()
                compose.runOnIdle { running.value = true; frames.clear() }
                repeat(5) { index ->
                    val tail = "末尾 $size/$index"
                    val started = SystemClock.elapsedRealtime()
                    compose.runOnUiThread { messages.value = listOf(GatewayMessageItem(id = "live", type = "agent", text = source + tail)) }
                    compose.waitUntil(2_000) { compose.onAllNodesWithText(tail).fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText(tail).assertIsDisplayed()
                    updateMs += SystemClock.elapsedRealtime() - started
                    Thread.sleep((250L - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(0))
                }
                compose.runOnIdle { running.value = false }
                compose.waitForIdle()
                compose.onNodeWithText("末尾 $size/4").assertIsDisplayed()
                compose.onNodeWithContentDescription("回到最新消息").assertDoesNotExist()
                val sortedFrames = synchronized(frames) { frames.sorted() }
                Log.i("StreamingVerification", "chars=${source.length} parseMs=$parseMs UI-updateMs=$updateMs " +
                    "pssDeltaKiB=${Debug.getPss() - beforePss} frames=${sortedFrames.size} " +
                    "frameP95Ms=${sortedFrames.getOrNull(((sortedFrames.size - 1) * .95).toInt())?.div(1_000_000.0)}")
            }
        } finally { compose.runOnUiThread { compose.activity.window.removeOnFrameMetricsAvailableListener(listener) } }
    }

    @Test fun openDiagramFenceShowsCodeUntilItCloses() {
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        val text = mutableStateOf("```mermaid\ngraph TD\nA-->B")
        compose.setContent { ConversationContent(vm, listOf(GatewayMessageItem(id = "live", type = "agent", text = text.value)),
            "live", true, false, false, false, {}, Modifier.fillMaxSize()) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("graph TD\nA-->B").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Mermaid 图表").assertDoesNotExist()
        compose.runOnIdle { text.value += "\n```" }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Mermaid 图表").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Mermaid 图表").assertIsDisplayed()
        compose.onNodeWithText("正在回复…").assertIsDisplayed()
    }
}
