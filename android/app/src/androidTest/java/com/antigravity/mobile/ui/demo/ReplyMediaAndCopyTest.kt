package com.antigravity.mobile.ui.demo

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.ViewModelProvider
import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.data.service.ApiClient
import com.antigravity.mobile.data.service.PreferencesManager
import com.antigravity.mobile.ui.chat.ChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReplyMediaAndCopyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
            MessageImages(GatewayMessageItem(id = "broken-image", type = "agent",
                imageUrls = listOf("http://127.0.0.1:1/missing.png")), vm)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("图片加载失败").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("聊天图片，点击放大").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("图片加载失败，点击关闭").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("图片加载失败，点击关闭").performClick()
    }
}
