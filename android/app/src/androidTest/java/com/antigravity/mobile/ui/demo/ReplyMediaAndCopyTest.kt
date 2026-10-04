package com.antigravity.mobile.ui.demo

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.data.model.FileContentResponse
import com.antigravity.mobile.data.service.ApiClient
import com.antigravity.mobile.data.service.PreferencesManager
import com.antigravity.mobile.ui.chat.ChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ReplyMediaAndCopyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun actualFileLinksShowMarkdownAndLoadImageBytes() {
        // Private real file paths are supplied at run time, never committed as fixtures.
        val args = InstrumentationRegistry.getArguments()
        val document = args.getString("documentUri")
        val image = args.getString("imageUri")
        assumeTrue(document != null && image != null)
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.setContent {
            ConversationContent(vm, listOf(GatewayMessageItem(id = "file-links", type = "agent",
                text = "[Markdown]($document)\n\n[Image]($image)")), null, false, false, false, false, {}, Modifier.fillMaxSize())
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Markdown").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Markdown").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("1. 项目简介").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("复制文件原文").assertExists()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("Image").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("文件图片预览").fetchSemanticsNodes().any {
                it.config.getOrElse(SemanticsProperties.StateDescription) { "" } == "已加载"
            }
        }
        compose.onNodeWithText("关闭").performClick()
    }

    @Test fun markdownFileUsesNativeBlocksAndHighlightedCodeRemainsCopyable() {
        val code = "// 中文 🙂\nval message = \"hello\"\n"
        compose.setContent {
            FileTextPreview(FileContentResponse(filename = "Agent.md", content = "# File heading\n\n**Strong** paragraph\n\n```kotlin\n${code}```\n\n| Short | Header |\n| --- | --- |\n| Longer first cell | Value |"), Modifier.fillMaxSize())
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("File heading").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Strong paragraph").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(code.trimEnd()).fetchSemanticsNodes().any { node ->
                node.config[SemanticsProperties.Text].any { it.spanStyles.any { span -> span.item.color == Color(0xFF8250A6) } }
            }
        }
        val codeNode = compose.onNodeWithText(code.trimEnd())
        codeNode.performTouchInput { longClick(center) }
        compose.onNodeWithContentDescription("复制代码").performClick()
        val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        compose.runOnIdle { assertEquals(code, clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        assertEquals(compose.onNodeWithText("Short").fetchSemanticsNode().boundsInRoot.left,
            compose.onNodeWithText("Longer first cell").fetchSemanticsNode().boundsInRoot.left, 0.5f)
        assertEquals(compose.onNodeWithText("Header").fetchSemanticsNode().boundsInRoot.left,
            compose.onNodeWithText("Value").fetchSemanticsNode().boundsInRoot.left, 0.5f)
    }

    @Test fun fileUriReachesGatewayWithoutLosingItsScheme() {
        val api = ApiClient(compose.activity, PreferencesManager(compose.activity))
        for (uri in listOf("file:///h:/Project/screenshots/image.png", "file:///tmp/image.png")) {
            val resolved = api.resolveMediaURL(uri)
            assertEquals(uri, if (resolved == uri) resolved else Uri.parse(resolved).getQueryParameter("uri"))
        }
        assertEquals("https://example.com/image.png", api.resolveMediaURL("https://example.com/image.png"))
    }

    @Test fun codeCopyPreservesWhitespaceAndReplyCopyIsOnTheRight() {
        val code = "fun example() {\n    println(\"  keep spaces  \")\n}\n"
        val source = "Before\n\n```kotlin\n${code}```\n\nAfter"
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.setContent {
            ConversationContent(vm, listOf(GatewayMessageItem(id = "copy-answer", type = "agent", text = source)),
                null, false, false, false, false, {}, Modifier.fillMaxSize())
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("复制整条回复").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("复制代码").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("复制代码").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已复制"))
        val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        compose.runOnIdle { assertEquals(code, clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithContentDescription("复制代码").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "未复制"))
        compose.onNodeWithContentDescription("复制整条回复").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("复制整条回复").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已复制"))
        compose.runOnIdle { assertEquals(source, clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
        val reply = compose.onNodeWithContentDescription("复制整条回复").fetchSemanticsNode().boundsInRoot
        val paragraph = compose.onNodeWithText("After").fetchSemanticsNode().boundsInRoot
        assertTrue("Reply copy should be at the right edge", reply.left > paragraph.right)
    }

    @Test fun inlineCodeKeepsItsBackgroundButSelectionPaintsOverIt() {
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.setContent {
            CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(Color.Blue, Color(0xFF4488FF))) {
                ConversationContent(vm, listOf(GatewayMessageItem(id = "select-answer", type = "agent",
                    text = "before `SELECTABLE_CODE` after")), null, false, false, false, false, {}, Modifier.fillMaxSize())
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("before SELECTABLE_CODE after").fetchSemanticsNodes().isNotEmpty() }
        val text = compose.onNodeWithText("before SELECTABLE_CODE after")
        fun countColor(color: Color): Int {
            val pixels = text.captureToImage().toPixelMap()
            return (0 until pixels.width).sumOf { x -> (0 until pixels.height).count { y -> pixels[x, y] == color } }
        }
        assertTrue("Inline code lost its normal background", countColor(Color(0xFFF2F2F2)) > 20)
        text.performTouchInput { longClick(center) }
        assertTrue("Code background covered the selection", countColor(Color(0xFF4488FF)) > 20)
    }

    @Test fun failedImageShowsAnExplanationInsteadOfBlankSpace() {
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.setContent {
            Box(Modifier.padding(48.dp)) {
            MessageImages(GatewayMessageItem(id = "broken-image", type = "agent",
                imageUrls = listOf("http://127.0.0.1:1/missing.png")), vm)
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("图片加载失败").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("聊天图片，点击放大").performTouchInput { click(center) }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("重试加载图片").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("关闭").assertDoesNotExist()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("重试加载图片").fetchSemanticsNodes().isEmpty() }
    }
}
