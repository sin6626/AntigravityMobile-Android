package com.antigravity.mobile.ui.demo

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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
        compose.onNodeWithContentDescription("复制代码").performClick()
        val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        compose.runOnIdle { assertEquals(code, clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
        compose.onNodeWithContentDescription("复制整条回复").performClick()
        compose.runOnIdle { assertEquals(source, clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
        val reply = compose.onNodeWithContentDescription("复制整条回复").fetchSemanticsNode().boundsInRoot
        val paragraph = compose.onNodeWithText("After").fetchSemanticsNode().boundsInRoot
        assertTrue("Reply copy should be at the right edge", reply.left > paragraph.right)
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
