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
        val original = PreferencesManager(application)
        assertTrue("Device must already be paired", original.isPaired())
        val override = InstrumentationRegistry.getArguments().getString("uxGatewayUrl")
        val prefs = if (override == null) original else PreferencesManager(isolatedApplication()).apply {
            gatewayBaseUrl = override; deviceToken = original.deviceToken
        }
        val api = ApiClient(application, prefs)
        runBlocking {
            // An empty prompt creates a test session without sending a message or starting an Agent task.
            val id = api.createCascade("", "", "gemini-3.8-flash-high", ProjectItem.PURE_CHAT.id).getOrThrow()
            try {
                val title = "UX 验收 ${UUID.randomUUID().toString().take(8)} " + "长标题原文".repeat(12)
                api.renameConversation(id, title).getOrThrow()
                assertEquals(title, api.fetchConversations().getOrThrow().first { it.id == id }.title)
                api.setConversationPinned(id, true).getOrThrow()
                api.setConversationArchived(id, true).getOrThrow()
                val archived = api.fetchConversations().getOrThrow().first { it.id == id }
                assertTrue(archived.isArchived)
                assertTrue(archived.isPinned)
                api.renameConversation(id, title + " archived").getOrThrow()
                api.setConversationArchived(id, false).getOrThrow()
                api.setConversationPinned(id, false).getOrThrow()
                val restored = api.fetchConversations().getOrThrow().first { it.id == id }
                assertFalse(restored.isArchived)
                assertFalse(restored.isPinned)
                assertEquals(title + " archived", restored.title)
            } finally {
                api.deleteConversation(id).getOrThrow()
            }
        }
        if (override != null && InstrumentationRegistry.getArguments().getString("uxUseGateway") == "true")
            original.updateEndpoints(lan = override, active = override)
    }

    @Test fun realGatewayRewindsADisposableConversation() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("uxRealRevert") == "true")
        val original = PreferencesManager(compose.activity.application)
        val app = isolatedApplication()
        val prefs = PreferencesManager(app).apply {
            gatewayBaseUrl = InstrumentationRegistry.getArguments().getString("uxGatewayUrl") ?: original.gatewayBaseUrl
            deviceToken = original.deviceToken
        }
        val api = ApiClient(app, prefs)
        runBlocking {
            suspend fun completed(id: String, text: String): com.antigravity.mobile.data.model.StreamUpdatePayload =
                kotlinx.coroutines.withTimeout(90_000) {
                    while (true) {
                        val data = api.fetchMessages(id).getOrThrow()
                        if (!data.status.contains("RUNNING", true) && data.messages.orEmpty().any { it.isUser && it.effectiveText == text } &&
                            data.messages.orEmpty().any { it.isAgent }) return@withTimeout data
                        kotlinx.coroutines.delay(500)
                    }
                    error("unreachable")
                }
            val first = "这是隔离的交互验收会话，只回复 FIRST_OK，不执行工具，不读写任何文件。"
            val second = "第二轮验收，只回复 SECOND_OK，不执行工具，不读写任何文件。"
            val id = api.createCascade("", first, "gemini-3.8-flash-high", ProjectItem.PURE_CHAT.id).getOrThrow()
            try {
                completed(id, first)
                api.sendMessage(id, second).getOrThrow()
                val before = completed(id, second)
                val message = before.messages.orEmpty().first { it.isUser && it.effectiveText == second }
                assertTrue(message.revertReason, message.canRevert)
                val vm = ChatViewModel(app)
                val store = ViewModelStore().apply { put("real-revert", vm) }
                try {
                    compose.runOnIdle { vm.openConversation(id) }
                    compose.waitUntil(15_000) { !vm.state.value.isLoadingMessages }
                    compose.runOnIdle { vm.previewRevert(message) }
                    compose.waitUntil(15_000) { !vm.state.value.isLoadingRevert }
                    assertNotNull(vm.state.value.revertPreview)
                    compose.runOnIdle { vm.executeRevert(true) }
                    compose.waitUntil(30_000) { !vm.state.value.isReverting && !vm.state.value.isLoadingMessages }
                    assertNull(vm.state.value.revertError)
                    assertEquals(second, vm.state.value.draft)
                    assertTrue(vm.state.value.messages.any { it.isUser && it.effectiveText == first })
                    assertFalse(vm.state.value.messages.any { it.isUser && it.effectiveText == second })
                    val after = api.fetchMessages(id).getOrThrow()
                    assertEquals(before.activeModel, after.activeModel)
                } finally { compose.runOnIdle { store.clear() } }
            } finally { api.deleteConversation(id).getOrThrow() }
        }
    }

    @Test fun realGatewayRestoresAnIsolatedFile() {
        val workspace = InstrumentationRegistry.getArguments().getString("uxRevertWorkspace")
        org.junit.Assume.assumeTrue(!workspace.isNullOrBlank())
        val original = PreferencesManager(compose.activity.application)
        val app = isolatedApplication()
        val prefs = PreferencesManager(app).apply {
            gatewayBaseUrl = InstrumentationRegistry.getArguments().getString("uxGatewayUrl") ?: original.gatewayBaseUrl
            deviceToken = original.deviceToken
        }
        val api = ApiClient(app, prefs)
        runBlocking {
            val file = "$workspace/probe.txt"
            val prompt = "这是隔离的文件回退验收。只将 $file 中的 BEFORE 改为 AFTER，使用文件编辑工具，不创建计划或其他文件，不操作工作区之外的文件。完成后只回复 FILE_OK。"
            val id = api.createCascade(workspace!!, prompt, "gemini-3.8-flash-high").getOrThrow()
            try {
                val before = kotlinx.coroutines.withTimeout(90_000) {
                    var data = api.fetchMessages(id).getOrThrow()
                    while (data.status.contains("RUNNING", true) || data.messages.orEmpty().none { it.isAgent }) {
                        kotlinx.coroutines.delay(500)
                        data = api.fetchMessages(id).getOrThrow()
                    }
                    data
                }
                assertEquals("AFTER", api.fetchFileContent(file, id).getOrThrow().content.trim())
                val message = before.messages.orEmpty().first { it.isUser }
                val preview = api.getRevertPreview(id, message.stepIndex!!).getOrThrow()
                assertTrue(preview.hasCodeChanges)
                assertTrue(preview.files.any { it.fileName == "probe.txt" })
                api.executeRevert(id, message.stepIndex!!, false).getOrThrow()
                assertEquals("BEFORE", api.fetchFileContent(file, id).getOrThrow().content.trim())
                val after = api.fetchMessages(id).getOrThrow()
                assertFalse(after.messages.orEmpty().any { it.isUser && it.effectiveText == prompt })
                assertEquals(before.activeModel, after.activeModel)
            } finally { api.deleteConversation(id).getOrThrow() }
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
                ConversationTopBar(false, {}, {}, { renamed = true }, { deleted = true }, title = "会话交互验收")
                Composer(activeConversation = true, draft = "next message", attachments = emptyList(),
                    onDraftChange = {}, onAddImage = {}, onRemoveImage = {}, onSend = { sends++ },
                    isSending = false, onUnsupported = {}, isRunning = running.value,
                    isStopping = stopping.value, onStop = { stops++; stopping.value = true })
            }
        }
        compose.onNodeWithContentDescription("更多选项").performClick()
        saveScreenshot("management-menu.png")
        compose.onNodeWithContentDescription("重命名").performClick()
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
        val application = isolatedApplication()
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

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun isolatedApplication(): Application {
        val namespace = "ux_${UUID.randomUUID()}_"
        val context = object : ContextWrapper(compose.activity.applicationContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(namespace + name, mode)
            override fun getCacheDir(): File = File(super.getCacheDir(), namespace).apply { mkdirs() }
        }
        return object : Application() { fun attach(context: Context) { attachBaseContext(context) } }.apply { attach(context) }
    }

    @Test fun archiveScreenAndMenuUseOnlyTheRequestedActions() {
        var restored = ""
        var renamed = ""
        var deleted = ""
        val items = listOf(com.antigravity.mobile.data.model.ConversationItem("A", "Archived A", isArchived = true),
            com.antigravity.mobile.data.model.ConversationItem("B", "Active B"))
        compose.setContent { ArchivedConversations(items, false, emptySet(), {}, {}, {},
            { renamed = it.id }, { restored = it }, { deleted = it }) }
        compose.onNodeWithText("Archived A").assertExists()
        compose.onNodeWithText("Active B").assertDoesNotExist()
        compose.onNodeWithContentDescription("归档会话操作").performClick()
        compose.onNodeWithText("恢复会话").assertExists()
        saveScreenshot("management-archive.png")
        compose.onNodeWithText("置顶").assertDoesNotExist()
        compose.onNodeWithContentDescription("重命名").performClick()
        assertEquals("A", renamed)
        compose.onNodeWithContentDescription("归档会话操作").performClick()
        compose.onNodeWithText("恢复会话").performClick()
        assertEquals("A", restored)
        compose.onNodeWithContentDescription("归档会话操作").performClick()
        compose.onNodeWithText("删除会话").performClick()
        assertEquals("A", deleted)
        compose.onNode(hasSetTextAction()).performTextInput("missing")
        compose.onNodeWithText("没有匹配的会话").assertExists()
    }

    @Test fun managementAndRevertKeepDraftsAndRollbackFailures() {
        val app = isolatedApplication()
        FakeGateway().use { gateway ->
            gateway.management = true
            val prefs = PreferencesManager(app).apply { gatewayBaseUrl = gateway.url; deviceToken = "test-token" }
            val vm = ChatViewModel(app)
            val store = ViewModelStore().apply { put("test", vm) }
            fun onMain(action: () -> Unit) = compose.runOnIdle(action)
            fun settled() = compose.waitUntil(10_000) { vm.state.value.busyConversations.isEmpty() && !vm.state.value.isLoadingConversations }
            try {
                compose.waitUntil(10_000) { vm.state.value.conversations.size == 2 }
                onMain { vm.setPinned("B", true) }; settled()
                assertEquals("B", vm.state.value.conversations.first().id)
                onMain { vm.setArchived("B", true) }; settled()
                assertTrue(vm.state.value.conversations.first { it.id == "B" }.isArchived)
                var renamed = false
                onMain { vm.renameConversation("B", "Archived title") { renamed = true } }; settled()
                assertTrue(renamed)
                onMain { vm.setArchived("B", false); vm.openConversation("A") }; settled()
                compose.waitUntil(10_000) { !vm.state.value.isLoadingMessages }
                onMain { vm.setDraft("unsent draft") }
                val message = vm.state.value.messages.first()
                onMain { vm.previewRevert(message) }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingRevert }
                assertNotNull(vm.state.value.revertPreview)
                gateway.failurePath = "/revert/execute"
                onMain { vm.executeRevert(true) }
                compose.waitUntil(10_000) { !vm.state.value.isReverting }
                assertEquals("unsent draft", vm.state.value.draft)
                assertEquals(1, vm.state.value.messages.size)
                assertNotNull(vm.state.value.revertError)
                gateway.failurePath = null
                onMain { vm.executeRevert(true) }
                compose.waitUntil(10_000) { !vm.state.value.isReverting && !vm.state.value.isLoadingMessages }
                assertEquals("original message\n\nunsent draft", vm.state.value.draft)
                assertTrue(vm.state.value.messages.isEmpty())
                val payload = gateway.requests.last { it.first.contains("/revert/execute") }.second
                assertTrue(JSONObject(payload).getBoolean("conversationOnly"))
                onMain { vm.setPinned("B", false) }; settled()
                assertFalse(vm.state.value.conversations.first { it.id == "B" }.isPinned)
                gateway.reverted = false
                onMain { vm.openConversation("A"); vm.setDraft("A next") }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingMessages }
                onMain { vm.previewRevert(vm.state.value.messages.first()) }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingRevert }
                val requested = CountDownLatch(1)
                val release = CountDownLatch(1)
                gateway.block = { path -> if (path.contains("/revert/execute")) { requested.countDown(); release.await(10, TimeUnit.SECONDS) } }
                onMain { vm.executeRevert(true) }
                assertTrue(requested.await(10, TimeUnit.SECONDS))
                onMain { vm.openConversation("B"); vm.setDraft("B untouched") }
                release.countDown()
                settled()
                assertEquals("B", vm.state.value.selectedConversationId)
                assertEquals("B untouched", vm.state.value.draft)
                onMain { vm.openConversation("A") }
                assertEquals("original message\n\nA next", vm.state.value.draft)
            } finally { onMain { store.clear() } }
        }
    }

    @Test fun archiveNavigationReturnsToTheListAfterDeletingItsOpenConversation() {
        val app = isolatedApplication()
        FakeGateway().use { gateway ->
            gateway.management = true
            PreferencesManager(app).apply { gatewayBaseUrl = gateway.url; deviceToken = "test-token" }
            val vm = ChatViewModel(app)
            val store = ViewModelStore().apply { put("archive-navigation", vm) }
            try {
                compose.setContent { ChatDemoScreen(vm, {}) }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingConversations }
                compose.runOnIdle { vm.setArchived("B", true) }
                compose.waitUntil(10_000) { vm.state.value.busyConversations.isEmpty() && !vm.state.value.isLoadingConversations }
                compose.onNodeWithContentDescription("打开菜单").performClick()
                compose.onNodeWithText("已归档").performClick()
                compose.onNodeWithText("搜索归档会话").assertIsDisplayed()
                compose.onNodeWithContentDescription("发送消息").assertDoesNotExist()
                compose.onNodeWithText("B").performClick()
                compose.onNodeWithContentDescription("返回归档列表").assertIsDisplayed()
                compose.onNodeWithContentDescription("更多选项").performClick()
                compose.onNodeWithText("删除会话").performClick()
                compose.onNodeWithText("删除", useUnmergedTree = true).performClick()
                compose.waitUntil(10_000) { vm.state.value.selectedConversationId == null }
                compose.onNodeWithText("搜索归档会话").assertIsDisplayed()
                compose.onNodeWithContentDescription("发送消息").assertDoesNotExist()
            } finally { compose.runOnIdle { store.clear() } }
        }
    }

    @Test fun revertRestoresOriginalImagesAndPreservesMessagesWhenReadingThemFails() {
        val app = isolatedApplication()
        FakeGateway().use { gateway ->
            gateway.management = true; gateway.userImage = true
            PreferencesManager(app).apply { gatewayBaseUrl = gateway.url; deviceToken = "test-token" }
            val vm = ChatViewModel(app)
            val store = ViewModelStore().apply { put("image-revert", vm) }
            try {
                compose.runOnIdle { vm.openConversation("A") }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingMessages }
                compose.runOnIdle { vm.previewRevert(vm.state.value.messages.first()) }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingRevert }
                gateway.failurePath = "/image.png"
                compose.runOnIdle { vm.executeRevert(true) }
                compose.waitUntil(10_000) { !vm.state.value.isReverting }
                assertNotNull(vm.state.value.revertError)
                assertEquals(1, vm.state.value.messages.size)
                assertFalse(gateway.requests.any { it.first.contains("/revert/execute") })
                gateway.failurePath = null
                compose.runOnIdle { vm.executeRevert(true) }
                compose.waitUntil(10_000) { !vm.state.value.isReverting && !vm.state.value.isLoadingMessages }
                assertEquals("image/png", vm.state.value.attachments.single().mimeType)
                assertArrayEquals(gateway.imageBytes, vm.state.value.attachments.single().bytes)
                assertEquals("original message", vm.state.value.draft)
            } finally { compose.runOnIdle { store.clear() } }
        }
    }

    private class FakeGateway : AutoCloseable {
        private val server = ServerSocket(0)
        val url = "http://127.0.0.1:${server.localPort}"
        val requests = CopyOnWriteArrayList<Pair<String, String>>()
        @Volatile var block: (String) -> Unit = {}
        @Volatile var failurePath: String? = null
        @Volatile var management = false
        @Volatile var reverted = false
        @Volatile var userImage = false
        val imageBytes = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==", android.util.Base64.DEFAULT)
        private val annotations = java.util.concurrent.ConcurrentHashMap<String, JSONObject>()
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
                            if (management && failurePath?.let(path::contains) != true) {
                                if (path.contains("UpdateConversationAnnotations")) {
                                    val update = JSONObject(String(chars))
                                    val key = update.getJSONArray("cascadeIds").getString(0)
                                    val ann = annotations.getOrPut(key) { JSONObject() }
                                    val changes = update.getJSONObject("annotations")
                                    changes.keys().forEach { ann.put(it, changes.get(it)) }
                                }
                                if (path.contains("/revert/execute")) reverted = true
                            }
                            val id = if (path.contains("cascadeId=A")) "A" else "B"
                            val body = when {
                                path.startsWith("/gateway/projects") -> "[]"
                                management && path.contains("/revert/preview") -> """{"cascadeId":"A","stepIndex":0,"targetStepIndex":-1,"files":[]}"""
                                management && path.startsWith("/gateway/cascade/messages") ->
                                    """{"status":"IDLE","messages":${if (reverted) "[]" else "[{\"id\":\"step-0\",\"type\":\"user\",\"text\":\"original message\",\"stepIndex\":0,\"canRevert\":true${if (userImage) ",\"imageUrls\":[\"$url/image.png\"]" else ""}}]"},"cascadeId":"$id"}"""
                                management && path.contains("GetAllCascadeTrajectories") -> {
                                    val summaries = JSONObject()
                                    for (key in listOf("A", "B")) summaries.put(key, JSONObject().put("summary", key)
                                        .put("status", "IDLE").put("annotations", annotations[key] ?: JSONObject()))
                                    JSONObject().put("trajectorySummaries", summaries).toString()
                                }
                                path.startsWith("/gateway/cascade/messages") -> """{"status":"${if (id in stopped) "IDLE" else "RUNNING"}","messages":[],"cascadeId":"$id"}"""
                                path.contains("GetAllCascadeTrajectories") -> """{"trajectorySummaries":{}}"""
                                else -> "{}"
                            }.toByteArray().let { if (path.startsWith("/image.png")) imageBytes else it }
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
