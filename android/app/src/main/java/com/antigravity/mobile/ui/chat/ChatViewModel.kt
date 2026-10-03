package com.antigravity.mobile.ui.chat

import android.app.Application
import android.net.Uri
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
import com.antigravity.mobile.data.service.ApiClient
import com.antigravity.mobile.data.service.GatewayAuthorizationException
import com.antigravity.mobile.data.service.ConnectionManager
import com.antigravity.mobile.data.service.PreferencesManager
import com.antigravity.mobile.data.service.StreamWebSocketClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
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
    val selectedConversationId: String? = null,
    val messages: List<GatewayMessageItem> = emptyList(),
    val outgoing: List<OutgoingMessage> = emptyList(),
    val draft: String = "",
    val attachments: List<PendingImage> = emptyList(),
    val error: String? = null,
)

data class PendingImage(val uri: Uri, val bytes: ByteArray, val mimeType: String)

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
    private val _state = MutableStateFlow(ChatUiState(isPaired = prefs.isPaired(), draft = prefs.getDraftText(draftKey)))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    val mediaImageLoader: ImageLoader get() = api.mediaImageLoader
    fun mediaImageRequest(raw: String): ImageRequest = api.mediaImageRequest(raw)
    fun linkedImageRequest(raw: String): ImageRequest = api.mediaImageRequest(raw, _state.value.selectedConversationId)
    suspend fun fetchLinkedFile(uri: String): Result<FileContentResponse> =
        api.fetchFileContent(uri.substringBefore('#'), _state.value.selectedConversationId)
    val gatewayUrl: String? get() = prefs.gatewayBaseUrl

    init {
        if (prefs.isPaired()) {
            connectionManager.startMonitoring(prefs, viewModelScope)
            refreshConversations()
            refreshProjects()
        }
        viewModelScope.launch {
            stream.streamUpdates.collect { update ->
                if (update == null || update.cascadeId != _state.value.selectedConversationId) return@collect
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
        draftAttachments[draftKey] = _state.value.attachments
        draftKey = key
        _state.value = _state.value.copy(draft = prefs.getDraftText(key), attachments = draftAttachments[key].orEmpty())
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
        draftAttachments[to] = images
        prefs.clearDraftText(from)
        draftAttachments.remove(from)
        if (active) draftKey = to
        if (draftKey == to) _state.value = _state.value.copy(draft = text, attachments = images)
    }

    fun addImages(uris: List<Uri>) {
        val key = draftKey
        viewModelScope.launch {
            for (uri in uris.take(4)) {
                if (draftKey != key) break
                if (_state.value.attachments.size >= 4) break
                if (_state.value.attachments.any { it.uri == uri }) continue
                try {
                    val image = withContext(Dispatchers.IO) { readImage(uri) }
                    if (draftKey != key) break
                    _state.value = _state.value.copy(attachments = _state.value.attachments + image)
                } catch (error: Exception) {
                    if (draftKey != key) break
                    _state.value = _state.value.copy(error = error.message ?: "读取图片失败")
                }
            }
        }
    }

    fun removeImage(uri: Uri) {
        _state.value = _state.value.copy(attachments = _state.value.attachments.filterNot { it.uri == uri })
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
        return PendingImage(uri, output.toByteArray(), mime)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun expirePairing() {
        if (!_state.value.isPaired) return
        prefs.deviceToken = null
        prefs.deviceId = null
        stream.disconnect()
        connectionManager.stopMonitoring()
        _state.value = _state.value.copy(
            isPaired = false, isLoadingConversations = false, isLoadingProjects = false,
            selectedConversationId = null, conversations = emptyList(), projects = emptyList(),
            messages = emptyList(), outgoing = emptyList(), error = "设备授权已失效，请重新配对",
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
                    _state.value = _state.value.copy(
                        isPaired = true, isPairing = false,
                        pairSuccessCount = _state.value.pairSuccessCount + 1,
                        conversations = emptyList(), projects = emptyList(), error = null,
                    )
                    connectionManager.startMonitoring(prefs, viewModelScope)
                    refreshConversations()
                    refreshProjects()
                },
                onFailure = {
                    _state.value = _state.value.copy(isPairing = false, error = it.message ?: "配对失败")
                },
            )
        }
    }

    fun refreshConversations() {
        if (!_state.value.isPaired) return
        _state.value = _state.value.copy(isLoadingConversations = true)
        viewModelScope.launch {
            api.fetchConversations().fold(
                onSuccess = { conversations ->
                    _state.value = _state.value.copy(
                        conversations = conversations,
                        isLoadingConversations = false,
                        error = null,
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
                        error = error.message ?: "获取会话列表失败",
                    )
                },
            )
        }
    }

    fun refreshProjects() {
        if (!_state.value.isPaired || _state.value.isLoadingProjects) return
        _state.value = _state.value.copy(isLoadingProjects = true)
        viewModelScope.launch {
            api.fetchProjects().fold(
                onSuccess = { projects ->
                    _state.value = _state.value.copy(
                        projects = projects.filterNot { it.isPureChat },
                        isLoadingProjects = false,
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
                        error = error.message ?: "获取项目失败",
                    )
                },
            )
        }
    }

    fun openConversation(id: String) {
        conversationVisit++
        switchDraft(id)
        stream.disconnect()
        _state.value = _state.value.copy(
            selectedConversationId = id,
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
        )
        stream.connect(id)
        loadMessages(id)
        api.notifySessionFocus(id)
    }

    fun newConversation() {
        closeConversation()
        switchDraft(NEW_CHAT_DRAFT)
        prefs.clearDraftText(NEW_CHAT_DRAFT)
        draftAttachments.remove(NEW_CHAT_DRAFT)
        _state.value = _state.value.copy(draft = "", attachments = emptyList())
    }

    fun closeConversation() {
        conversationVisit++
        stream.disconnect()
        _state.value = _state.value.copy(
            selectedConversationId = null,
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
        )
    }

    fun send() {
        val text = _state.value.draft.trim()
        val images = _state.value.attachments
        if ((text.isEmpty() && images.isEmpty()) || _state.value.isSending || _state.value.isLoadingMessages || _state.value.isStopping || _state.value.isReverting || _state.value.selectedConversationId in _state.value.busyConversations) return
        val id = _state.value.selectedConversationId
        val clientId = UUID.randomUUID().toString()
        var targetId = id ?: "local:$clientId"
        val originalDraftKey = draftKey
        val pending = OutgoingMessage(targetId,
            GatewayMessageItem(id = "local:$clientId", text = text, imageDataList = images.map { it.bytes }),
            _state.value.messages.filter { it.isUser }.map { it.id }.toSet(),
            _state.value.messages.mapNotNull { it.stepIndex }.maxOrNull())
        val wasRunning = _state.value.isRunning
        setDraft("")
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
                    model = DEFAULT_CHAT_MODEL,
                    projectId = ProjectItem.PURE_CHAT.id,
                )
            } else if (id == null) {
                api.createCascade("", "", model = DEFAULT_CHAT_MODEL, projectId = ProjectItem.PURE_CHAT.id)
                    .fold(
                        onSuccess = { createdId ->
                            moveDraft(targetId, createdId)
                            _state.value = _state.value.copy(outgoing = _state.value.outgoing.map {
                                if (it.message.id == pending.message.id) it.copy(conversationId = createdId) else it
                            })
                            if (_state.value.selectedConversationId == targetId) openConversation(createdId)
                            targetId = createdId
                            api.sendMessage(
                                createdId, text, model = DEFAULT_CHAT_MODEL,
                                images = images.map { it.bytes to it.mimeType },
                                clientMessageId = clientId,
                            ).map { createdId }
                        },
                        onFailure = { Result.failure(it) },
                    )
            } else {
                api.sendMessage(
                    id, text, images = images.map { it.bytes to it.mimeType },
                    clientMessageId = clientId,
                ).map { id }
            }
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
                        draftAttachments[restoreKey] = restoredImages
                    }
                    _state.value = current.copy(
                        outgoing = current.outgoing.filterNot { it.message.id == pending.message.id },
                        selectedConversationId = if (stillHere && targetId.startsWith("local:")) null else current.selectedConversationId,
                        draft = if (draftKey == restoreKey && unconfirmed) restoredDraft else current.draft,
                        attachments = if (draftKey == restoreKey && unconfirmed) restoredImages else current.attachments,
                        isSending = false,
                        isRunning = if (stillHere && unconfirmed) wasRunning else current.isRunning,
                        error = if (unconfirmed) "${error.message ?: "消息发送失败"}（内容已恢复到原会话草稿）" else null,
                    )
                },
            )
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
        if (!_state.value.isReverting) _state.value = _state.value.copy(revertMessage = null, revertPreview = null,
            isLoadingRevert = false, revertError = null)
    }

    fun previewRevert(message: GatewayMessageItem) {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        val index = message.stepIndex ?: return
        if (current.isRunning || current.isSending || current.isReverting || !message.canRevert) return
        val visit = conversationVisit
        _state.value = current.copy(revertMessage = message, revertPreview = null, revertError = null, isLoadingRevert = true)
        viewModelScope.launch {
            val result = api.getRevertPreview(id, index)
            if (conversationVisit != visit || _state.value.revertMessage?.id != message.id) return@launch
            result.fold(onSuccess = { _state.value = _state.value.copy(revertPreview = it, isLoadingRevert = false) },
                onFailure = { _state.value = _state.value.copy(revertError = it.message ?: "预览失败", isLoadingRevert = false) })
        }
    }

    fun executeRevert(conversationOnly: Boolean) {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        val message = current.revertMessage ?: return
        val index = message.stepIndex ?: return
        if (current.revertPreview == null || current.isReverting || current.isRunning || current.isSending || id in current.busyConversations) return
        val visit = conversationVisit
        _state.value = current.copy(isReverting = true, revertError = null, busyConversations = current.busyConversations + id)
        viewModelScope.launch {
            try {
                val urls = message.imageUrls.orEmpty().distinct()
                check(urls.isNotEmpty() || message.media.isNullOrEmpty()) { "原图片不可恢复，请在电脑端回退" }
                check(urls.size + _state.value.attachments.size <= 4) { "恢复后图片超过 4 张，请先保存当前草稿" }
                val images = urls.map { url ->
                    val (bytes, mime) = api.imageForDraft(url, id).getOrThrow()
                    PendingImage(Uri.parse(url), bytes, mime)
                }
                if (conversationVisit != visit) return@launch
                api.executeRevert(id, index, conversationOnly).getOrThrow()
                // Restore to the original draft even when the user changes conversations during the request.
                val text = listOf(message.effectiveText, if (draftKey == id) _state.value.draft else prefs.getDraftText(id))
                    .filter { it.isNotEmpty() }.joinToString("\n\n")
                prefs.setDraftText(id, text)
                draftAttachments[id] = (images + if (draftKey == id) _state.value.attachments else draftAttachments[id].orEmpty()).distinctBy { it.uri }
                if (conversationVisit == visit) {
                    conversationVisit++
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
                if (conversationVisit == visit) _state.value = _state.value.copy(isReverting = false, revertError = error.message ?: "回退失败")
            } finally {
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
        val visit = conversationVisit
        return viewModelScope.launch {
            api.fetchMessages(id, limit = 50).fold(
                onSuccess = { payload ->
                    if (_state.value.selectedConversationId == id && conversationVisit == visit) {
                        val outgoing = reconcileOutgoing(_state.value.outgoing, id, payload.messages.orEmpty())
                        _state.value = _state.value.copy(
                            messages = payload.messages ?: emptyList(),
                            outgoing = outgoing,
                            isRunning = payload.status.contains("RUNNING", ignoreCase = true) ||
                                outgoing.any { it.conversationId == id },
                            pendingInteraction = payload.pendingInteraction,
                            isLoadingMessages = false,
                            hasMoreMessages = payload.hasMore,
                            nextMessageOffset = payload.nextOffset,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    if (_state.value.selectedConversationId == id && conversationVisit == visit) {
                        _state.value = _state.value.copy(
                            isLoadingMessages = false,
                            error = error.message ?: "获取消息失败",
                        )
                    }
                },
            )
        }
    }

    fun loadOlderMessages() {
        val visit = conversationVisit
        val current = _state.value
        val id = current.selectedConversationId ?: return
        if (!current.hasMoreMessages || current.isLoadingOlder) return
        _state.value = current.copy(isLoadingOlder = true)
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
                    _state.value = _state.value.copy(
                        isLoadingOlder = false,
                        error = error.message ?: "加载更早消息失败",
                    )
                },
            )
        }
    }

    override fun onCleared() {
        stream.disconnect()
        connectionManager.stopMonitoring()
        super.onCleared()
    }
}

private const val DEFAULT_CHAT_MODEL = "gemini-3.8-flash-high"
private const val NEW_CHAT_DRAFT = "android_new_chat"

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
