package com.antigravity.mobile.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.mobile.data.model.ConversationItem
import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.data.model.PairingInfo
import com.antigravity.mobile.data.model.PendingInteraction
import com.antigravity.mobile.data.model.InteractionOption
import com.antigravity.mobile.data.model.ProjectItem
import com.antigravity.mobile.data.service.ApiClient
import com.antigravity.mobile.data.service.ConnectionManager
import com.antigravity.mobile.data.service.PreferencesManager
import com.antigravity.mobile.data.service.StreamWebSocketClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val isPaired: Boolean = false,
    val isPairing: Boolean = false,
    val isLoadingConversations: Boolean = false,
    val isLoadingMessages: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasMoreMessages: Boolean = false,
    val nextMessageOffset: Int = 0,
    val isSending: Boolean = false,
    val isRunning: Boolean = false,
    val pendingInteraction: PendingInteraction? = null,
    val isSubmittingInteraction: Boolean = false,
    val conversations: List<ConversationItem> = emptyList(),
    val selectedConversationId: String? = null,
    val messages: List<GatewayMessageItem> = emptyList(),
    val draft: String = "",
    val error: String? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager(application)
    private val connectionManager = ConnectionManager(application)
    private val api = ApiClient(application, prefs, connectionManager)
    private val stream = StreamWebSocketClient(prefs, connectionManager)

    private val _state = MutableStateFlow(ChatUiState(isPaired = prefs.isPaired()))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        if (prefs.isPaired()) {
            connectionManager.startMonitoring(prefs, viewModelScope)
            refreshConversations()
        }
        viewModelScope.launch {
            stream.streamUpdates.collect { update ->
                if (update == null || update.cascadeId != _state.value.selectedConversationId) return@collect
                _state.value = _state.value.copy(
                    messages = update.messages?.let { incoming ->
                        if (update.isFullSnapshot) incoming else mergeMessages(_state.value.messages, incoming)
                    } ?: _state.value.messages,
                    isRunning = update.status.contains("RUNNING", ignoreCase = true),
                    pendingInteraction = update.pendingInteraction,
                )
            }
        }
    }

    fun setDraft(value: String) {
        _state.value = _state.value.copy(draft = value)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun pair(uri: String) {
        if (_state.value.isPairing) return
        val info = try { PairingInfo.parseFromUri(uri) } catch (_: Exception) { null }
        if (info == null) {
            _state.value = _state.value.copy(error = "请粘贴 mgy pair 显示的完整配对链接或扫描二维码")
            return
        }
        _state.value = _state.value.copy(isPairing = true, error = null)
        viewModelScope.launch {
            api.pair(info).fold(
                onSuccess = {
                    _state.value = _state.value.copy(isPaired = true, isPairing = false)
                    connectionManager.startMonitoring(prefs, viewModelScope)
                    refreshConversations()
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
                    _state.value = _state.value.copy(
                        isLoadingConversations = false,
                        error = error.message ?: "获取会话列表失败",
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
            isRunning = false,
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
        if (text.isEmpty() || _state.value.isSending) return
        val id = _state.value.selectedConversationId
        _state.value = _state.value.copy(isSending = true, isRunning = true, error = null)
        viewModelScope.launch {
            val result = if (id == null) {
                api.createCascade(
                    workspaceUri = "",
                    prompt = text,
                    model = DEFAULT_CHAT_MODEL,
                    projectId = ProjectItem.PURE_CHAT.id,
                )
            } else {
                api.sendMessage(id, text, clientMessageId = UUID.randomUUID().toString()).map { id }
            }
            result.fold(
                onSuccess = { conversationId ->
                    _state.value = _state.value.copy(draft = "", isSending = false)
                    if (id == null) {
                        openConversation(conversationId)
                    } else {
                        loadMessages(conversationId)
                    }
                    refreshConversations()
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isSending = false,
                        isRunning = false,
                        error = error.message ?: "消息发送失败",
                    )
                },
            )
        }
    }

    fun deleteSelectedConversation() {
        val id = _state.value.selectedConversationId ?: return
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
                        _state.value = _state.value.copy(
                            messages = payload.messages ?: emptyList(),
                            isRunning = payload.status.contains("RUNNING", ignoreCase = true),
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
