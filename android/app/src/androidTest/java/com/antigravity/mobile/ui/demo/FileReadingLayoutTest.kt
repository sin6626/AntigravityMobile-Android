package com.antigravity.mobile.ui.demo

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.antigravity.mobile.data.model.FileContentResponse
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FileReadingLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun wrapToggleReflowsCodeAndTextWithoutChangingCopySource() {
        val wrap = mutableStateOf(true)
        val name = mutableStateOf("Example.kt")
        val code = "import com.antigravity.mobile.ui.demo.ThisIsAVeryLongUnbrokenClassNameForReadingOnANarrowPhone"
        compose.setContent {
            Column(Modifier.width(320.dp).fillMaxHeight().background(Color.White).padding(16.dp)) {
                FileWrapToggle(wrap.value) { wrap.value = it }
                FileTextPreview(FileContentResponse(filename = name.value,
                    content = if (name.value.endsWith(".md")) "```kotlin\n$code\n```" else code),
                    Modifier.weight(1f).fillMaxWidth(), wrapLines = wrap.value)
            }
        }
        fun lineCount(): Int {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(code).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().lineCount
        }
        for (filename in listOf("Example.kt", "Example.txt", "Example.md")) {
            compose.runOnIdle { name.value = filename; wrap.value = true }
            compose.waitUntil(5_000) { compose.onAllNodesWithText(code).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("自动换行").assertIsOn().assertHeightIsAtLeast(48.dp)
            assertTrue("$filename must wrap", lineCount() > 1)
            saveScreenshot("file-wrap-${filename.substringAfterLast('.')}.png")
            compose.onNodeWithContentDescription("自动换行").performClick()
            compose.onNodeWithContentDescription("自动换行").assertIsOff()
            assertEquals(1, lineCount())
            if (filename.endsWith(".kt")) {
                compose.onNodeWithContentDescription("复制代码").performClick()
                val clipboard = compose.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                compose.runOnIdle { assertEquals(code, clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
            }
        }
    }

    @Test fun longFailureKeepsRetryVisibleAtLargeFontAndShortHeight() {
        var retries = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Box(Modifier.width(300.dp).height(280.dp).background(Color.White)) {
                    FilePreviewFailure((1..50).joinToString("\n") { "连接超时：无法读取电脑上的文件，请检查连接。$it" }) { retries++ }
                }
            }
        }
        val retry = compose.onNodeWithContentDescription("重试读取文件")
        retry.assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        val detail = compose.onNodeWithContentDescription("文件加载错误详情").fetchSemanticsNode().boundsInRoot
        assertTrue(detail.bottom < retry.fetchSemanticsNode().boundsInRoot.top)
        saveScreenshot("file-long-error-large-font.png")
    }

    @Test fun changesBarFitsLargeCountsAndFontWithAccessibleReview() {
        var reviewed = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Box(Modifier.width(300.dp).background(Color.White).padding(12.dp)) {
                    ConversationChangesBar(ConversationDiffSummary(123, 12345, 67890, 2)) { reviewed = true }
                }
            }
        }
        compose.onNodeWithText("+12345", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("−67890", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("审阅当前会话变更：123 个文件，新增 12345 行，删除 67890 行，2 个文件暂不可用")
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertTrue(reviewed) }
        saveScreenshot("changes-bar-large-font.png")
    }

    @Test fun changeCountsShareTheSameTextBaseline() {
        compose.setContent {
            Box(Modifier.width(360.dp).background(Color.White).padding(12.dp)) {
                ConversationChangesBar(ConversationDiffSummary(4, 286, 70, 0)) {}
            }
        }
        val baselines = listOf("4 个文件", "+286", "−70").map { text ->
            val node = compose.onNodeWithText(text, useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            node.fetchSemanticsNode().boundsInRoot.top + layouts.single().firstBaseline
        }
        assertTrue("统计文字必须共享基线：$baselines", baselines.max() - baselines.min() < 1f)
        saveScreenshot("changes-baseline.png")
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
