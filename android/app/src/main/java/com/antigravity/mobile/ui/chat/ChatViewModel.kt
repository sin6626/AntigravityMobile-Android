package com.antigravity.mobile.ui.chat

import android.app.Application
import android.net.Uri
import android.webkit.MimeTypeMap
import coil.ImageLoader
import coil.request.ImageRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.mobile.data.model.ConversationItem
import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.data.model.FileContentResponse
import com.antigravity.mobile.data.model.PairingInfo
import com.antigravity.mobile.data.model.PendingInteraction
import com.antigravity.mobile.data.model.InteractionOption
import com.antigravity.mobile.data.model.RevertPreviewResponse
import com.antigravity.mobile.data.model.ProjectItem
import com.antigravity.mobile.data.model.ChatModel
import com.antigravity.mobile.data.service.ApiClient
import com.antigravity.mobile.data.service.GatewayAuthorizationException
import com.antigravity.mobile.data.service.ConnectionManager
import com.antigravity.mobile.data.service.PreferencesManager
import com.antigravity.mobile.data.service.StreamWebSocketClient
import com.antigravity.mobile.data.service.ConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.io.File
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ChatUiState(
    val isPaired: Boolean = false,
    val isPairing: Boolean = false,
    val pairSuccessCount: Int = 0,
    val isLoadingConversations: Boolean = false,
    val isLoadingProjects: Boolean = false,
    val isLoadingMessages: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasMoreMessages: Boolean = false,
    val nextMessageOffset: Int = 0,
    val isSending: Boolean = false,
    val isRunning: Boolean = false,
    val isStopping: Boolean = false,
    val isRenaming: Boolean = false,
    val isDeleting: Boolean = false,
    val busyConversations: Set<String> = emptySet(),
    val revertMessage: GatewayMessageItem? = null,
    val revertPreview: RevertPreviewResponse? = null,
    val isLoadingRevert: Boolean = false,
    val isReverting: Boolean = false,
    val revertError: String? = null,
    val streamingMessageId: String? = null,
    val pendingInteraction: PendingInteraction? = null,
    val isSubmittingInteraction: Boolean = false,
    val conversations: List<ConversationItem> = emptyList(),
    val projects: List<ProjectItem> = emptyList(),
    val models: List<ChatModel> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsError: String? = null,
    val selectedModelId: String? = null,
    val activeModelId: String? = null,
    val modelOverrideId: String? = null,
    val creatingProjectKey: String? = null,
    val selectedConversationId: String? = null,
    val messages: List<GatewayMessageItem> = emptyList(),
    val outgoing: List<OutgoingMessage> = emptyList(),
    val draft: String = "",
    val attachments: List<PendingImage> = emptyList(),
    val isRestoringDraft: Boolean = false,
    val error: String? = null,
    val messagesError: String? = null,
    val olderMessagesError: String? = null,
    val conversationsError: String? = null,
    val projectsError: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
)

data class PendingImage(val uri: Uri, val bytes: ByteArray, val mimeType: String, val sourceUri: Uri = uri)

data class OutgoingMessage(
    val conversationId: String,
    val message: GatewayMessageItem,
    val knownUserIds: Set<String>,
    val afterStepIndex: Int? = null,
)

