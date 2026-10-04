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
