package com.antigravity.mobile.ui.demo

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.data.service.PreferencesManager
import com.antigravity.mobile.data.service.ApiClient
import com.antigravity.mobile.data.model.ProjectItem
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import com.antigravity.mobile.ui.chat.ChatViewModel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class ChatUxTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun realGatewayRenamesOnlyADisposableConversation() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("uxRealGateway") == "true")
        val application = compose.activity.application
        val prefs = PreferencesManager(application)
        assertTrue("Device must already be paired", prefs.isPaired())
        val api = ApiClient(application, prefs)
        runBlocking {
            // An empty prompt creates a test session without sending a message or starting an Agent task.
            val id = api.createCascade("", "", "gemini-3.8-flash-high", ProjectItem.PURE_CHAT.id).getOrThrow()
            try {
                val title = "UX 验收 ${UUID.randomUUID().toString().take(8)}"
                api.renameConversation(id, title).getOrThrow()
                assertEquals(title, api.fetchConversations().getOrThrow().first { it.id == id }.title)
            } finally {
                api.deleteConversation(id).getOrThrow()
            }
        }
    }

    @Test fun moreMenuAndRunningActionHaveDistinctOperations() {
        var renamed = false
        var deleted = false
        val running = mutableStateOf(true)
        val stopping = mutableStateOf(false)
        var stops = 0
        var sends = 0
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                ConversationTopBar(false, {}, {}, { renamed = true }, { deleted = true })
                Composer(activeConversation = true, draft = "next message", attachments = emptyList(),
                    onDraftChange = {}, onAddImage = {}, onRemoveImage = {}, onSend = { sends++ },
                    isSending = false, onUnsupported = {}, isRunning = running.value,
                    isStopping = stopping.value, onStop = { stops++; stopping.value = true })
            }
        }
        compose.onNodeWithContentDescription("更多选项").performClick()
        compose.onNodeWithText("重命名").performClick()
        assertTrue(renamed)
        assertFalse(deleted)
        compose.onNodeWithContentDescription("更多选项").performClick()
        compose.onNodeWithText("删除会话").performClick()
        assertTrue(deleted)
        compose.onNodeWithContentDescription("停止生成").performClick()
        compose.onNodeWithContentDescription("停止生成").assertIsNotEnabled()
        assertEquals(1, stops)
        assertEquals(0, sends)
        compose.runOnIdle { running.value = false }
        compose.onNodeWithContentDescription("发送消息").assertIsNotEnabled()
        compose.runOnIdle { stopping.value = false }
        compose.onNodeWithContentDescription("发送消息").performClick()
        assertEquals(1, sends)
    }

    @Test fun readingHistoryDoesNotFollowUpdatesUntilJumpToLatest() {
        val viewModel = ChatViewModel(compose.activity.application)
        val store = ViewModelStore().apply { put("test", viewModel) }
        try {
            val messages = mutableStateOf((1..50).flatMap {
                listOf(GatewayMessageItem(id = "user-$it", text = "Question $it"),
                    GatewayMessageItem(id = "answer-$it", type = "agent", text = "Answer $it\n\nMore text $it"))
            })
            compose.setContent { ConversationContent(viewModel, messages.value, null, false,
                false, false, false, {}, Modifier.fillMaxSize()) }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Answer 50").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            compose.onNodeWithContentDescription("回到最新消息").assertExists()
            compose.runOnIdle { messages.value += listOf(GatewayMessageItem(id = "user-51", text = "Question 51"),
                GatewayMessageItem(id = "answer-51", type = "agent", text = "Newest reply")) }
            compose.waitForIdle()
            compose.onNodeWithText("Answer 1").assertIsDisplayed()
            compose.onNodeWithText("Newest reply").assertDoesNotExist()
            compose.onNodeWithContentDescription("回到最新消息").performClick()
            compose.onNodeWithText("Newest reply").assertIsDisplayed()
            compose.onNodeWithContentDescription("回到最新消息").assertDoesNotExist()
        } finally { compose.runOnIdle { store.clear() } }
    }

    @Test fun draftsPersistAndLateRenameStopDeleteCannotChangeAnotherConversation() {
        // A unique preferences namespace keeps these checks away from real pairing and drafts.
        val namespace = "ux_${UUID.randomUUID()}_"
        val context = object : ContextWrapper(compose.activity.applicationContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(namespace + name, mode)
            override fun getCacheDir(): File = File(super.getCacheDir(), namespace).apply { mkdirs() }
        }
        val application = object : Application() { fun attach(context: Context) { attachBaseContext(context) } }.apply { attach(context) }
        FakeGateway().use { gateway ->
            val prefs = PreferencesManager(application)
            prefs.gatewayBaseUrl = gateway.url
            prefs.deviceToken = "test-token"
            var vm = ChatViewModel(application)
            val store = ViewModelStore().apply { put("test", vm) }
            fun onMain(action: () -> Unit) = compose.runOnIdle(action)
            fun open(id: String) {
                onMain { vm.openConversation(id) }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingMessages }
            }
            try {
                open("A")
                onMain { vm.setDraft("A draft") }
                open("B")
                assertEquals("", vm.state.value.draft)
                onMain { vm.setDraft("B draft") }
                open("A")
                assertEquals("A draft", vm.state.value.draft)
                onMain { store.clear(); vm = ChatViewModel(application); store.put("test", vm) }
                open("B")
                assertEquals("B draft", vm.state.value.draft)
                open("A")

                for (operation in listOf("rename", "stop", "delete")) {
                    val listsBefore = gateway.requests.count { it.first.contains("GetAllCascadeTrajectories") }
                    val requested = CountDownLatch(1)
                    val release = CountDownLatch(1)
                    val replied = CountDownLatch(1)
                    gateway.block = { path ->
                        if (path.contains(when (operation) {
                            "rename" -> "UpdateConversationAnnotations"
                            "stop" -> "CancelCascadeInvocation"
                            else -> "DeleteCascadeTrajectory"
                        })) { requested.countDown(); release.await(10, TimeUnit.SECONDS); replied.countDown() }
                    }
                    open("A")
                    onMain { when (operation) {
                        "rename" -> vm.renameSelectedConversation("Renamed A") {}
                        "stop" -> vm.stopGeneration()
                        else -> vm.deleteSelectedConversation()
                    } }
                    assertTrue("$operation request missing", requested.await(10, TimeUnit.SECONDS))
                    open("B")
                    release.countDown()
                    assertTrue(replied.await(10, TimeUnit.SECONDS))
                    // A follow-up list request proves the successful mutation callback has run.
                    if (operation != "stop") compose.waitUntil(10_000) {
                        gateway.requests.count { it.first.contains("GetAllCascadeTrajectories") } > listsBefore &&
                            !vm.state.value.isLoadingConversations
                    }
                    compose.waitForIdle()
                    assertEquals("B", vm.state.value.selectedConversationId)
                    assertEquals("B draft", vm.state.value.draft)
                    assertFalse(vm.state.value.isStopping)
                    assertFalse(vm.state.value.isRenaming)
                    assertFalse(vm.state.value.isDeleting)
                }
                val rename = gateway.requests.first { it.first.contains("UpdateConversationAnnotations") }.second
                val payload = JSONObject(rename)
                assertEquals("A", payload.getJSONArray("cascadeIds").getString(0))
                assertTrue(payload.getBoolean("mergeAnnotations"))
                assertEquals("Renamed A", payload.getJSONObject("annotations").getString("title"))

                gateway.block = {}
                open("B")
                gateway.failurePath = "CancelCascadeInvocation"
                val stopsBefore = gateway.requests.count { it.first.contains("CancelCascadeInvocation") }
                onMain { vm.stopGeneration(); vm.stopGeneration() }
                compose.waitUntil(10_000) { !vm.state.value.isStopping }
                assertTrue(vm.state.value.isRunning)
                assertTrue(vm.state.value.error.orEmpty().contains("500"))
                assertEquals(stopsBefore + 1, gateway.requests.count { it.first.contains("CancelCascadeInvocation") })
                gateway.failurePath = null
                onMain { vm.stopGeneration() }
                compose.waitUntil(10_000) { !vm.state.value.isStopping && !vm.state.value.isRunning }
                assertEquals("B draft", vm.state.value.draft)
                gateway.failurePath = "UpdateConversationAnnotations"
                var renamed = false
                onMain { vm.renameSelectedConversation("Failed title") { renamed = true } }
                compose.waitUntil(10_000) { !vm.state.value.isRenaming }
                assertFalse(renamed)
                assertTrue(vm.state.value.error.orEmpty().contains("500"))

                open("A")
                val sendRequested = CountDownLatch(1)
                val releaseSend = CountDownLatch(1)
                gateway.failurePath = "SendUserCascadeMessage"
                gateway.block = { path -> if (path.contains("SendUserCascadeMessage")) {
                    sendRequested.countDown(); releaseSend.await(10, TimeUnit.SECONDS)
                } }
                onMain { vm.setDraft("failed A"); vm.send(); vm.setDraft("new A") }
                assertTrue(sendRequested.await(10, TimeUnit.SECONDS))
                open("B")
                releaseSend.countDown()
                compose.waitUntil(10_000) { !vm.state.value.isSending }
                assertEquals("B draft", vm.state.value.draft)
                open("A")
                assertEquals("failed A\n\nnew A", vm.state.value.draft)

                val createRequested = CountDownLatch(1)
                val releaseCreate = CountDownLatch(1)
                gateway.failurePath = "/gateway/cascade/new"
                gateway.block = { path -> if (path.contains("/gateway/cascade/new")) {
                    createRequested.countDown(); releaseCreate.await(10, TimeUnit.SECONDS)
                } }
                onMain { vm.newConversation(); vm.setDraft("failed new"); vm.send(); vm.setDraft("more local") }
                assertTrue(createRequested.await(10, TimeUnit.SECONDS))
                onMain { vm.newConversation(); vm.setDraft("new home") }
                releaseCreate.countDown()
                compose.waitUntil(10_000) { !vm.state.value.isSending }
                assertNull(vm.state.value.selectedConversationId)
                assertEquals("failed new\n\nmore local\n\nnew home", vm.state.value.draft)
            } finally { onMain { store.clear() } }
        }
    }

    private class FakeGateway : AutoCloseable {
        private val server = ServerSocket(0)
        val url = "http://127.0.0.1:${server.localPort}"
        val requests = CopyOnWriteArrayList<Pair<String, String>>()
        @Volatile var block: (String) -> Unit = {}
        @Volatile var failurePath: String? = null
        private val stopped = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val socket = try { server.accept() } catch (_: Exception) { break }
                    thread(isDaemon = true) {
                        socket.use {
                            val reader = it.getInputStream().bufferedReader()
                            val path = reader.readLine()?.split(' ')?.getOrNull(1) ?: return@use
                            var length = 0
                            while (true) {
                                val header = reader.readLine() ?: return@use
                                if (header.isEmpty()) break
                                if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                            }
                            val chars = CharArray(length)
                            var read = 0
                            while (read < length) { val n = reader.read(chars, read, length - read); if (n < 0) break; read += n }
                            requests += path to String(chars)
                            block(path)
                            if (path.contains("CancelCascadeInvocation") && failurePath?.let(path::contains) != true)
                                stopped += JSONObject(String(chars)).getString("cascadeId")
                            val id = if (path.contains("cascadeId=A")) "A" else "B"
                            val body = when {
                                path.startsWith("/gateway/projects") -> "[]"
                                path.startsWith("/gateway/cascade/messages") -> """{"status":"${if (id in stopped) "IDLE" else "RUNNING"}","messages":[],"cascadeId":"$id"}"""
                                path.contains("GetAllCascadeTrajectories") -> """{"trajectorySummaries":{}}"""
                                else -> "{}"
                            }.toByteArray()
                            val status = when {
                                failurePath?.let(path::contains) == true -> "500 Failure"
                                path.startsWith("/gateway/cascade/stream") -> "503 Unavailable"
                                else -> "200 OK"
                            }
                            val output = it.getOutputStream()
                            output.write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            output.write(body)
                        }
                    }
                }
            }
        }
        override fun close() { server.close() }
    }
}