// The gateway does not echo X-Client-Message-Id in history; only match newly observed user steps.
internal fun reconcileOutgoing(
    outgoing: List<OutgoingMessage>,
    conversationId: String,
    incoming: List<GatewayMessageItem>,
): List<OutgoingMessage> {
    val candidates = incoming.filter { it.isUser }.toMutableList()
    val confirmedIds = mutableSetOf<String>()
    val remaining = outgoing.mapNotNull { pending ->
        if (pending.conversationId != conversationId) pending else {
            val index = candidates.indexOfFirst {
                it.id !in pending.knownUserIds && it.effectiveText == pending.message.effectiveText &&
                    (pending.afterStepIndex == null || (it.stepIndex ?: -1) > pending.afterStepIndex) &&
                    (pending.message.imageDataList.isEmpty() ||
                        !it.media.isNullOrEmpty() || !it.imageUrls.isNullOrEmpty() || it.imageDataList.isNotEmpty())
            }
            if (index >= 0) confirmedIds += candidates.removeAt(index).id
            if (index >= 0) null else pending
        }
    }
    return remaining.map {
        if (it.conversationId == conversationId) it.copy(knownUserIds = it.knownUserIds + confirmedIds) else it
    }
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager(application)
    private val connectionManager = ConnectionManager(application)
    private val api = ApiClient(application, prefs, connectionManager)
    private val stream = StreamWebSocketClient(prefs, connectionManager)

    private var draftKey = NEW_CHAT_DRAFT
    private val draftAttachments = mutableMapOf<String, List<PendingImage>>()
    private var conversationVisit = 0L
    private var revertPreviewVisit = 0L
    private var messagesRevision = 0L
    private var fullSnapshotRevision = 0L
    private var messagesJob: Job? = null
    private var draftRestoreJob: Job? = null
    private var recoveryJob: Job? = null
    private val _state = MutableStateFlow(ChatUiState(isPaired = prefs.isPaired(), draft = prefs.getDraftText(draftKey)))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    val mediaImageLoader: ImageLoader get() = api.mediaImageLoader
    fun mediaImageRequest(raw: String): ImageRequest = api.mediaImageRequest(raw)
    fun linkedImageRequest(raw: String): ImageRequest = api.mediaImageRequest(raw, _state.value.selectedConversationId)
    suspend fun fetchLinkedFile(uri: String): Result<FileContentResponse> =
        api.fetchFileContent(uri.substringBefore('#'), _state.value.selectedConversationId)
    val gatewayUrl: String? get() = prefs.gatewayBaseUrl

    init {
        // A request interrupted by process death becomes a draft, never an automatic resend.
        prefs.pendingSendTarget?.let { target ->
            prefs.setDraftText(target, listOf(prefs.getDraftText(PENDING_SEND_DRAFT), prefs.getDraftText(target))
                .filter { it.isNotEmpty() }.distinct().joinToString("\n\n"))
            prefs.saveDraftImages(target, (prefs.loadDraftImages(PENDING_SEND_DRAFT) + prefs.loadDraftImages(target)).distinct().take(4))
            clearSendSnapshot()
        }
        _state.value = _state.value.copy(draft = prefs.getDraftText(NEW_CHAT_DRAFT))
        restoreDraftImages(NEW_CHAT_DRAFT)
        if (prefs.isPaired()) {
            connectionManager.startMonitoring(prefs, viewModelScope)
            prefs.lastConversationId?.takeUnless { it.startsWith("local:") || prefs.isDeletedConversation(it) }?.let(::openConversation)
            refreshConversations()
            refreshProjects()
            refreshModels()
        }
        viewModelScope.launch {
            stream.connectionStatus.collect { status ->
                _state.value = _state.value.copy(connectionStatus = status)
                if (status == ConnectionStatus.UNAUTHORIZED) expirePairing()
                else if (status == ConnectionStatus.CONNECTED) _state.value.selectedConversationId?.let { loadMessages(it) }
            }
        }
        viewModelScope.launch {
            stream.streamUpdates.collect { update ->
                if (update == null || update.cascadeId != _state.value.selectedConversationId) return@collect
                if (update.type == "error") {
                    _state.value = _state.value.copy(messagesError = update.errorMessage ?: "同步消息失败", isLoadingMessages = false)
                    return@collect
                }
                messagesRevision++
                if (update.isFullSnapshot) fullSnapshotRevision = messagesRevision
                val previous = _state.value
                val nextMessages = update.messages?.let { incoming ->
                    if (update.isFullSnapshot) incoming else mergeMessages(previous.messages, incoming)
                } ?: previous.messages
                val running = update.status.contains("RUNNING", ignoreCase = true)
                val latestAgent = nextMessages.lastOrNull { it.type == "agent" }
                val previousText = previous.messages.firstOrNull { it.id == latestAgent?.id }?.effectiveText.orEmpty()
                val growing = update.type != "init" && !previous.isLoadingMessages && latestAgent != null &&
                    latestAgent.effectiveText.isNotEmpty() &&
                    latestAgent.effectiveText != previousText && latestAgent.effectiveText.startsWith(previousText)
                val outgoing = reconcileOutgoing(previous.outgoing, update.cascadeId, nextMessages)
                _state.value = _state.value.copy(
                    messages = nextMessages,
                    outgoing = outgoing,
                    isRunning = running || outgoing.any { it.conversationId == update.cascadeId },
                    streamingMessageId = if (running && (growing || previous.streamingMessageId == latestAgent?.id)) latestAgent?.id else null,
                    pendingInteraction = update.pendingInteraction,
                    activeModelId = update.activeModel ?: previous.activeModelId,
                    isLoadingMessages = false, messagesError = null,
                    hasMoreMessages = if (previous.isLoadingMessages || update.isFullSnapshot) update.hasMore else previous.hasMoreMessages,
                    nextMessageOffset = if (previous.isLoadingMessages || update.isFullSnapshot) update.nextOffset else previous.nextMessageOffset,
                )
            }
        }
    }

    fun setDraft(value: String) {
        prefs.setDraftText(draftKey, value)
        _state.value = _state.value.copy(draft = value)
    }

    private fun switchDraft(key: String) {
        prefs.setDraftText(draftKey, _state.value.draft)
        if (!_state.value.isRestoringDraft) draftAttachments[draftKey] = _state.value.attachments
        draftRestoreJob?.cancel()
        draftKey = key
        _state.value = _state.value.copy(draft = prefs.getDraftText(key), attachments = draftAttachments[key].orEmpty())
        restoreDraftImages(key)
    }

    private fun restoreDraftImages(key: String) {
        _state.value = _state.value.copy(isRestoringDraft = key !in draftAttachments && prefs.draftImageCount(key) > 0)
        if (_state.value.isRestoringDraft) draftRestoreJob = viewModelScope.launch {
            val images = withContext(Dispatchers.IO) { prefs.loadDraftImages(key).mapNotNull { file ->
                try { PendingImage(Uri.fromFile(file), file.readBytes(),
                    checkNotNull(MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension))) }
                catch (_: Exception) { null }
            } }
            if (draftKey != key) return@launch
            draftAttachments[key] = images
            _state.value = _state.value.copy(attachments = images, isRestoringDraft = false,
                error = if (images.size < prefs.draftImageCount(key)) "部分图片草稿无法读取，请重新选择图片" else _state.value.error)
        }
    }

    private fun saveImages(key: String, images: List<PendingImage>) {
        draftAttachments[key] = images
        if (images.isEmpty()) prefs.clearDraftImages(key)
        else prefs.saveDraftImages(key, images.map { File(checkNotNull(it.uri.path)) })
    }

    private fun clearSendSnapshot() {
        prefs.pendingSendTarget = null
        prefs.clearDraftText(PENDING_SEND_DRAFT)
        prefs.clearDraftImages(PENDING_SEND_DRAFT)
    }

    private fun moveDraft(from: String, to: String) {
        val active = draftKey == from
        val sourceText = if (active) _state.value.draft else prefs.getDraftText(from)
        val targetText = if (draftKey == to) _state.value.draft else prefs.getDraftText(to)
        val text = listOf(sourceText, targetText).filter { it.isNotEmpty() }.joinToString("\n\n")
        val sourceImages = if (active) _state.value.attachments else draftAttachments[from].orEmpty()
        val targetImages = if (draftKey == to) _state.value.attachments else draftAttachments[to].orEmpty()
        val images = (sourceImages + targetImages).distinctBy { it.uri }
        prefs.setDraftText(to, text)
        saveImages(to, images)
        prefs.clearDraftText(from)
        prefs.clearDraftImages(from)
        draftAttachments.remove(from)
        if (active) draftKey = to
        if (draftKey == to) _state.value = _state.value.copy(draft = text, attachments = images)
    }

    fun addImages(uris: List<Uri>) {
        val key = draftKey
        viewModelScope.launch {
            draftRestoreJob?.join()
            for (uri in uris.take(4)) {
                if (draftKey != key) break
                if (_state.value.attachments.size + prefs.loadDraftImages(PENDING_SEND_DRAFT).size >= 4) break
                if (_state.value.attachments.any { it.sourceUri == uri }) continue
                try {
                    val image = withContext(Dispatchers.IO) { readImage(uri) }
                    if (draftKey != key) { prefs.deleteUnusedDraftImage(File(checkNotNull(image.uri.path))); break }
                    if (_state.value.attachments.size + prefs.loadDraftImages(PENDING_SEND_DRAFT).size >= 4 ||
                        _state.value.attachments.any { it.sourceUri == uri }) {
                        prefs.deleteUnusedDraftImage(File(checkNotNull(image.uri.path))); continue
                    }
                    _state.value = _state.value.copy(attachments = _state.value.attachments + image)
                    saveImages(key, _state.value.attachments)
                } catch (error: Exception) {
                    if (draftKey != key) break
                    _state.value = _state.value.copy(error = error.message ?: "读取图片失败")
                }
            }
        }
    }

    fun removeImage(uri: Uri) {
        _state.value = _state.value.copy(attachments = _state.value.attachments.filterNot { it.uri == uri })
        saveImages(draftKey, _state.value.attachments)
        uri.path?.let { prefs.deleteUnusedDraftImage(File(it)) }
    }

    private fun readImage(uri: Uri): PendingImage {
        val resolver = getApplication<Application>().contentResolver
        val mime = resolver.getType(uri) ?: throw IllegalArgumentException("无法识别图片格式")
        if (mime !in setOf("image/jpeg", "image/png", "image/webp", "image/gif")) {
            throw IllegalArgumentException("暂不支持 $mime，请选择 JPG、PNG、WebP 或 GIF 图片")
        }
        val input = resolver.openInputStream(uri) ?: throw IllegalArgumentException("无法读取所选图片")
        val output = ByteArrayOutputStream()
        input.use { source ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                if (output.size() + count > 8 * 1024 * 1024) {
                    throw IllegalArgumentException("单张图片不能超过 8 MB")
                }
                output.write(buffer, 0, count)
            }
        }
        val bytes = output.toByteArray()
        return PendingImage(Uri.fromFile(prefs.storeDraftImage(bytes, mime)), bytes, mime, uri)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun expirePairing() {
        if (!_state.value.isPaired) return
        conversationVisit++
        messagesJob?.cancel(); messagesJob = null
        prefs.deviceToken = null
        prefs.deviceId = null
        stream.disconnect()
        connectionManager.stopMonitoring()
        prefs.lastConversationId = null
        _state.value = _state.value.copy(
            isPaired = false, isLoadingConversations = false, isLoadingProjects = false,
            models = emptyList(), isLoadingModels = false, modelsError = null,
            activeModelId = null, modelOverrideId = null, creatingProjectKey = null,
            selectedConversationId = null, conversations = emptyList(), projects = emptyList(),
            messages = emptyList(), outgoing = emptyList(), error = "设备授权已失效，请重新配对",
            isLoadingMessages = false,
        )
    }

    fun pair(uri: String, customUrl: String = "") {
        if (_state.value.isPairing) return
        val input = uri.trim()
        val override = customUrl.trim().trimEnd('/').takeIf { it.isNotBlank() }
        val parsed = override?.toHttpUrlOrNull()
        if (override != null) {
            if (parsed == null || parsed.encodedPath != "/" || parsed.query != null ||
                parsed.fragment != null || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()
            ) {
                _state.value = _state.value.copy(error = "自定义地址须为完整的 http(s)://主机:端口，不含路径或账号")
                return
            }
            if (parsed.scheme == "http" && !ConnectionManager.isLanHost(parsed.host) &&
                !ConnectionManager.isTailscaleHost(parsed.host)
            ) {
                _state.value = _state.value.copy(error = "公网地址请使用 HTTPS；局域网和 Tailscale 地址可使用 HTTP")
                return
            }
        }
        val info = if (input.startsWith("agy://", ignoreCase = true) ||
            input.startsWith("multigravity://", ignoreCase = true)
        ) {
            try { PairingInfo.parseFromUri(input) } catch (_: Exception) { null }
        } else if (input.isNotBlank() && parsed != null) {
            PairingInfo(host = parsed.host, port = parsed.port, code = input, ssl = parsed.isHttps)
        } else null
        if (info == null) {
            _state.value = _state.value.copy(error = if (input.isBlank())
                "还需填写 mgy pair 生成的配对码或扫描配对二维码（网关地址不能代替配对码）"
            else "请粘贴完整配对链接；单独填写配对码时还需填写网关 URL")
            return
        }
        _state.value = _state.value.copy(isPairing = true, error = null)
        viewModelScope.launch {
            api.pair(info, override).fold(
                onSuccess = {
                    closeConversation()
                    _state.value = _state.value.copy(
                        isPaired = true, isPairing = false,
                        pairSuccessCount = _state.value.pairSuccessCount + 1,
                        conversations = emptyList(), projects = emptyList(), error = null,
                        isLoadingConversations = false, isLoadingProjects = false,
                        conversationsError = null, projectsError = null,
                        models = emptyList(), isLoadingModels = false, modelsError = null, creatingProjectKey = null,
                    )
                    connectionManager.startMonitoring(prefs, viewModelScope)
                    refreshConversations()
                    refreshProjects()
                    refreshModels()
                },
                onFailure = {
                    _state.value = _state.value.copy(isPairing = false, error = it.message ?: "配对失败")
                },
            )
        }
    }

    fun refreshConversations() {
        if (!_state.value.isPaired || _state.value.isLoadingConversations) return
        _state.value = _state.value.copy(isLoadingConversations = true)
        viewModelScope.launch {
            val token = prefs.deviceToken
            val result = api.fetchConversations()
            if (prefs.deviceToken != token) return@launch
            result.fold(
                onSuccess = { conversations ->
                    if (!_state.value.isPaired) return@fold
                    _state.value = _state.value.copy(
                        conversations = conversations,
                        isLoadingConversations = false,
                        conversationsError = null,
                    )
                },
                onFailure = { error ->
                    if (error is GatewayAuthorizationException) {
                        expirePairing()
                        return@fold
                    }
                    if (!_state.value.isPaired) return@fold
                    _state.value = _state.value.copy(
                        isLoadingConversations = false,
                        conversationsError = error.message ?: "获取会话列表失败",
                    )
                },
            )
        }
    }

    fun refreshProjects() {
        if (!_state.value.isPaired || _state.value.isLoadingProjects) return
        _state.value = _state.value.copy(isLoadingProjects = true)
        viewModelScope.launch {
            val token = prefs.deviceToken
            val result = api.fetchProjects()
            if (prefs.deviceToken != token) return@launch
            result.fold(
                onSuccess = { projects ->
                    if (!_state.value.isPaired) return@fold
                    _state.value = _state.value.copy(
                        projects = projects.filterNot { it.isPureChat },
                        isLoadingProjects = false,
                        projectsError = null,
                    )
                },
                onFailure = { error ->
                    if (error is GatewayAuthorizationException) {
                        expirePairing()
                        return@fold
                    }
                    if (!_state.value.isPaired) return@fold
                    _state.value = _state.value.copy(
                        isLoadingProjects = false,
                        projectsError = error.message ?: "获取项目失败",
                    )
                },
            )
        }
    }

    fun refreshModels() {
        if (!_state.value.isPaired || _state.value.isLoadingModels) return
        val token = prefs.deviceToken
        _state.value = _state.value.copy(isLoadingModels = true)
        viewModelScope.launch {
            api.fetchChatModels().fold(onSuccess = { models ->
                if (prefs.deviceToken != token) return@fold
                val selected = models.firstOrNull { it.id == prefs.selectedModelId }
                    ?: models.firstOrNull { it.id == DEFAULT_CHAT_MODEL } ?: models.first()
                prefs.selectedModelId = selected.id
                _state.value = _state.value.copy(models = models, selectedModelId = selected.id,
                    isLoadingModels = false, modelsError = null)
            }, onFailure = { error ->
                if (prefs.deviceToken != token) return@fold
                if (error is GatewayAuthorizationException) { expirePairing(); return@fold }
                _state.value = _state.value.copy(isLoadingModels = false, modelsError = error.message ?: "读取模型失败")
            })
        }
    }

    fun selectModel(id: String) {
        val current = _state.value
        val model = current.models.firstOrNull { it.id == id } ?: return
        if (current.isSending || current.isReverting || current.creatingProjectKey != null) return
        if (current.attachments.isNotEmpty() && !model.supportsImages) {
            _state.value = current.copy(error = "这个模型不支持图片，请先移除图片")
            return
        }
        if (current.selectedConversationId == null) {
            prefs.selectedModelId = id
            _state.value = current.copy(selectedModelId = id)
        } else _state.value = current.copy(modelOverrideId = id)
    }

    fun createProjectConversation(project: ProjectItem, onCreated: () -> Unit = {}) {
        val current = _state.value
        if (current.creatingProjectKey != null || current.isSending || current.isReverting) return
        if (project.isPureChat || project !in current.projects || (project.rawId.isNullOrBlank() && project.uri.isBlank())) {
            _state.value = current.copy(error = "项目已不可用，请刷新项目列表")
            return
        }
        val model = current.models.firstOrNull { it.id == current.selectedModelId } ?: run {
            _state.value = current.copy(error = current.modelsError ?: "请先等待模型列表加载完成")
            refreshModels(); return
        }
        val visit = conversationVisit
        val token = prefs.deviceToken
        _state.value = current.copy(creatingProjectKey = project.id, error = null)
        viewModelScope.launch {
            api.createCascade(project.uri, "", model.model, project.rawId).fold(onSuccess = { id ->
                if (prefs.deviceToken != token) return@fold
                if (conversationVisit == visit) {
                    onCreated(); openConversation(id)
                    _state.value = _state.value.copy(modelOverrideId = model.id)
                }
                refreshConversations()
            }, onFailure = { error ->
                if (prefs.deviceToken != token) return@fold
                if (error is GatewayAuthorizationException) { expirePairing(); return@fold }
                if (conversationVisit == visit) _state.value = _state.value.copy(error = error.message ?: "创建项目会话失败")
            })
            if (prefs.deviceToken == token) _state.value = _state.value.copy(creatingProjectKey = null)
        }
    }

    fun openConversation(id: String) {
        conversationVisit++
        messagesJob?.cancel(); messagesJob = null
        switchDraft(id)
        prefs.lastConversationId = id.takeUnless { it.startsWith("local:") }
        stream.disconnect()
        _state.value = _state.value.copy(
            selectedConversationId = id,
            activeModelId = null, modelOverrideId = null,
            messages = emptyList(),
            isLoadingMessages = true,
            isLoadingOlder = false,
            isSubmittingInteraction = false,
            isStopping = false, isRenaming = false, isDeleting = false,
            revertMessage = null, revertPreview = null, revertError = null, isLoadingRevert = false, isReverting = false,
            streamingMessageId = null,
            isRunning = _state.value.outgoing.any { it.conversationId == id },
            pendingInteraction = null,
            hasMoreMessages = false,
            nextMessageOffset = 0,
            error = null,
            messagesError = null, olderMessagesError = null,
        )
        stream.connect(id)
        loadMessages(id)
        api.notifySessionFocus(id)
    }

    fun newConversation() {
        closeConversation()
        switchDraft(NEW_CHAT_DRAFT)
        prefs.clearDraftText(NEW_CHAT_DRAFT)
        prefs.clearDraftImages(NEW_CHAT_DRAFT)
        draftRestoreJob?.cancel()
        draftAttachments.remove(NEW_CHAT_DRAFT)
        _state.value = _state.value.copy(draft = "", attachments = emptyList(), isRestoringDraft = false)
    }

    fun closeConversation() {
        conversationVisit++
        messagesJob?.cancel(); messagesJob = null
        stream.disconnect()
        prefs.lastConversationId = null
        switchDraft(NEW_CHAT_DRAFT)
        _state.value = _state.value.copy(
            selectedConversationId = null,
            activeModelId = null, modelOverrideId = null,
            messages = emptyList(),
            streamingMessageId = null,
            isLoadingMessages = false,
            isLoadingOlder = false,
            isSubmittingInteraction = false,
            isStopping = false, isRenaming = false, isDeleting = false,
            revertMessage = null, revertPreview = null, revertError = null, isLoadingRevert = false, isReverting = false,
            isRunning = false,
            pendingInteraction = null,
            hasMoreMessages = false,
            nextMessageOffset = 0,
            error = null,
            messagesError = null, olderMessagesError = null,
        )
    }

    fun send() {
        if (_state.value.isRestoringDraft) return
        val text = _state.value.draft.trim()
        val images = _state.value.attachments
        if ((text.isEmpty() && images.isEmpty()) || _state.value.isSending || _state.value.isLoadingMessages || _state.value.isStopping || _state.value.isReverting || _state.value.selectedConversationId in _state.value.busyConversations) return
        val id = _state.value.selectedConversationId
        val modelId = _state.value.modelOverrideId ?: if (id == null) _state.value.selectedModelId else _state.value.activeModelId
        val model = _state.value.models.firstOrNull { it.id == modelId || it.model == modelId }
        if ((id == null || _state.value.modelOverrideId != null) && model == null) {
            _state.value = _state.value.copy(error = _state.value.modelsError ?: "请等待模型列表加载完成后重试")
            refreshModels(); return
        }
        if (images.isNotEmpty() && model?.supportsImages == false) {
            _state.value = _state.value.copy(error = "这个模型不支持图片，请选择支持图片的模型")
            return
        }
        val requestedModel = model?.model.takeIf { id == null || _state.value.modelOverrideId != null }
        val token = prefs.deviceToken
        val visit = conversationVisit
        val clientId = UUID.randomUUID().toString()
        var targetId = id ?: "local:$clientId"
        val originalDraftKey = draftKey
        val pending = OutgoingMessage(targetId,
            GatewayMessageItem(id = "local:$clientId", text = text, imageDataList = images.map { it.bytes }),
            _state.value.messages.filter { it.isUser }.map { it.id }.toSet(),
            _state.value.messages.mapNotNull { it.stepIndex }.maxOrNull())
        val wasRunning = _state.value.isRunning
        prefs.setDraftText(PENDING_SEND_DRAFT, text)
        prefs.saveDraftImages(PENDING_SEND_DRAFT, images.map { File(checkNotNull(it.uri.path)) })
        prefs.pendingSendTarget = originalDraftKey
        setDraft("")
        saveImages(draftKey, emptyList())
        _state.value = _state.value.copy(attachments = emptyList())
        if (id == null) moveDraft(originalDraftKey, targetId)
        if (id == null) conversationVisit++
        _state.value = _state.value.copy(
            selectedConversationId = targetId, outgoing = _state.value.outgoing + pending,
            draft = "", attachments = emptyList(),
            isSending = true, isRunning = true, streamingMessageId = null, error = null)
        viewModelScope.launch {
            val result = if (id == null && images.isEmpty()) {
                api.createCascade(
                    workspaceUri = "",
                    prompt = text,
                    model = requestedModel,
                    projectId = ProjectItem.PURE_CHAT.id,
                )
            } else if (id == null) {
                api.createCascade("", "", model = requestedModel, projectId = ProjectItem.PURE_CHAT.id)
                    .fold(
                        onSuccess = { createdId ->
                            if (prefs.deviceToken != token) return@fold Result.failure(IllegalStateException("配对已变化"))
                            prefs.pendingSendTarget = createdId
                            moveDraft(targetId, createdId)
                            _state.value = _state.value.copy(outgoing = _state.value.outgoing.map {
                                if (it.message.id == pending.message.id) it.copy(conversationId = createdId) else it
                            })
                            if (_state.value.selectedConversationId == targetId) openConversation(createdId)
                            targetId = createdId
                            api.sendMessage(
                                createdId, text, model = requestedModel,
                                images = images.map { it.bytes to it.mimeType },
                                clientMessageId = clientId,
                            ).map { createdId }
                        },
                        onFailure = { Result.failure(it) },
                    )
            } else {
                api.sendMessage(
                    id, text, model = requestedModel, images = images.map { it.bytes to it.mimeType },
                    clientMessageId = clientId,
                ).map { id }
            }
            if (prefs.deviceToken != token) return@launch
            result.fold(
                onSuccess = { conversationId ->
                    if (targetId.startsWith("local:")) moveDraft(targetId, conversationId)
                    _state.value = _state.value.copy(isSending = false,
                        outgoing = _state.value.outgoing.map {
                            if (it.message.id == pending.message.id) it.copy(conversationId = conversationId) else it
                        })
                    if (_state.value.selectedConversationId == targetId && targetId.startsWith("local:")) {
                        openConversation(conversationId)
                    } else if (_state.value.selectedConversationId == conversationId) {
                        if (conversationVisit == visit && requestedModel != null) {
                            _state.value = _state.value.copy(activeModelId = model?.id, modelOverrideId = null)
                        }
                        loadMessages(conversationId)
                    }
                    refreshConversations()
                },
                onFailure = { error ->
                    val stillHere = _state.value.selectedConversationId == targetId
                    val unconfirmed = _state.value.outgoing.any { it.message.id == pending.message.id }
                    val restoreKey = if (targetId.startsWith("local:")) originalDraftKey else targetId
                    if (targetId.startsWith("local:")) moveDraft(targetId, restoreKey)
                    val current = _state.value
                    val restoredDraft = if (unconfirmed) listOf(text, prefs.getDraftText(restoreKey))
                        .filter { it.isNotBlank() }.joinToString("\n\n") else prefs.getDraftText(restoreKey)
                    val restoredImages = if (unconfirmed) (images +
                        (if (draftKey == restoreKey) current.attachments else draftAttachments[restoreKey].orEmpty()))
                        .distinctBy { it.uri } else current.attachments
                    if (unconfirmed) {
                        prefs.setDraftText(restoreKey, restoredDraft)
                        saveImages(restoreKey, restoredImages)
                    }
                    _state.value = current.copy(
                        outgoing = current.outgoing.filterNot { it.message.id == pending.message.id },
                        selectedConversationId = if (stillHere && targetId.startsWith("local:")) null else current.selectedConversationId,
                        draft = if (draftKey == restoreKey && unconfirmed) restoredDraft else current.draft,
                        attachments = if (draftKey == restoreKey && unconfirmed) restoredImages else current.attachments,
                        isSending = false,
                        modelOverrideId = if (stillHere && conversationVisit == visit) null else current.modelOverrideId,
                        isRunning = if (stillHere && unconfirmed) wasRunning else current.isRunning,
                        error = if (unconfirmed) "${error.message ?: "消息发送失败"}（内容已恢复到原会话草稿）" else null,
                    )
                },
            )
            clearSendSnapshot()
        }
    }

    private fun mutateConversation(id: String, operation: suspend () -> Result<Unit>, onSuccess: () -> Unit) {
        if (id.startsWith("local:") || id in _state.value.busyConversations) return
        _state.value = _state.value.copy(busyConversations = _state.value.busyConversations + id)
        viewModelScope.launch {
            val result = operation()
            _state.value = _state.value.copy(busyConversations = _state.value.busyConversations - id)
            result.fold(onSuccess = { onSuccess(); refreshConversations() }, onFailure = {
                if (it is GatewayAuthorizationException) expirePairing()
                else _state.value = _state.value.copy(error = it.message ?: "会话操作失败")
            })
        }
    }

    fun deleteSelectedConversation() { _state.value.selectedConversationId?.let(::deleteConversation) }

    fun deleteConversation(id: String) {
        if (id in _state.value.busyConversations || id.startsWith("local:")) return
        if (_state.value.selectedConversationId == id) _state.value = _state.value.copy(isDeleting = true)
        mutateConversation(id, { api.deleteConversation(id).also {
            if (_state.value.selectedConversationId == id) _state.value = _state.value.copy(isDeleting = false)
        } }) {
            prefs.clearDraftText(id)
            prefs.clearDraftImages(id)
            draftAttachments.remove(id)
            if (_state.value.selectedConversationId == id) {
                _state.value = _state.value.copy(draft = "", attachments = emptyList())
                closeConversation(); switchDraft(NEW_CHAT_DRAFT)
            }
        }
    }

    fun renameSelectedConversation(title: String, onSuccess: () -> Unit) {
        val id = _state.value.selectedConversationId ?: return
        val visit = conversationVisit
        renameConversation(id, title) { if (conversationVisit == visit) onSuccess() }
    }

    fun renameConversation(id: String, title: String, onSuccess: () -> Unit) {
        if (title.isBlank() || id in _state.value.busyConversations || id.startsWith("local:")) return
        if (_state.value.selectedConversationId == id) _state.value = _state.value.copy(isRenaming = true)
        mutateConversation(id, { api.renameConversation(id, title).also {
            if (_state.value.selectedConversationId == id) _state.value = _state.value.copy(isRenaming = false)
        } }) {
            _state.value = _state.value.copy(conversations = _state.value.conversations.map {
                if (it.id == id) it.copy(title = title.trim()) else it
            })
            onSuccess()
        }
    }

    fun setPinned(id: String, pinned: Boolean) = mutateConversation(id, { api.setConversationPinned(id, pinned) }) {
        _state.value = _state.value.copy(conversations = _state.value.conversations.map {
            if (it.id == id) it.copy(isPinned = pinned) else it
        }.sortedWith(compareByDescending<ConversationItem> { it.isPinned }.thenByDescending { it.lastModifiedEpochMs }))
    }

    fun setArchived(id: String, archived: Boolean) {
        if (archived && (_state.value.conversations.any { it.id == id && it.status.isRunning } ||
                _state.value.selectedConversationId == id && _state.value.isRunning)) {
            _state.value = _state.value.copy(error = "请先停止生成，再归档会话")
            return
        }
        mutateConversation(id, { api.setConversationArchived(id, archived) }) {
            _state.value = _state.value.copy(conversations = _state.value.conversations.map {
                if (it.id == id) it.copy(isArchived = archived) else it
            })
            if (archived && _state.value.selectedConversationId == id) { closeConversation(); switchDraft(NEW_CHAT_DRAFT) }
        }
    }

    fun dismissRevert() {
        revertPreviewVisit++
        if (!_state.value.isReverting) _state.value = _state.value.copy(revertMessage = null, revertPreview = null,
            isLoadingRevert = false, revertError = null)
    }

    fun previewRevert(message: GatewayMessageItem) {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        val index = message.stepIndex ?: return
        if (current.isSending || current.isReverting || current.isStopping || id in current.busyConversations || message.canRevert == false) return
        val previewVisit = ++revertPreviewVisit
        if (message.canRevert == null) {
            _state.value = current.copy(revertMessage = message, revertPreview = null, isLoadingRevert = false,
                revertError = "电脑网关版本较旧，请更新并重启 mgy 后下拉刷新会话，再使用回退。")
            return
        }
        val visit = conversationVisit
        _state.value = current.copy(revertMessage = message, revertPreview = null, revertError = null, isLoadingRevert = true)
        viewModelScope.launch {
            val result = api.getRevertPreview(id, index)
            if (conversationVisit != visit || revertPreviewVisit != previewVisit || _state.value.revertMessage?.id != message.id) return@launch
            result.fold(onSuccess = { _state.value = _state.value.copy(revertPreview = it, isLoadingRevert = false) },
                onFailure = { _state.value = _state.value.copy(revertError = it.message ?: "预览失败", isLoadingRevert = false) })
        }
    }

    fun executeRevert(conversationOnly: Boolean) {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        val message = current.revertMessage ?: return
        val index = message.stepIndex ?: return
        if (current.revertPreview == null || current.isReverting || current.isSending || current.isStopping || id in current.busyConversations) return
        val visit = ++conversationVisit
        messagesJob?.cancel(); messagesJob = null
        stream.disconnect()
        _state.value = current.copy(isReverting = true, revertError = null, busyConversations = current.busyConversations + id)
        viewModelScope.launch {
            val stagedFiles = mutableListOf<File>()
            try {
                val urls = message.imageUrls.orEmpty().distinct()
                check(urls.isNotEmpty() || message.media.isNullOrEmpty()) { "原图片不可恢复，请在电脑端回退" }
                check(urls.size + _state.value.attachments.size <= 4) { "恢复后图片超过 4 张，请先保存当前草稿" }
                val images = urls.map { url ->
                    val (bytes, mime) = api.imageForDraft(url, id).getOrThrow()
                    withContext(Dispatchers.IO) {
                        val file = prefs.storeDraftImage(bytes, mime).also(stagedFiles::add)
                        PendingImage(Uri.fromFile(file), bytes, mime)
                    }
                }
                if (conversationVisit != visit) return@launch
                api.executeRevert(id, index, conversationOnly).getOrThrow()
                // Restore to the original draft even when the user changes conversations during the request.
                val text = listOf(message.effectiveText, if (draftKey == id) _state.value.draft else prefs.getDraftText(id))
                    .filter { it.isNotEmpty() }.joinToString("\n\n")
                prefs.setDraftText(id, text)
                saveImages(id, (images + if (draftKey == id) _state.value.attachments else draftAttachments[id].orEmpty()).distinctBy { it.uri })
                if (conversationVisit == visit) {
                    conversationVisit++
                    messagesJob?.cancel(); messagesJob = null
                    stream.disconnect()
                    _state.value = _state.value.copy(messages = emptyList(), outgoing = _state.value.outgoing.filterNot { it.conversationId == id },
                        draft = text, attachments = draftAttachments[id].orEmpty(), isRunning = false, streamingMessageId = null,
                        pendingInteraction = null, revertMessage = null, revertPreview = null, isReverting = false, isLoadingMessages = true)
                    stream.connect(id); loadMessages(id)
                } else if (draftKey == id) {
                    _state.value = _state.value.copy(draft = text, attachments = draftAttachments[id].orEmpty())
                    if (_state.value.selectedConversationId == id) openConversation(id)
                }
                refreshConversations()
            } catch (error: Exception) {
                if (conversationVisit == visit) {
                    _state.value = _state.value.copy(isReverting = false, revertError = error.message ?: "回退失败")
                    stream.connect(id); loadMessages(id)
                }
            } finally {
                stagedFiles.forEach(prefs::deleteUnusedDraftImage)
                _state.value = _state.value.copy(busyConversations = _state.value.busyConversations - id)
            }
        }
    }

    fun stopGeneration() {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        if (id.startsWith("local:") || !current.isRunning || current.isSending || current.isStopping) return
        val visit = conversationVisit
        _state.value = current.copy(isStopping = true)
        viewModelScope.launch {
            api.cancelInvocation(id).fold(onSuccess = {
                if (conversationVisit == visit) loadMessages(id).join()
            }, onFailure = {
                if (conversationVisit == visit) _state.value = _state.value.copy(error = it.message ?: "停止生成失败")
            })
            if (conversationVisit == visit) _state.value = _state.value.copy(isStopping = false)
        }
    }

    fun respondToInteraction(option: InteractionOption?, writeInResponse: String = "") {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        val interaction = current.pendingInteraction ?: return
        val visit = conversationVisit
        if (interaction.isMultiSelect || current.isSubmittingInteraction) return
        _state.value = current.copy(isSubmittingInteraction = true, error = null)
        viewModelScope.launch {
            api.submitInteraction(id, interaction, option, writeInResponse).fold(
                onSuccess = {
                    if (conversationVisit != visit) return@fold
                    _state.value = _state.value.copy(
                        pendingInteraction = null,
                        isSubmittingInteraction = false,
                    )
                    loadMessages(id)
                },
                onFailure = { error ->
                    if (conversationVisit != visit) return@fold
                    _state.value = _state.value.copy(
                        isSubmittingInteraction = false,
                        error = error.message ?: "提交选择失败",
                    )
                },
            )
        }
    }

    private fun loadMessages(id: String): Job {
        messagesJob?.takeIf { it.isActive }?.let { return it }
        val visit = conversationVisit
        val revision = messagesRevision
        return viewModelScope.launch {
            api.fetchMessages(id, limit = 50).fold(
                onSuccess = { payload ->
                    if (_state.value.selectedConversationId == id && conversationVisit == visit) {
                        if (fullSnapshotRevision > revision) return@fold
                        val changed = messagesRevision != revision
                        val current = _state.value
                        val merged = if (changed) mergeMessages(payload.messages.orEmpty(), current.messages)
                            else mergeMessages(current.messages, payload.messages.orEmpty())
                        val outgoing = reconcileOutgoing(current.outgoing, id, merged)
                        _state.value = _state.value.copy(
                            messages = merged,
                            outgoing = outgoing,
                            isRunning = (if (changed) current.isRunning else payload.status.contains("RUNNING", ignoreCase = true)) ||
                                outgoing.any { it.conversationId == id },
                            pendingInteraction = if (changed) current.pendingInteraction else payload.pendingInteraction,
                            activeModelId = if (changed) current.activeModelId else payload.activeModel ?: current.activeModelId,
                            isLoadingMessages = false,
                            hasMoreMessages = payload.hasMore,
                            nextMessageOffset = payload.nextOffset,
                            messagesError = null,
                        )
                    }
                },
                onFailure = { error ->
                    if (_state.value.selectedConversationId == id && conversationVisit == visit) {
                        if (error is GatewayAuthorizationException) { expirePairing(); return@fold }
                        if (messagesRevision != revision) return@fold
                        _state.value = _state.value.copy(
                            isLoadingMessages = false,
                            messagesError = error.message ?: "获取消息失败",
                        )
                    }
                },
            )
        }.also { messagesJob = it }
    }

    fun retryMessages() {
        val id = _state.value.selectedConversationId ?: return
        if (messagesJob?.isActive == true) return
        _state.value = _state.value.copy(isLoadingMessages = true, messagesError = null)
        resumeConnection()
    }

    /** Sync after background/lock or manual retry while keeping the current page and draft. */
    fun resumeConnection() {
        if (!_state.value.isPaired || recoveryJob?.isActive == true) return
        val visit = conversationVisit
        recoveryJob = viewModelScope.launch {
            connectionManager.probeEndpoints(prefs)
            if (conversationVisit == visit && !_state.value.isSending && !_state.value.isReverting && !_state.value.isStopping) {
                _state.value.selectedConversationId?.takeUnless { it.startsWith("local:") }?.let { id ->
                    stream.reconnect()
                    loadMessages(id)
                }
            }
            refreshConversations()
            refreshProjects()
        }
    }

    fun loadOlderMessages() {
        val visit = conversationVisit
        val current = _state.value
        val id = current.selectedConversationId ?: return
        if (!current.hasMoreMessages || current.isLoadingOlder) return
        _state.value = current.copy(isLoadingOlder = true, olderMessagesError = null)
        viewModelScope.launch {
            api.fetchMessages(id, limit = 50, offset = current.nextMessageOffset).fold(
                onSuccess = { payload ->
                    if (_state.value.selectedConversationId == id && conversationVisit == visit) {
                        val older = payload.messages ?: emptyList()
                        val existingIds = older.map { it.id }.toSet()
                        _state.value = _state.value.copy(
                            messages = older + _state.value.messages.filterNot { it.id in existingIds },
                            hasMoreMessages = payload.hasMore,
                            nextMessageOffset = payload.nextOffset,
                            isLoadingOlder = false,
                        )
                    }
                },
                onFailure = { error ->
                    if (conversationVisit != visit) return@fold
                    if (error is GatewayAuthorizationException) { expirePairing(); return@fold }
                    _state.value = _state.value.copy(
                        isLoadingOlder = false,
                        olderMessagesError = error.message ?: "加载更早消息失败",
                    )
                },
            )
        }
    }

    override fun onCleared() {
        stream.close()
        connectionManager.stopMonitoring()
        super.onCleared()
    }
}

private const val DEFAULT_CHAT_MODEL = "gemini-3.8-flash-high"
private const val NEW_CHAT_DRAFT = "android_new_chat"
private const val PENDING_SEND_DRAFT = "android_pending_send"

private fun mergeMessages(
    current: List<GatewayMessageItem>,
    incoming: List<GatewayMessageItem>,
): List<GatewayMessageItem> {
    val result = current.toMutableList()
    incoming.forEach { message ->
        val index = result.indexOfFirst { it.id.isNotBlank() && it.id == message.id }
        if (index >= 0) result[index] = message else result.add(message)
    }
    return result
}
