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
                assertTrue(message.revertReason, message.canRevert == true)
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

    @Test fun realGatewayRevertsDuringGeneration() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("uxRealRunningRevert") == "true")
        val original = PreferencesManager(compose.activity.application)
        val app = isolatedApplication()
        val prefs = PreferencesManager(app).apply {
            gatewayBaseUrl = InstrumentationRegistry.getArguments().getString("uxGatewayUrl") ?: original.gatewayBaseUrl
            deviceToken = original.deviceToken
        }
        val api = ApiClient(app, prefs)
        runBlocking {
            val prompt = "隔离回退验收：不使用工具、不读写文件，请逐行列出1到10000，不要省略。"
            val id = api.createCascade("", "只回复 READY，不使用工具、不读写文件。", "gemini-3.8-flash-high", ProjectItem.PURE_CHAT.id).getOrThrow()
            try {
                kotlinx.coroutines.withTimeout(60_000) {
                    while (true) {
                        val data = api.fetchMessages(id).getOrThrow()
                        check(!data.hasError) { data.errorMessage ?: "测试会话初始化失败" }
                        if (!data.status.contains("RUNNING", true) && data.messages.orEmpty().any { it.isAgent }) break
                        kotlinx.coroutines.delay(250)
                    }
                }
                val vm = ChatViewModel(app)
                val store = ViewModelStore().apply { put("real-running-ui", vm) }
                try {
                    compose.setContent { ChatDemoScreen(vm, {}) }
                    compose.runOnIdle { vm.openConversation(id) }
                    compose.waitUntil(15_000) { !vm.state.value.isLoadingMessages }
                    compose.runOnIdle { vm.setDraft(prompt); vm.send() }
                    compose.waitUntil(30_000) { vm.state.value.isRunning && !vm.state.value.isSending &&
                        vm.state.value.messages.any { it.isUser && it.effectiveText == prompt } }
                    val message = vm.state.value.messages.first { it.isUser && it.effectiveText == prompt }
                    assertTrue("${prefs.gatewayBaseUrl}: ${message.revertReason}", message.canRevert == true)
                    compose.onAllNodesWithContentDescription("回退到这条消息").onLast().assertIsDisplayed()
                    saveScreenshot("revert-running-button.png")
                    compose.onAllNodesWithContentDescription("回退到这条消息").onLast().performClick()
                    compose.waitUntil(15_000) { vm.state.value.revertPreview != null || vm.state.value.revertError != null }
                    assertNull(vm.state.value.revertError)
                    compose.onNode(isToggleable()).assertIsOn().performClick()
                    saveScreenshot("revert-running-real.png")
                    assertTrue("Native UI confirmation must be tested while running", vm.state.value.isRunning)
                    compose.onNodeWithText("确认回退").assertIsEnabled().performClick()
                    compose.waitUntil(20_000) { !vm.state.value.isReverting && !vm.state.value.isLoadingMessages }
                    assertNull(vm.state.value.revertError)
                    assertEquals(prompt, vm.state.value.draft)
                } finally { compose.runOnIdle { store.clear() } }
                kotlinx.coroutines.withTimeout(20_000) {
                    while (true) {
                        val data = api.fetchMessages(id).getOrThrow()
                        if (!data.status.contains("RUNNING", true) && data.messages.orEmpty().none { it.effectiveText == prompt }) break
                        kotlinx.coroutines.delay(250)
                    }
                }
                val after = api.fetchMessages(id).getOrThrow()
                assertFalse(after.status.contains("RUNNING", true))
                assertFalse(after.messages.orEmpty().any { it.effectiveText == prompt })
            } finally {
                api.cancelInvocation(id)
                api.deleteConversation(id).getOrThrow()
            }
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
            val workspaceUri = "file:///" + workspace!!.replace('\\', '/').trimStart('/')
            val id = api.createCascade(workspaceUri, prompt, "gemini-3.8-flash-high").getOrThrow()
            try {
                val before = kotlinx.coroutines.withTimeout(90_000) {
                    var data = api.fetchMessages(id).getOrThrow()
                    while (data.status.contains("RUNNING", true) || data.messages.orEmpty().none { it.isAgent }) {
                        check(!data.hasError) { data.errorMessage ?: "隔离文件会话执行失败" }
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
                val vm = ChatViewModel(app)
                val store = ViewModelStore().apply { put("real-file-revert", vm) }
                try {
                    compose.setContent { ChatDemoScreen(vm, {}) }
                    compose.runOnIdle { vm.openConversation(id) }
                    compose.waitUntil(15_000) { !vm.state.value.isLoadingMessages }
                    compose.runOnIdle { vm.previewRevert(message) }
                    compose.waitUntil(15_000) { vm.state.value.revertPreview != null }
                    compose.onNode(isToggleable()).assertIsOn()
                    compose.onNodeWithText("确认回退").performClick()
                    compose.waitUntil(30_000) { !vm.state.value.isReverting && !vm.state.value.isLoadingMessages }
                    assertNull(vm.state.value.revertError)
                } finally { compose.runOnIdle { store.clear() } }
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
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var captured = automation.takeScreenshot()
        if (captured == null) { Thread.sleep(200); captured = automation.takeScreenshot() }
        val bitmap = checkNotNull(captured) { "系统截图未返回，未生成 $name" }
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
        compose.onNodeWithText("清空").assertDoesNotExist()
        compose.onNodeWithContentDescription("清除搜索").performClick()
        compose.onNodeWithText("Archived A").assertIsDisplayed()
        compose.onNodeWithContentDescription("清除搜索").assertDoesNotExist()
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

    @Test fun reconnectRetriesAndLateSnapshotsPreserveMessagesDraftsAndOtherConversations() {
        val app = isolatedApplication()
        FakeGateway().use { gateway ->
            gateway.management = true; gateway.liveStream = true
            gateway.failurePath = "/gateway/cascade/messages"
            PreferencesManager(app).apply { gatewayBaseUrl = gateway.url; deviceToken = "test-token" }
            val vm = ChatViewModel(app)
            val store = ViewModelStore().apply { put("recovery", vm) }
            fun main(action: () -> Unit) = compose.runOnIdle(action)
            try {
                main { vm.openConversation("A"); vm.setDraft("keep draft") }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingMessages && vm.state.value.messagesError != null }
                assertTrue(vm.state.value.messages.isEmpty())
                gateway.failurePath = null
                main { vm.retryMessages(); vm.retryMessages() }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingMessages && vm.state.value.messages.isNotEmpty() }
                assertNull(vm.state.value.messagesError)
                gateway.messageText = "after reconnect"
                gateway.dropStreams()
                compose.waitUntil(15_000) { vm.state.value.connectionStatus == com.antigravity.mobile.data.service.ConnectionStatus.CONNECTED &&
                    vm.state.value.messages.any { it.effectiveText == "after reconnect" } }
                assertEquals("keep draft", vm.state.value.draft)

                val requested = CountDownLatch(1); val release = CountDownLatch(1)
                gateway.block = { path -> if (path.startsWith("/gateway/cascade/messages")) { requested.countDown(); release.await(10, TimeUnit.SECONDS) } }
                main { vm.retryMessages() }
                assertTrue(requested.await(10, TimeUnit.SECONDS))
                gateway.emit("A", """{"cascadeId":"A","status":"RUNNING","messages":[{"id":"step-0","type":"user","text":"new stream"}]}""")
                compose.waitUntil(10_000) { vm.state.value.messages.any { it.effectiveText == "new stream" } }
                release.countDown()
                compose.waitForIdle()
                assertEquals("new stream", vm.state.value.messages.single().effectiveText)
                assertTrue(vm.state.value.isRunning)
                gateway.block = {}
                gateway.failurePath = "GetAllCascadeTrajectories"
                main { vm.refreshConversations() }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingConversations }
                assertNotNull(vm.state.value.conversationsError)
                assertEquals(2, vm.state.value.conversations.size)
                val listsBefore = gateway.requests.count { it.first.contains("GetAllCascadeTrajectories") }
                gateway.failurePath = null
                main { vm.refreshConversations(); vm.refreshConversations() }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingConversations }
                assertNull(vm.state.value.conversationsError)
                assertEquals(listsBefore + 1, gateway.requests.count { it.first.contains("GetAllCascadeTrajectories") })
                main { vm.openConversation("B"); vm.setDraft("B draft") }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingMessages && vm.state.value.connectionStatus == com.antigravity.mobile.data.service.ConnectionStatus.CONNECTED }
                assertEquals("B", vm.state.value.selectedConversationId)
                assertEquals("B draft", vm.state.value.draft)
                gateway.failurePath = "/gateway/cascade/messages"; gateway.failureCode = 401
                main { vm.retryMessages() }
                compose.waitUntil(10_000) { !vm.state.value.isPaired }
                assertEquals("B draft", vm.state.value.draft)
                PreferencesManager(app).deviceToken = "test-token"
                gateway.failurePath = "/gateway/cascade/stream"
                val socketVm = ChatViewModel(app)
                val socketStore = ViewModelStore().apply { put("socket-auth", socketVm) }
                try {
                    main { socketVm.openConversation("A") }
                    compose.waitUntil(10_000) { !socketVm.state.value.isPaired }
                    assertEquals("keep draft", socketVm.state.value.draft)
                } finally { main { socketStore.clear() } }
            } finally { main { store.clear() } }
        }
    }

    @Test fun realEmulatorOfflineRecoveryKeepsTheDraft() {
        org.junit.Assume.assumeTrue(android.os.Build.HARDWARE == "ranchu" &&
            InstrumentationRegistry.getArguments().getString("round2RealNetwork") == "true")
        val original = PreferencesManager(compose.activity.application)
        val app = isolatedApplication()
        PreferencesManager(app).apply {
            gatewayBaseUrl = InstrumentationRegistry.getArguments().getString("uxGatewayUrl") ?: original.gatewayBaseUrl
            deviceToken = original.deviceToken
        }
        fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        val wifi = shell("settings get global wifi_on").trim() == "1"
        val data = shell("settings get global mobile_data").trim() == "1"
        val api = ApiClient(app, PreferencesManager(app))
        val id = runBlocking { api.createCascade("", "", "gemini-3.8-flash-high", ProjectItem.PURE_CHAT.id).getOrThrow() }
        val vm = ChatViewModel(app)
        val store = ViewModelStore().apply { put("offline", vm) }
        try {
            compose.setContent { ChatDemoScreen(vm, {}) }
            compose.runOnIdle { vm.openConversation(id); vm.setDraft("网络恢复验收草稿，不发送") }
            compose.waitUntil(20_000) { !vm.state.value.isLoadingMessages && vm.state.value.connectionStatus == com.antigravity.mobile.data.service.ConnectionStatus.CONNECTED }
            shell("svc wifi disable"); shell("svc data disable")
            compose.runOnIdle { vm.retryMessages() }
            compose.waitUntil(30_000) { !vm.state.value.isLoadingMessages && vm.state.value.messagesError != null }
            assertEquals("网络恢复验收草稿，不发送", vm.state.value.draft)
            saveScreenshot("round2-offline.png")
            shell("svc wifi ${if (wifi) "enable" else "disable"}"); shell("svc data ${if (data) "enable" else "disable"}")
            val network = app.getSystemService(android.net.ConnectivityManager::class.java)
            compose.waitUntil(30_000) { network.activeNetwork != null }
            compose.runOnIdle { vm.retryMessages() }
            compose.waitUntil(30_000) { !vm.state.value.isLoadingMessages && vm.state.value.messagesError == null &&
                vm.state.value.connectionStatus == com.antigravity.mobile.data.service.ConnectionStatus.CONNECTED }
            assertEquals("网络恢复验收草稿，不发送", vm.state.value.draft)
        } finally {
            shell("svc wifi ${if (wifi) "enable" else "disable"}"); shell("svc data ${if (data) "enable" else "disable"}")
            compose.runOnIdle { store.clear() }
            runBlocking { api.deleteConversation(id).getOrThrow() }
        }
    }

    @Test fun searchSelectionAndProjectExpansionSurviveFilteringAndReordering() {
        val projects = mutableStateOf(listOf(ProjectItem(rawId = "p", name = "Project 1"), ProjectItem(rawId = "q", name = "Project 2")))
        val chats = listOf(com.antigravity.mobile.data.model.ConversationItem("A", "中文会话"),
            com.antigravity.mobile.data.model.ConversationItem("P", "项目对话", projectId = "p", workspaceName = "Project 1"))
        compose.setContent { DemoDrawer(Modifier.fillMaxSize(), chats, projects.value, false, false, {}, {}, {}, {}, {}, selectedConversationId = "A") }
        compose.onNodeWithText("中文会话").assertIsSelected()
        val row = compose.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Selected, true)).fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("中文会话", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(row.center.y, label.center.y, 1f)
        assertTrue(label.left > row.left)
        compose.onNodeWithText("项目").performClick()
        compose.onNodeWithText("Project 1").performClick()
        compose.onNodeWithText("项目对话").assertIsDisplayed()
        compose.onNodeWithContentDescription("搜索").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("missing")
        compose.onNodeWithText("没有匹配的最近会话").assertIsDisplayed()
        compose.onNodeWithText("没有匹配的项目").assertIsDisplayed()
        compose.onNodeWithText("清空").assertDoesNotExist()
        compose.onNodeWithContentDescription("清除搜索").performClick()
        compose.runOnIdle { projects.value = projects.value.reversed() }
        compose.onNodeWithText("项目对话").assertIsDisplayed()
        compose.onNodeWithText("中文会话").assertIsSelected()
        saveScreenshot("round2-search.png")
    }

    @Test fun pageEntrancesPreserveProjectStateDraftAndKeyboardBack() {
        val app = isolatedApplication()
        FakeGateway().use { gateway ->
            gateway.management = true
            gateway.projectFixture = true
            PreferencesManager(app).apply { gatewayBaseUrl = gateway.url; deviceToken = "test-token" }
            val vm = ChatViewModel(app)
            val store = ViewModelStore().apply { put("motion-navigation", vm) }
            try {
                compose.setContent { ChatDemoScreen(vm, {}) }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingConversations }
                compose.onNode(hasText("项目") and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected)).performClick()
                compose.onNodeWithText("动效项目").performClick()
                val headerY = compose.onNodeWithText("动效项目").fetchSemanticsNode().boundsInRoot.top
                compose.onNodeWithText("B").performClick()
                compose.waitUntil(10_000) { vm.state.value.selectedConversationId == "B" && !vm.state.value.isLoadingMessages }
                compose.onNode(hasSetTextAction()).performClick().performTextReplacement("第一行\n第二行\n第三行\n第四行")
                compose.waitForIdle()
                compose.onNodeWithContentDescription("发送消息").assertIsDisplayed()
                saveScreenshot("round3-screen-keyboard.png")
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                compose.waitForIdle()
                compose.onNodeWithContentDescription("返回项目列表").assertIsDisplayed()
                compose.onNodeWithContentDescription("返回项目列表").performClick()
                compose.onNodeWithText("B").assertIsDisplayed()
                assertEquals(headerY, compose.onNodeWithText("动效项目").fetchSemanticsNode().boundsInRoot.top, 1f)
                compose.onNodeWithText("B").performClick()
                compose.onNode(hasSetTextAction()).assertTextEquals("第一行\n第二行\n第三行\n第四行")
                compose.onNodeWithContentDescription("更多选项").performClick()
                compose.mainClock.autoAdvance = false
                compose.runOnUiThread { vm.openConversation("A") }
                compose.mainClock.advanceTimeBy(32)
                compose.runOnUiThread { vm.openConversation("B") }
                compose.mainClock.advanceTimeBy(32)
                compose.runOnUiThread { vm.openConversation("A") }
                compose.mainClock.advanceTimeBy(260)
                compose.mainClock.autoAdvance = true
                compose.waitUntil(10_000) { vm.state.value.selectedConversationId == "A" && !vm.state.value.isLoadingMessages }
                compose.waitForIdle()
                saveScreenshot("round3-rapid-navigation.png")
                compose.onNodeWithText("置顶").assertDoesNotExist()
                compose.onNodeWithText("original message").assertIsDisplayed()
                compose.runOnIdle { vm.openConversation("B") }
                compose.onNode(hasSetTextAction()).assertTextEquals("第一行\n第二行\n第三行\n第四行")
            } finally { compose.mainClock.autoAdvance = true; compose.runOnIdle { store.clear() } }
        }
    }

    @Test fun imagesZoomResetSwitchCloseAndRetry() {
        val app = isolatedApplication()
        FakeGateway().use { gateway ->
            PreferencesManager(app).apply { gatewayBaseUrl = gateway.url; deviceToken = "test-token" }
            val vm = ChatViewModel(app)
            val store = ViewModelStore().apply { put("image-viewer", vm) }
            val index = mutableStateOf(0); val showing = mutableStateOf(true)
            val linked = mutableStateOf(false)
            try {
                gateway.failurePath = "/image.png"
                val files = listOf("image.png", "wide.png", "long.png")
                compose.setContent { if (showing.value) {
                    if (linked.value) LinkedFilePreview("${gateway.url}/image.png", vm) { showing.value = false }
                    else ImageViewer(vm.mediaImageRequest("${gateway.url}/image.png"), vm.mediaImageLoader,
                        "图片验收", { showing.value = false }, index.value, files.size, { index.value = it },
                        modelAt = { vm.mediaImageRequest("${gateway.url}/${files[it]}") })
                } }
                compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("重试加载图片").fetchSemanticsNodes().isNotEmpty() }
                gateway.failurePath = null
                compose.onNodeWithContentDescription("重试加载图片").performClick()
                fun loaded(page: Int) {
                    compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("图片验收 ${page + 1}/3").fetchSemanticsNodes().any {
                        it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) { "" }.startsWith("已加载") } }
                }
                fun imageNode() = compose.onNodeWithContentDescription("图片验收 ${index.value + 1}/3")
                fun assertScale(expected: String) {
                    imageNode().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已加载，缩放 $expected%"))
                }
                loaded(0)
                val imageBounds = imageNode().fetchSemanticsNode().boundsInWindow
                val screenHeight = compose.activity.resources.displayMetrics.heightPixels
                val screenWidth = compose.activity.resources.displayMetrics.widthPixels
                assertTrue("Image popup must leave space above and below", imageBounds.height < screenHeight * .9f)
                assertTrue("Image popup must leave side margins", imageBounds.width < screenWidth * .97f)
                listOf("图片验收", "关闭", "放大", "缩小", "重置", "上一张", "下一张", "100%").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
                imageNode().performTouchInput { doubleClick(center) }
                assertScale("250")
                imageNode().performTouchInput {
                    val mid = center
                    down(0, mid - androidx.compose.ui.geometry.Offset(40f, 0f)); down(1, mid + androidx.compose.ui.geometry.Offset(40f, 0f))
                    moveTo(0, mid - androidx.compose.ui.geometry.Offset(120f, 0f)); moveTo(1, mid + androidx.compose.ui.geometry.Offset(120f, 0f))
                    up(0); up(1)
                }
                imageNode().assert(SemanticsMatcher("pinch changed scale") {
                    it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) { "" } != "已加载，缩放 250%" })
                imageNode().performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(0f, -200f)) }
                assertEquals(0, index.value)
                imageNode().performTouchInput { doubleClick(center) }
                assertScale("100")
                imageNode().performTouchInput { swipeLeft() }
                compose.waitUntil(10_000) { index.value == 1 }; loaded(1); assertScale("100")
                imageNode().performTouchInput { doubleClick(center) }
                assertScale("250")
                // A wide image pans first, then a second drag beyond its edge changes page.
                imageNode().performTouchInput { swipeLeft() }
                compose.waitForIdle()
                if (index.value == 1) imageNode().performTouchInput { swipeLeft() }
                compose.waitUntil(10_000) { index.value == 2 }; loaded(2); assertScale("100")
                imageNode().performTouchInput { swipeLeft() }
                assertEquals(2, index.value)
                imageNode().performTouchInput { swipeRight() }
                compose.waitUntil(10_000) { index.value == 1 }; loaded(1)
                saveScreenshot("ui-fix-image.png")
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                compose.waitUntil(5_000) { !showing.value }
                compose.runOnIdle { linked.value = true; showing.value = true }
                compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("文件图片预览").fetchSemanticsNodes().any {
                    it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) { "" }.startsWith("已加载") } }
                compose.onNodeWithText("关闭").assertDoesNotExist()
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                compose.waitUntil(5_000) { !showing.value }
            } finally { compose.runOnIdle { store.clear() } }
        }
    }

    @Test fun revertPreviewKeepsDialogSizeAndDefaultsToRestoringFiles() {
        val app = isolatedApplication()
        FakeGateway().use { gateway ->
            gateway.management = true; gateway.previewFiles = 6
            val requested = CountDownLatch(1); val release = CountDownLatch(1)
            gateway.block = { if (it.contains("/revert/preview")) { requested.countDown(); release.await(15, TimeUnit.SECONDS) } }
            PreferencesManager(app).apply { gatewayBaseUrl = gateway.url; deviceToken = "test-token" }
            val vm = ChatViewModel(app)
            val store = ViewModelStore().apply { put("revert-size", vm) }
            try {
                compose.setContent { ChatDemoScreen(vm, {}) }
                compose.runOnIdle { vm.openConversation("A"); vm.setDraft("preserved") }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingMessages }
                compose.onNodeWithContentDescription("回退到这条消息").performClick()
                assertTrue(requested.await(5, TimeUnit.SECONDS))
                Thread.sleep(350) // Let the native dialog entrance finish before measuring its layout.
                val loading = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
                saveScreenshot("revert-modes-loading.png")
                release.countDown()
                compose.waitUntil(10_000) { !vm.state.value.isLoadingRevert }
                val ready = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
                assertEquals("Preview must not resize dialog height", loading.height, ready.height, 1f)
                assertEquals("Preview must not resize dialog width", loading.width, ready.width, 1f)
                compose.onNode(isToggleable()).assertIsOn()
                compose.onNodeWithText("Agent.md").assertDoesNotExist()
                compose.onNodeWithText("没有文件改动").assertDoesNotExist()
                assertTrue("Confirmation must stay compact", ready.height < 200 * compose.activity.resources.displayMetrics.density *
                    compose.activity.resources.configuration.fontScale)
                saveScreenshot("revert-modes-ready.png")
                gateway.failurePath = "/revert/execute"
                compose.onNodeWithText("确认回退").performClick()
                compose.waitUntil(10_000) { !vm.state.value.isReverting && vm.state.value.revertError != null }
                assertFalse(JSONObject(gateway.requests.last { it.first.contains("/revert/execute") }.second).getBoolean("conversationOnly"))
                assertEquals("preserved", vm.state.value.draft)
                assertNotNull(vm.state.value.revertError)
                compose.onNode(isToggleable()).performClick()
                compose.onNode(isToggleable()).assertIsOff()
                gateway.failurePath = null
                compose.onNodeWithText("确认回退").performClick()
                compose.waitUntil(10_000) { gateway.reverted && !vm.state.value.isReverting }
                assertTrue(JSONObject(gateway.requests.last { it.first.contains("/revert/execute") }.second).getBoolean("conversationOnly"))
            } finally { release.countDown(); compose.runOnIdle { store.clear() } }
        }
    }

    @Test fun runningRevertKeepsUndoAndRejectsLateStreamMessages() {
        val app = isolatedApplication()
        FakeGateway().use { gateway ->
            gateway.management = true; gateway.running = true; gateway.liveStream = true
            PreferencesManager(app).apply { gatewayBaseUrl = gateway.url; deviceToken = "test-token" }
            val vm = ChatViewModel(app)
            val store = ViewModelStore().apply { put("running-revert", vm) }
            val requested = CountDownLatch(1); val release = CountDownLatch(1)
            try {
                compose.setContent { ChatDemoScreen(vm, {}) }
                compose.runOnIdle { vm.openConversation("A"); vm.setDraft("kept draft") }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingMessages && vm.state.value.isRunning &&
                    vm.state.value.connectionStatus == com.antigravity.mobile.data.service.ConnectionStatus.CONNECTED }
                compose.onNodeWithContentDescription("回退到这条消息").assertIsDisplayed().performClick()
                compose.waitUntil(10_000) { vm.state.value.revertPreview != null }
                compose.onNodeWithText("确认回退").assertIsEnabled()
                gateway.block = { if (it.contains("/revert/execute")) { requested.countDown(); release.await(10, TimeUnit.SECONDS) } }
                compose.onNodeWithText("确认回退").performClick()
                assertTrue(requested.await(5, TimeUnit.SECONDS))
                compose.runOnIdle { vm.executeRevert(false) }
                gateway.emit("A", """{"type":"update","cascadeId":"A","status":"RUNNING","messages":[{"id":"late","type":"agent","text":"late pre-revert reply"}]}""")
                release.countDown()
                compose.waitUntil(10_000) { gateway.reverted && !vm.state.value.isReverting && !vm.state.value.isLoadingMessages }
                compose.waitUntil(10_000) { vm.state.value.connectionStatus == com.antigravity.mobile.data.service.ConnectionStatus.CONNECTED }
                gateway.emit("A", """{"type":"init","cascadeId":"A","status":"IDLE","messages":[]}""")
                compose.waitForIdle()
                assertFalse(vm.state.value.isRunning)
                assertTrue(vm.state.value.messages.isEmpty())
                assertEquals("original message\n\nkept draft", vm.state.value.draft)
                assertEquals(1, gateway.requests.count { it.first.contains("/revert/execute") })
                compose.onNodeWithText("late pre-revert reply").assertDoesNotExist()
            } finally { release.countDown(); compose.runOnIdle { store.clear() } }
        }
    }

    @Test fun drawerGestureKeepsConversationVisibleBehindScrim() {
        val app = isolatedApplication()
        FakeGateway().use { gateway ->
            gateway.management = true
            PreferencesManager(app).apply { gatewayBaseUrl = gateway.url; deviceToken = "test-token" }
            val vm = ChatViewModel(app)
            val store = ViewModelStore().apply { put("drawer-visibility", vm) }
            try {
                compose.setContent { ChatDemoScreen(vm, {}) }
                compose.runOnIdle { vm.openConversation("A"); vm.setDraft("保留草稿") }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingMessages && vm.state.value.messages.isNotEmpty() }
                compose.onNodeWithText("original message").assertIsDisplayed()
                val text = compose.onNodeWithText("original message").fetchSemanticsNode().boundsInWindow
                val x = (text.right + 5).toInt(); val y = text.center.y.toInt()
                fun bubbleBlue(): Int {
                    val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                    val pixel = bitmap.getPixel(x, y)
                    bitmap.recycle()
                    return android.graphics.Color.blue(pixel) - android.graphics.Color.red(pixel)
                }
                assertTrue("Sample must be inside the blue user bubble", bubbleBlue() > 8)
                repeat(2) {
                    compose.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(width * .06f, height * .4f),
                        androidx.compose.ui.geometry.Offset(width * .85f, height * .4f), 600) }
                    compose.onNodeWithText("已归档").assertIsDisplayed()
                    compose.waitForIdle()
                    assertTrue("Conversation bubble must still be drawn behind drawer scrim", bubbleBlue() > 3)
                    saveScreenshot("ui-refine-drawer.png")
                    compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .97f, height * .4f)) }
                    compose.onNodeWithText("original message").assertIsDisplayed()
                    assertEquals("保留草稿", vm.state.value.draft)
                }
            } finally { compose.runOnIdle { store.clear() } }
        }
    }

    @Test fun frostedHeadersKeepControlsFixedWhileListsScroll() {
        val app = isolatedApplication()
        FakeGateway().use { gateway ->
            gateway.liveStream = true
            PreferencesManager(app).apply { gatewayBaseUrl = gateway.url; deviceToken = "test-token" }
            val vm = ChatViewModel(app)
            val store = ViewModelStore().apply { put("frosted-header", vm) }
            val drawerOnly = mutableStateOf(false)
            val chats = (1..35).map { com.antigravity.mobile.data.model.ConversationItem("$it", "历史会话 $it") }
            try {
                compose.setContent {
                    if (drawerOnly.value) DemoDrawer(Modifier.fillMaxSize(), chats, emptyList(), false, false, {}, {}, {}, {}, {})
                    else ChatDemoScreen(vm, {})
                }
                compose.runOnIdle { vm.openConversation("A") }
                compose.waitUntil(10_000) { !vm.state.value.isLoadingMessages && vm.state.value.connectionStatus ==
                    com.antigravity.mobile.data.service.ConnectionStatus.CONNECTED }
                val messages = org.json.JSONArray((1..30).map { JSONObject().put("id", "message-$it").put("type", if (it % 2 == 1) "user" else "agent")
                    .put("text", "## 阅读段落 $it\n\n这是顶部渐变的滚动验收内容。文字应在进入顶部时柔和模糊，按钮保持清晰。") })
                gateway.emit("A", JSONObject().put("type", "init").put("cascadeId", "A").put("status", "IDLE")
                    .put("isFullSnapshot", true).put("messages", messages).toString())
                compose.waitUntil(10_000) { vm.state.value.messages.size == 30 }
                val header = compose.onNodeWithContentDescription("打开菜单").fetchSemanticsNode().boundsInRoot
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(6)
                assertEquals(header, compose.onNodeWithContentDescription("打开菜单").fetchSemanticsNode().boundsInRoot)
                saveScreenshot("frosted-conversation.png")
                compose.onNodeWithContentDescription("更多选项").performClick()
                compose.onNodeWithText("置顶").assertIsDisplayed()
                compose.onNodeWithText("置顶").performClick()
                compose.runOnIdle { drawerOnly.value = true }
                val title = compose.onNodeWithText("Multigravity").fetchSemanticsNode().boundsInRoot
                compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
                assertEquals(title, compose.onNodeWithText("Multigravity").fetchSemanticsNode().boundsInRoot)
                saveScreenshot("frosted-drawer.png")
                compose.onNodeWithContentDescription("搜索").performClick()
                compose.onNode(hasSetTextAction()).performTextInput("历史会话 2")
                compose.onNodeWithContentDescription("清除搜索").assertIsDisplayed().performClick()
                assertEquals(title, compose.onNodeWithText("Multigravity").fetchSemanticsNode().boundsInRoot)
                compose.runOnIdle { drawerOnly.value = false; vm.newConversation() }
                fun shadowDarkness(): Int {
                    compose.waitForIdle()
                    Thread.sleep(200) // Wait for the native renderer to present the held gesture.
                    val send = compose.onNodeWithContentDescription("发送消息").fetchSemanticsNode().boundsInWindow
                    val field = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInWindow
                    val density = compose.activity.resources.displayMetrics.density
                    val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                    val darkness = (1..5).maxOf { gap -> 255 - android.graphics.Color.red(bitmap.getPixel(
                        field.center.x.toInt(), (send.center.y + (29 + gap) * density).toInt())) }
                    bitmap.recycle()
                    return darkness
                }
                val restingShadow = shadowDarkness()
                assertTrue("Resting composer must have a shadow", restingShadow > 4)
                saveScreenshot("composer-home-resting.png")
                val restingWidth = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.width
                compose.mainClock.autoAdvance = false
                try {
                    compose.onRoot().performTouchInput {
                        down(androidx.compose.ui.geometry.Offset(width * .8f, height * .35f))
                        moveTo(androidx.compose.ui.geometry.Offset(width * .65f, height * .35f), 150)
                    }
                    compose.mainClock.advanceTimeBy(96)
                    val earlyShadow = shadowDarkness()
                    assertTrue("Shadow must fade gradually at the start of the drag: $restingShadow -> $earlyShadow",
                        earlyShadow in 2 until restingShadow)
                    saveScreenshot("composer-home-shadow-early.png")
                    compose.onRoot().performTouchInput {
                        moveTo(androidx.compose.ui.geometry.Offset(width * .5f, height * .35f), 150)
                    }
                    compose.mainClock.advanceTimeBy(96)
                    val laterShadow = shadowDarkness()
                    assertTrue("Shadow must keep fading with the composer: $earlyShadow -> $laterShadow",
                        laterShadow in 1 until earlyShadow)
                    assertTrue("Composer must shrink during the actual home drag",
                        compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.width < restingWidth * .9f)
                    saveScreenshot("composer-home-morphing.png")
                    compose.onRoot().performTouchInput {
                        moveTo(androidx.compose.ui.geometry.Offset(width * .7f, height * .35f), 150)
                    }
                    compose.mainClock.advanceTimeBy(96)
                    val returningShadow = shadowDarkness()
                    assertTrue("Shadow must recover progressively during the reverse drag: $laterShadow -> $returningShadow",
                        returningShadow in (laterShadow + 1) until restingShadow)
                    saveScreenshot("composer-home-shadow-returning.png")
                    compose.onRoot().performTouchInput { up() }
                    compose.mainClock.advanceTimeBy(1_000)
                } finally { compose.mainClock.autoAdvance = true }
                compose.onNode(hasSetTextAction()).assertIsDisplayed()
                assertTrue("Shadow must return to its resting appearance", kotlin.math.abs(shadowDarkness() - restingShadow) <= 2)
                saveScreenshot("composer-home-restored.png")
                compose.onNode(hasText("项目") and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected)).performClick()
                compose.onNode(hasSetTextAction()).assertDoesNotExist()
                compose.mainClock.autoAdvance = false
                try {
                    compose.onRoot().performTouchInput {
                        down(androidx.compose.ui.geometry.Offset(width * .15f, height * .35f))
                        moveTo(androidx.compose.ui.geometry.Offset(width * .6f, height * .35f), 300)
                    }
                    compose.mainClock.advanceTimeBy(96)
                    val reappearingShadow = shadowDarkness()
                    assertTrue("Remounted composer must start with a partial shadow", reappearingShadow in 1 until restingShadow)
                    saveScreenshot("composer-home-shadow-reappearing.png")
                    compose.onRoot().performTouchInput {
                        moveTo(androidx.compose.ui.geometry.Offset(width * .8f, height * .35f), 300)
                    }
                    compose.mainClock.advanceTimeBy(96)
                    val growingShadow = shadowDarkness()
                    assertTrue("Shadow must grow with the returning composer: $reappearingShadow -> $growingShadow",
                        growingShadow in (reappearingShadow + 1) until restingShadow)
                    compose.onRoot().performTouchInput { up() }
                    compose.mainClock.advanceTimeBy(1_000)
                } finally { compose.mainClock.autoAdvance = true }
                assertTrue("Completed return must restore the resting shadow", kotlin.math.abs(shadowDarkness() - restingShadow) <= 2)
            } finally { compose.runOnIdle { store.clear() } }
        }
    }

    @Test fun conversationPullRetriesWithoutStatusTextAndActionsStayOutsideBubble() {
        val app = isolatedApplication()
        FakeGateway().use { gateway ->
            gateway.management = true
            gateway.failurePath = "/gateway/cascade/messages"
            val initialRead = CountDownLatch(1)
            val finishRead = CountDownLatch(1)
            gateway.block = { path -> if (path.startsWith("/gateway/cascade/messages")) {
                initialRead.countDown(); finishRead.await(5, TimeUnit.SECONDS)
            } }
            PreferencesManager(app).apply { gatewayBaseUrl = gateway.url; deviceToken = "test-token" }
            val vm = ChatViewModel(app)
            val store = ViewModelStore().apply { put("pull-retry", vm) }
            try {
                compose.setContent { ChatDemoScreen(vm, {}) }
                compose.runOnIdle { vm.openConversation("A"); vm.setDraft("preserved draft") }
                assertTrue(initialRead.await(5, TimeUnit.SECONDS))
                assertTrue(vm.state.value.isLoadingMessages)
                compose.onNodeWithText("original message").assertDoesNotExist()
                gateway.block = {}; finishRead.countDown()
                compose.waitUntil(10_000) { vm.state.value.messagesError != null && !vm.state.value.isLoadingMessages }
                listOf("正在连接，生成状态待同步…", "连接已断开，正在自动重连…", "重试", "重试中…").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
                gateway.failurePath = null
                val height = compose.onRoot().fetchSemanticsNode().size.height.toFloat()
                compose.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(width / 2f, height * .25f),
                    androidx.compose.ui.geometry.Offset(width / 2f, height * .70f), 700) }
                compose.waitUntil(10_000) { vm.state.value.messagesError == null && !vm.state.value.isLoadingMessages }
                compose.onNodeWithText("original message").assertIsDisplayed()
                assertEquals("preserved draft", vm.state.value.draft)
                val text = compose.onNodeWithText("original message").fetchSemanticsNode().boundsInRoot
                val copy = compose.onNodeWithContentDescription("复制用户消息").fetchSemanticsNode().boundsInRoot
                val undo = compose.onNodeWithContentDescription("回退到这条消息").fetchSemanticsNode().boundsInRoot
                assertTrue(copy.top > text.bottom); assertTrue(undo.top > text.bottom)
                saveScreenshot("ui-fix-message.png")
                compose.onNodeWithContentDescription("回退到这条消息").performClick()
                compose.waitUntil(10_000) { vm.state.value.revertPreview != null }
                compose.onNodeWithText("确认回退吗？").assertIsDisplayed()
                compose.onNodeWithText("回退文件改动").assertIsDisplayed()
                compose.onNode(isToggleable()).assertIsOn()
                compose.onNodeWithText("同时回退工作区文件").assertDoesNotExist()
                saveScreenshot("ui-refine-revert.png")
                compose.onNodeWithText("取消").performClick()
                assertFalse(gateway.requests.any { it.first.contains("/revert/execute") })
                assertEquals("preserved draft", vm.state.value.draft)
                compose.onNodeWithContentDescription("回退到这条消息").performClick()
                compose.waitUntil(10_000) { vm.state.value.revertPreview != null }
                compose.onNode(isToggleable()).performClick()
                compose.onNodeWithText("确认回退").performClick()
                compose.waitUntil(10_000) { gateway.reverted && !vm.state.value.isReverting }
                assertTrue(JSONObject(gateway.requests.last { it.first.contains("/revert/execute") }.second).getBoolean("conversationOnly"))
                assertEquals("original message\n\npreserved draft", vm.state.value.draft)
                // A legacy gateway gets an actionable error, never a disabled decorative Undo.
                compose.runOnIdle { vm.previewRevert(GatewayMessageItem(id = "step-0", stepIndex = 0, text = "legacy")) }
                compose.waitForIdle()
                assertTrue(vm.state.value.revertError.orEmpty().contains("网关版本较旧"))
                assertNull(vm.state.value.revertPreview)
            } finally { compose.runOnIdle { store.clear() } }
        }
    }

    @Test fun authorizationAndExternalImageFailuresDoNotChangeGatewayRoutes() {
        val app = isolatedApplication()
        FakeGateway().use { gateway -> FakeGateway().use { cloud ->
            val prefs = PreferencesManager(app).apply {
                gatewayBaseUrl = gateway.url; deviceToken = "test-token"
                primaryCloudUrl = cloud.url.replace("127.0.0.1", "localhost")
            }
            val api = com.antigravity.mobile.data.service.ApiClient(app, prefs)
            gateway.failurePath = "GetAllCascadeTrajectories"; gateway.failureCode = 401
            val failure = runBlocking { api.fetchConversations() }.exceptionOrNull()
            assertTrue(failure is com.antigravity.mobile.data.service.GatewayAuthorizationException)
            assertEquals(gateway.url, prefs.gatewayBaseUrl)
            assertTrue(runBlocking { api.imageForDraft("http://127.0.0.1:1/image.png", "A") }.isFailure)
            assertTrue("Unrelated requests reached the cloud gateway", cloud.requests.isEmpty())
            assertEquals(gateway.url, prefs.gatewayBaseUrl)
        } }
    }

    @Test fun pairingAgainDoesNotLeavePreviousListRequestsBusy() {
        val app = isolatedApplication()
        FakeGateway().use { gateway ->
            gateway.management = true
            val requested = CountDownLatch(1); val release = CountDownLatch(1)
            val first = java.util.concurrent.atomic.AtomicBoolean(true)
            gateway.block = { path -> if (path.contains("GetAllCascadeTrajectories") && first.getAndSet(false)) {
                requested.countDown(); release.await(10, TimeUnit.SECONDS)
            } }
            PreferencesManager(app).apply { gatewayBaseUrl = gateway.url; deviceToken = "old-token" }
            val vm = ChatViewModel(app)
            val store = ViewModelStore().apply { put("pairing", vm) }
            try {
                assertTrue(requested.await(10, TimeUnit.SECONDS))
                compose.runOnIdle { vm.pair("a".repeat(64), gateway.url) }
                compose.waitUntil(10_000) { vm.state.value.pairSuccessCount == 1 && !vm.state.value.isLoadingConversations && vm.state.value.conversations.size == 2 }
                assertTrue(vm.state.value.isPaired)
                assertEquals("new-token", PreferencesManager(app).deviceToken)
                assertEquals(2, gateway.requests.count { it.first.contains("GetAllCascadeTrajectories") })
            } finally { release.countDown(); compose.runOnIdle { store.clear() } }
        }
    }

    @Test fun composerActionsHaveAccessibleTargetsAndBusyStates() {
        val sending = mutableStateOf(false)
        val images = mutableStateOf(listOf(com.antigravity.mobile.ui.chat.PendingImage(android.net.Uri.parse("test://image"), byteArrayOf(), "image/png")))
        var submitted = 0
        compose.setContent { Composer(activeConversation = true, draft = "验收文字", attachments = images.value,
            onDraftChange = {}, onAddImage = {}, onRemoveImage = { images.value = emptyList() },
            onSend = { submitted++; sending.value = true }, isSending = sending.value, onUnsupported = {}) }
        for (label in listOf("添加图片", "移除待发送图片", "语音输入，暂不可用", "发送消息")) {
            compose.onNodeWithContentDescription(label).assertWidthIsAtLeast(androidx.compose.ui.unit.Dp(48f))
                .assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
        }
        if (InstrumentationRegistry.getArguments().getString("round2TalkBack") == "true") {
            org.junit.Assume.assumeTrue(android.os.Build.HARDWARE == "ranchu")
            val automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            val manager = compose.activity.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            compose.waitUntil(10_000) { manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.id.contains("talkback", true) } }
            fun find(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.contentDescription?.toString() == "添加图片") return node
                for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
                return null
            }
            val add = find(automation.rootInActiveWindow)
            assertNotNull("TalkBack cannot find the image action", add)
            assertTrue(add!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
        }
        saveScreenshot("round2-accessibility.png")
        compose.onNodeWithContentDescription("移除待发送图片").performClick()
        compose.onNodeWithContentDescription("移除待发送图片").assertDoesNotExist()
        compose.onNodeWithContentDescription("发送消息").performClick()
        compose.onNodeWithContentDescription("发送消息").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "正在发送"))
        assertEquals(1, submitted)
    }

    private class FakeGateway : AutoCloseable {
        private val server = ServerSocket(0)
        val url = "http://127.0.0.1:${server.localPort}"
        val requests = CopyOnWriteArrayList<Pair<String, String>>()
        @Volatile var block: (String) -> Unit = {}
        @Volatile var failurePath: String? = null
        @Volatile var failureCode = 500
        @Volatile var liveStream = false
        @Volatile var messageText = "original message"
        private val streams = CopyOnWriteArrayList<Pair<java.net.Socket, String>>()
        fun dropStreams() { streams.forEach { it.first.close() }; streams.clear() }
        fun emit(id: String, text: String) {
            val bytes = text.toByteArray()
            streams.filter { it.second == id && !it.first.isClosed }.forEach { (socket, _) ->
                val output = java.io.DataOutputStream(socket.getOutputStream())
                output.writeByte(0x81)
                if (bytes.size < 126) output.writeByte(bytes.size) else { output.writeByte(126); output.writeShort(bytes.size) }
                output.write(bytes); output.flush()
            }
        }
        @Volatile var management = false
        @Volatile var projectFixture = false
        @Volatile var reverted = false
        @Volatile var userImage = false
        @Volatile var running = false
        @Volatile var previewFiles = 0
        private fun image(width: Int, height: Int) = java.io.ByteArrayOutputStream().let { output ->
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle(); output.toByteArray()
        }
        val imageBytes = image(200, 400)
        private val wideImage = image(800, 200)
        private val longImage = image(100, 20_000)
        private val annotations = java.util.concurrent.ConcurrentHashMap<String, JSONObject>()
        private val stopped = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val socket = try { server.accept() } catch (_: Exception) { break }
                    thread(isDaemon = true) {
                        try { socket.use {
                            val reader = it.getInputStream().bufferedReader()
                            val path = reader.readLine()?.split(' ')?.getOrNull(1) ?: return@use
                            var length = 0
                            var webSocketKey = ""
                            while (true) {
                                val header = reader.readLine() ?: return@use
                                if (header.isEmpty()) break
                                if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                                if (header.startsWith("Sec-WebSocket-Key:", true)) webSocketKey = header.substringAfter(':').trim()
                            }
                            val chars = CharArray(length)
                            var read = 0
                            while (read < length) { val n = reader.read(chars, read, length - read); if (n < 0) break; read += n }
                            requests += path to String(chars)
                            block(path)
                            if (liveStream && path.startsWith("/gateway/cascade/stream") && failurePath?.let(path::contains) != true) {
                                val accept = android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-1")
                                    .digest((webSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()), android.util.Base64.NO_WRAP)
                                it.getOutputStream().write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray())
                                streams += it to if (path.contains("cascadeId=A")) "A" else "B"
                                try { while (it.getInputStream().read() >= 0) {} } catch (_: java.io.IOException) {}
                                return@use
                            }
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
                                path.startsWith("/api/v1/auth/pair") -> """{"device_id":"test-device","device_token":"new-token"}"""
                                path.startsWith("/gateway/projects") -> if (projectFixture) """[{"id":"p","name":"动效项目","uri":"file:///test/project"}]""" else "[]"
                                management && path.contains("/revert/preview") -> """{"cascadeId":"A","stepIndex":0,"targetStepIndex":-1,"hasCodeChanges":${previewFiles > 0},"files":[${(0 until previewFiles).joinToString { """{"fileName":"${if (it == 0) "Agent.md" else "file-$it.kt"}","actionType":"MODIFY","additions":1,"deletions":2}""" }}]}"""
                                management && path.startsWith("/gateway/cascade/messages") ->
                                    """{"status":"${if (running && !reverted) "RUNNING" else "IDLE"}","messages":${if (reverted) "[]" else "[{\"id\":\"step-0\",\"type\":\"user\",\"text\":\"$messageText\",\"stepIndex\":0,\"canRevert\":true${if (userImage) ",\"imageUrls\":[\"$url/image.png\"]" else ""}}]"},"cascadeId":"$id"}"""
                                management && path.contains("GetAllCascadeTrajectories") -> {
                                    val summaries = JSONObject()
                                    for (key in listOf("A", "B")) summaries.put(key, JSONObject().put("summary", key)
                                        .put("status", "IDLE").put("annotations", annotations[key] ?: JSONObject()).apply {
                                            if (projectFixture && key == "B") put("trajectoryMetadata", JSONObject().put("projectId", "p"))
                                        })
                                    JSONObject().put("trajectorySummaries", summaries).toString()
                                }
                                path.startsWith("/gateway/cascade/messages") -> """{"status":"${if (id in stopped) "IDLE" else "RUNNING"}","messages":[],"cascadeId":"$id"}"""
                                path.contains("GetAllCascadeTrajectories") -> """{"trajectorySummaries":{}}"""
                                else -> "{}"
                            }.toByteArray().let { when {
                                path.startsWith("/image.png") -> imageBytes
                                path.startsWith("/wide.png") -> wideImage
                                path.startsWith("/long.png") -> longImage
                                else -> it
                            } }
                            val status = when {
                                failurePath?.let(path::contains) == true -> "$failureCode Failure"
                                path.startsWith("/gateway/cascade/stream") -> "503 Unavailable"
                                else -> "200 OK"
                            }
                            val output = it.getOutputStream()
                            output.write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            output.write(body)
                        } } catch (_: java.io.IOException) { /* The client may cancel an in-flight test request. */ }
                    }
                }
            }
        }
        override fun close() { dropStreams(); server.close() }
    }
}
