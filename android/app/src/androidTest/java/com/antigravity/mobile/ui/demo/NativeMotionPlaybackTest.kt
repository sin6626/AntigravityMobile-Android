package com.antigravity.mobile.ui.demo

import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.antigravity.mobile.ui.chat.PendingImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import androidx.compose.ui.unit.dp

/** Optional recording fixture: uses the Android frame clock, not Compose's virtual test clock. */
class NativeMotionPlaybackTest {
    @Test fun replyIndicatorUsesPlatformFramesThroughoutStreaming() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val user = com.antigravity.mobile.data.model.GatewayMessageItem(id = "native-user", text = "帮我打磨一下回复动画")
        val messages = mutableStateOf(listOf(user))
        val running = mutableStateOf(true)
        fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
            if (node == null || !node.isVisibleToUser) return null
            if (node.text?.contains(text) == true) return node
            for (i in 0 until node.childCount) find(node.getChild(i), text)?.let { return it }
            return null
        }
        fun awaitText(text: String): AccessibilityNodeInfo {
            repeat(50) {
                find(automation.rootInActiveWindow, text)?.let { return it }
                Thread.sleep(100)
            }
            error("未显示：$text")
        }
        fun statusBounds(): android.graphics.Rect {
            val bounds = android.graphics.Rect()
            awaitText("正在回复").getBoundsInScreen(bounds)
            // Accessibility omits the ribbon; include its 32 dp slot and 10 dp gap.
            bounds.left = (bounds.left - 42 * instrumentation.targetContext.resources.displayMetrics.density).toInt().coerceAtLeast(0)
            return bounds
        }
        fun capture(bounds: android.graphics.Rect, name: String? = null): IntArray {
            val bitmap = checkNotNull(automation.takeScreenshot())
            try {
                if (name != null) java.io.File(instrumentation.targetContext.getExternalFilesDir(null), name)
                    .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                return IntArray(bounds.width() * bounds.height()).also {
                    bitmap.getPixels(it, 0, bounds.width(), bounds.left, bounds.top, bounds.width(), bounds.height())
                }
            } finally { bitmap.recycle() }
        }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val frames = java.util.Collections.synchronizedList(mutableListOf<Long>())
            val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                frames.add(metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION))
            }
            scenario.onActivity { activity ->
                activity.window.addOnFrameMetricsAvailableListener(listener, android.os.Handler(android.os.Looper.getMainLooper()))
                val vm = androidx.lifecycle.ViewModelProvider(activity)[com.antigravity.mobile.ui.chat.ChatViewModel::class.java]
                activity.setContent {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        ConversationContent(vm, messages.value, null, running.value, false, false, false, {}, Modifier.fillMaxSize())
                    }
                }
            }
            var bounds = statusBounds()
            Thread.sleep(300)
            frames.clear()
            Thread.sleep(6000)
            val sampled = synchronized(frames) { frames.sorted() }
            android.util.Log.i("ReplyWaitVerification", "Platform ribbon frames=${sampled.size} " +
                "p95Ms=${sampled.getOrNull(((sampled.size - 1) * .95).toInt())?.div(1_000_000.0)} " +
                "over32ms=${sampled.count { it > 32_000_000 }}")
            val first = capture(bounds, "reply-wait-native-0.png")
            val differences = (1..5).map { index ->
                Thread.sleep(160)
                val next = capture(bounds, if (index == 5) "reply-wait-native-1.png" else null)
                first.indices.count { first[it] != next[it] }
            }
            assertTrue("实际时钟下等待行应变化：$differences", differences.any { it > 20 })
            android.util.Log.i("ReplyWaitVerification", "Platform waiting row changed pixels=$differences")
            scenario.onActivity { messages.value += com.antigravity.mobile.data.model.GatewayMessageItem(
                id = "native-reply", type = "agent", text = "**正文即时显示**") }
            awaitText("正文即时显示")
            Thread.sleep(400)
            bounds = statusBounds()
            val settled = capture(bounds, "reply-wait-native-body.png")
            val ribbonBounds = android.graphics.Rect(bounds).apply {
                right = left + (32 * instrumentation.targetContext.resources.displayMetrics.density).toInt()
            }
            val ribbon = capture(ribbonBounds)
            assertTrue("正文输出时左侧光带必须保留", ribbon.any {
                android.graphics.Color.blue(it) > 180 && android.graphics.Color.red(it) < 80
            })
            val bodyDifferences = (1..5).map {
                Thread.sleep(160)
                val next = capture(bounds)
                settled.indices.count { settled[it] != next[it] }
            }
            assertTrue("生成正文期间文字应继续流光：$bodyDifferences", bodyDifferences.any { it > 20 })
            assertTrue("正文输出时光带必须继续运动", !ribbon.contentEquals(capture(ribbonBounds)))
            for (chunk in listOf("左侧光带持续流动。", "文字高光与三个点共享节奏。", "全部输出完成后，整组柔和退场。")) {
                scenario.onActivity { messages.value = messages.value.dropLast(1) +
                    messages.value.last().copy(text = messages.value.last().text + "\n\n" + chunk) }
                awaitText(chunk)
                Thread.sleep(900)
            }
            scenario.onActivity { running.value = false }
            val removed = (1..20).any {
                Thread.sleep(100)
                find(automation.rootInActiveWindow, "正在回复") == null
            }
            capture(bounds, "reply-wait-native-finished.png")
            assertTrue("回复结束后等待状态应移除", removed)
            assertNotNull(find(automation.rootInActiveWindow, "正文即时显示"))
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
        }
    }

    @Test fun recordComposerAndMenuAtPlatformSpeed() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("motionPlayback") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val draft = mutableStateOf("")
        val images = mutableStateOf(emptyList<PendingImage>())
        var pins = 0
        fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
            if (node == null || !node.isVisibleToUser) return null
            if (predicate(node)) return node
            for (i in 0 until node.childCount) find(node.getChild(i), predicate)?.let { return it }
            return null
        }
        fun lookup(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
            repeat(20) {
                find(automation.rootInActiveWindow, predicate)?.let { return it }
                Thread.sleep(100)
            }
            return null
        }
        fun tap(node: AccessibilityNodeInfo) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val downTime = android.os.SystemClock.uptimeMillis()
            for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                val event = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), action,
                    bounds.exactCenterX(), bounds.exactCenterY(), 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                assertTrue(automation.injectInputEvent(event, true))
                event.recycle()
            }
        }
        fun click(description: String) {
            val node = lookup { it.contentDescription?.toString() == description }
            assertNotNull(description, node)
            tap(node!!)
        }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                activity.setContent {
                    BackHandler { }
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        Box(Modifier.statusBarsPadding()) {
                            ConversationTopBar(false, {}, {}, {}, {}, title = "动效测试对话", onPin = { pins++ })
                        }
                        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()) {
                            Composer(activeConversation = true, draft = draft.value, attachments = images.value,
                                onDraftChange = { draft.value = it }, onAddImage = {},
                                onRemoveImage = { uri -> images.value = images.value.filterNot { it.uri == uri } },
                                onSend = {}, isSending = false, onUnsupported = {})
                            Spacer(Modifier.height(23.dp))
                        }
                    }
                }
            }
            Thread.sleep(700)
            val field = lookup { it.isEditable }
            assertNotNull("Input field", field)
            tap(field!!)
            Thread.sleep(500)
            scenario.onActivity { draft.value = "第一行" }
            Thread.sleep(500)
            scenario.onActivity { draft.value = "第一行\n第二行\n第三行\n第四行" }
            Thread.sleep(500)
            scenario.onActivity { images.value = (1..4).map { PendingImage(Uri.parse("test://$it"), byteArrayOf(), "image/png") } }
            Thread.sleep(500)
            scenario.onActivity { images.value = images.value.filterIndexed { index, _ -> index != 1 } }
            Thread.sleep(500)
            scenario.onActivity { images.value = emptyList() }
            Thread.sleep(500)
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            Thread.sleep(500)
            click("更多选项")
            Thread.sleep(500)
            val pin = lookup { it.text?.toString()?.contains("置顶") == true }
            assertNotNull("Pin action", pin)
            tap(pin!!)
            Thread.sleep(300)
            assertEquals(1, pins)
            click("更多选项")
            Thread.sleep(500)
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            Thread.sleep(300)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
                "dumpsys gfxinfo com.antigravity.mobile framestats")).use { input ->
                java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "round3-native-framestats.txt")
                    .writeText(input.bufferedReader().readText())
            }
        }
    }
}
