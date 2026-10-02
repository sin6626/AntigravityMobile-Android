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

    private val _state = MutableStateFlow(ChatUiState(isPaired = prefs.isPaired()))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    val mediaImageLoader: ImageLoader get() = api.mediaImageLoader
    fun mediaImageRequest(raw: String): ImageRequest = api.mediaImageRequest(raw)
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
        _state.value = _state.value.copy(draft = value)
    }

    fun addImages(uris: List<Uri>) {
        viewModelScope.launch {
            for (uri in uris.take(4)) {
                if (_state.value.attachments.size >= 4) break
                if (_state.value.attachments.any { it.uri == uri }) continue
                try {
                    val image = withContext(Dispatchers.IO) { readImage(uri) }
                    _state.value = _state.value.copy(attachments = _state.value.attachments + image)
                } catch (error: Exception) {
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
        stream.disconnect()
        _state.value = _state.value.copy(
            selectedConversationId = id,
            messages = emptyList(),
            isLoadingMessages = true,
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
        stream.disconnect()
        _state.value = _state.value.copy(
            selectedConversationId = null,
            messages = emptyList(),
            draft = "",
            streamingMessageId = null,
            attachments = emptyList(),
            isLoadingMessages = false,
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
        if ((text.isEmpty() && images.isEmpty()) || _state.value.isSending || _state.value.isLoadingMessages) return
        val id = _state.value.selectedConversationId
        val clientId = UUID.randomUUID().toString()
        var targetId = id ?: "local:$clientId"
        val pending = OutgoingMessage(targetId,
            GatewayMessageItem(id = "local:$clientId", text = text, imageDataList = images.map { it.bytes }),
            _state.value.messages.filter { it.isUser }.map { it.id }.toSet(),
            _state.value.messages.mapNotNull { it.stepIndex }.maxOrNull())
        val wasRunning = _state.value.isRunning
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
                    val current = _state.value
                    val stillHere = current.selectedConversationId == targetId
                    val unconfirmed = current.outgoing.any { it.message.id == pending.message.id }
                    _state.value = current.copy(
                        outgoing = current.outgoing.filterNot { it.message.id == pending.message.id },
                        selectedConversationId = if (stillHere && targetId.startsWith("local:")) null else current.selectedConversationId,
                        draft = if (unconfirmed) listOf(text, current.draft).filter { it.isNotBlank() }.joinToString("\n\n") else current.draft,
                        attachments = if (unconfirmed) (images + current.attachments).distinctBy { it.uri } else current.attachments,
                        isSending = false,
                        isRunning = if (stillHere && unconfirmed) wasRunning else current.isRunning,
                        error = if (unconfirmed) "${error.message ?: "消息发送失败"}（内容已恢复到输入框）" else null,
                    )
                },
            )
        }
    }

    fun deleteSelectedConversation() {
        val id = _state.value.selectedConversationId ?: return
        if (id.startsWith("local:")) return
        viewModelScope.launch {
            api.deleteConversation(id).fold(
                onSuccess = {
                    newConversation()
                    refreshConversations()
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(error = error.message ?: "删除会话失败")
                },
            )
        }
    }

    fun respondToInteraction(option: InteractionOption?, writeInResponse: String = "") {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        val interaction = current.pendingInteraction ?: return
        if (interaction.isMultiSelect || current.isSubmittingInteraction) return
        _state.value = current.copy(isSubmittingInteraction = true, error = null)
        viewModelScope.launch {
            api.submitInteraction(id, interaction, option, writeInResponse).fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        pendingInteraction = null,
                        isSubmittingInteraction = false,
                    )
                    loadMessages(id)
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isSubmittingInteraction = false,
                        error = error.message ?: "提交选择失败",
                    )
                },
            )
        }
    }

    private fun loadMessages(id: String) {
        viewModelScope.launch {
            api.fetchMessages(id, limit = 50).fold(
                onSuccess = { payload ->
                    if (_state.value.selectedConversationId == id) {
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
                    if (_state.value.selectedConversationId == id) {
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
        val current = _state.value
        val id = current.selectedConversationId ?: return
        if (!current.hasMoreMessages || current.isLoadingOlder) return
        _state.value = current.copy(isLoadingOlder = true)
        viewModelScope.launch {
            api.fetchMessages(id, limit = 50, offset = current.nextMessageOffset).fold(
                onSuccess = { payload ->
                    if (_state.value.selectedConversationId == id) {
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
