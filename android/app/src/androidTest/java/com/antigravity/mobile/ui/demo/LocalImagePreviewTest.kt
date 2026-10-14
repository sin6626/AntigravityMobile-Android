package com.antigravity.mobile.ui.demo

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.data.model.MessageAttachment
import com.antigravity.mobile.ui.chat.ChatViewModel
import com.antigravity.mobile.ui.chat.OutgoingMessage
import com.antigravity.mobile.ui.chat.retainLocalImagePreviews
import java.io.ByteArrayOutputStream
import java.util.UUID
import org.junit.Rule
import org.junit.Test

class LocalImagePreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun pendingImagesRenderAndOpenBeforeAnyServerResponse() {
        val namespace = "images_${UUID.randomUUID()}_"
        val context = object : ContextWrapper(compose.activity.applicationContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(namespace + name, mode)
        }
        val app = object : Application() { fun attach(context: Context) { attachBaseContext(context) } }
            .apply { attach(context) }
        val vm = ChatViewModel(app)
        val store = ViewModelStore().apply { put("local-image-test", vm) }
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val local = GatewayMessageItem(id = "local:images", text = "这是什么", imageDataList = List(4) { bytes },
            attachments = List(4) { MessageAttachment("image/png", "multigravity-private-$it") })
        val message = mutableStateOf(local)
        try {
            compose.setContent {
                Box(Modifier.fillMaxSize()) {
                    ConversationContent(vm, listOf(message.value), null, false, false, false, false, {}, allowRevert = false)
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("聊天图片，点击放大").fetchSemanticsNodes().size == 4 }
            compose.onAllNodesWithContentDescription("聊天图片，点击放大").assertCountEquals(4)
            compose.onAllNodesWithContentDescription("聊天图片，点击放大")[0].performClick()
            compose.onNodeWithContentDescription("聊天图片 1/4").assertIsDisplayed()
            compose.runOnIdle {
                val remote = local.copy(id = "server", imageDataList = emptyList(),
                    media = listOf(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)))
                message.value = retainLocalImagePreviews(listOf(remote), emptyList(),
                    listOf(OutgoingMessage("test", local, emptySet(), hasAttachments = true)), "test").single()
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("聊天图片，点击放大").fetchSemanticsNodes().size == 4 }
            compose.onAllNodesWithText("multigravity-private-", substring = true).assertCountEquals(0)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("聊天图片，点击放大")[3].captureToImage().toPixelMap()
                    .let { pixels -> pixels[pixels.width / 2, pixels.height / 2].blue > .9f &&
                        pixels[pixels.width / 2, pixels.height / 2].red < .2f }
            }
        } finally { compose.runOnIdle { store.clear() } }
    }
}
