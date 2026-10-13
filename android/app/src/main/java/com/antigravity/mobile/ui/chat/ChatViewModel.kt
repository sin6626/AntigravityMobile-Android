package com.antigravity.mobile.ui.chat

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
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
import com.antigravity.mobile.data.model.QueuedMessageItem
import com.antigravity.mobile.data.model.RunningTaskItem
import com.antigravity.mobile.data.model.SubagentItem
import com.antigravity.mobile.data.model.RevertPreviewResponse
import com.antigravity.mobile.data.model.ProjectItem
import com.antigravity.mobile.data.model.ChatModel
import com.antigravity.mobile.data.model.MessageAttachment
import com.antigravity.mobile.data.model.DraftComposerContext
import com.antigravity.mobile.data.model.WorkspaceContextItem
import com.antigravity.mobile.data.model.SlashCommandDefinition
import com.antigravity.mobile.data.model.ArtifactCommentDraft
import com.antigravity.mobile.data.model.ArtifactCommentDraftState
import com.antigravity.mobile.data.model.ArtifactTextSelection
import com.antigravity.mobile.data.model.ConversationDiffResponse
import com.antigravity.mobile.data.model.DiffCommentDraft
import com.antigravity.mobile.data.model.DiffCommentDraftState
import com.antigravity.mobile.data.model.DiffSide
import com.antigravity.mobile.data.model.PlanReviewPreference
import com.antigravity.mobile.data.service.ApiClient
import com.antigravity.mobile.data.service.GatewayAuthorizationException
import com.antigravity.mobile.data.service.ConnectionManager
import com.antigravity.mobile.data.service.PreferencesManager
import com.antigravity.mobile.data.service.StoredDraftFile
import com.antigravity.mobile.data.service.safeDownloadedFilename
import com.antigravity.mobile.data.service.StreamWebSocketClient
import com.antigravity.mobile.data.service.ConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.io.File
import java.security.MessageDigest
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
    val interactionError: String? = null,
    val queuedMessages: List<QueuedMessageItem> = emptyList(),
    val busyQueuedMessageIds: Set<String> = emptySet(),
    val queueError: String? = null,
    val runningTasks: List<RunningTaskItem> = emptyList(),
    val stoppingTaskIds: Set<String> = emptySet(),
    val tasksError: String? = null,
    val subagents: List<SubagentItem> = emptyList(),
    val activeSubagent: SubagentItem? = null,
    val isLoadingSubagents: Boolean = false,
    val subagentsError: String? = null,
    val canProceed: Boolean = false,
    val proceedArtifactUri: String? = null,
    val isProceedingArtifact: Boolean = false,
    val proceedError: String? = null,
    val proceedSuccessCount: Int = 0,
    val planCommentDraft: ArtifactCommentDraftState = ArtifactCommentDraftState(),
    val isSubmittingPlanComments: Boolean = false,
    val planCommentError: String? = null,
    val planCommentSuccessCount: Int = 0,
    val diffReview: ConversationDiffResponse? = null,
    val isLoadingDiffReview: Boolean = false,
    val diffReviewError: String? = null,
    val diffCommentDraft: DiffCommentDraftState = DiffCommentDraftState(),
    val isSubmittingDiffComments: Boolean = false,
    val diffCommentSuccessCount: Int = 0,
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
    val fileAttachment: PendingFile? = null,
    val workspaceUri: String = "",
    val workspaceContexts: List<WorkspaceContextItem> = emptyList(),
    val slashCommand: SlashCommandDefinition? = null,
    val planReviewPreference: PlanReviewPreference? = null,
    val isLoadingPlanReviewPreference: Boolean = false,
    val isUpdatingPlanReviewPreference: Boolean = false,
    val isSelectingWorkMode: Boolean = false,
    val workModeError: String? = null,
    val planReviewPolicySuccessCount: Int = 0,
    val contextSuggestions: List<WorkspaceContextItem> = emptyList(),
    val commandSuggestions: List<SlashCommandDefinition> = emptyList(),
    val isLoadingContextSuggestions: Boolean = false,
    val isLoadingCommandSuggestions: Boolean = false,
    val contextSuggestionsError: String? = null,
    val commandSuggestionsError: String? = null,
    val isRestoringDraft: Boolean = false,
    val error: String? = null,
    val messagesError: String? = null,
    val olderMessagesError: String? = null,
    val conversationsError: String? = null,
    val projectsError: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
)

data class PendingImage(val uri: Uri, val bytes: ByteArray, val mimeType: String, val sourceUri: Uri = uri)
data class PendingFile(val uri: Uri, val bytes: ByteArray, val mimeType: String, val displayName: String, val size: Long)

data class OutgoingMessage(
    val conversationId: String,
    val message: GatewayMessageItem,
    val knownUserIds: Set<String>,
    val afterStepIndex: Int? = null,
    val hasAttachments: Boolean = false,
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
                    (!(pending.hasAttachments || pending.message.imageDataList.isNotEmpty()) ||
                        !it.media.isNullOrEmpty() || !it.imageUrls.isNullOrEmpty() || it.imageDataList.isNotEmpty() ||
                        !it.attachments.isNullOrEmpty())
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
    private val draftFiles = mutableMapOf<String, PendingFile?>()
    private var conversationVisit = 0L
    private var revertPreviewVisit = 0L
    private var messagesRevision = 0L
    private var fullSnapshotRevision = 0L
    private var messagesJob: Job? = null
    private var draftRestoreJob: Job? = null
    private var recoveryJob: Job? = null
    private var subagentsJob: Job? = null
    private var contextSuggestionsJob: Job? = null
    private var commandSuggestionsJob: Job? = null
    private val subagentTrail = mutableListOf<SubagentItem>()
    private val _state = MutableStateFlow(ChatUiState(isPaired = prefs.isPaired(), draft = prefs.getDraftText(draftKey)))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    val mediaImageLoader: ImageLoader get() = api.mediaImageLoader
    fun mediaImageRequest(raw: String): ImageRequest = api.mediaImageRequest(raw)
    fun linkedImageRequest(raw: String): ImageRequest = api.mediaImageRequest(raw, _state.value.selectedConversationId)
    suspend fun fetchLinkedFile(uri: String): Result<FileContentResponse> =
        api.fetchFileContent(uri.substringBefore('#'), _state.value.selectedConversationId)
    suspend fun downloadLinkedFile(uri: String, displayName: String): Result<File> {
        val cleanUri = uri.substringBefore('#')
        val key = MessageDigest.getInstance("SHA-256").digest(cleanUri.toByteArray())
            .take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val directory = File(getApplication<Application>().cacheDir, "antigravity_documents/$key")
        val target = File(directory, safeDownloadedFilename(displayName))
        return api.downloadFile(cleanUri, target, _state.value.selectedConversationId)
    }
    val gatewayUrl: String? get() = prefs.gatewayBaseUrl

    init {
        // A request interrupted by process death becomes a draft, never an automatic resend.
        prefs.pendingSendTarget?.let { target ->
            prefs.setDraftText(target, listOf(prefs.getDraftText(PENDING_SEND_DRAFT), prefs.getDraftText(target))
                .filter { it.isNotEmpty() }.distinct().joinToString("\n\n"))
            prefs.saveDraftImages(target, (prefs.loadDraftImages(PENDING_SEND_DRAFT) + prefs.loadDraftImages(target)).distinct().take(4))
            prefs.loadDraftFile(PENDING_SEND_DRAFT)?.let { prefs.saveDraftFile(target, it) }
            prefs.saveDraftComposerContext(target, mergeComposerContext(
                prefs.loadDraftComposerContext(PENDING_SEND_DRAFT), prefs.loadDraftComposerContext(target)))
            clearSendSnapshot()
        }
        val initialContext = prefs.loadDraftComposerContext(NEW_CHAT_DRAFT)
        _state.value = _state.value.copy(draft = prefs.getDraftText(NEW_CHAT_DRAFT),
            workspaceContexts = initialContext.workspaceItems, slashCommand = initialContext.slashCommand)
        restoreDraftAttachments(NEW_CHAT_DRAFT)
        if (prefs.isPaired()) {
            val cachedModels = runCatching {
                Json.decodeFromString<List<ChatModel>>(prefs.cachedModelsJson ?: "[]")
            }.getOrDefault(emptyList())
            _state.value = _state.value.copy(models = cachedModels, selectedModelId = prefs.selectedModelId)
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
                val planChanged = previous.proceedArtifactUri != update.proceedArtifactUri
                val nextPlanCommentDraft = if (planChanged) loadPlanCommentDraft(update.cascadeId, update.proceedArtifactUri)
                    else previous.planCommentDraft
                val interactionChanged = previous.pendingInteraction?.let { it.trajectoryId to it.stepIndex } !=
                    update.pendingInteraction?.let { it.trajectoryId to it.stepIndex }
                _state.value = _state.value.copy(
                    messages = nextMessages,
                    outgoing = outgoing,
                    isRunning = running || outgoing.any { it.conversationId == update.cascadeId },
                    streamingMessageId = if (running && (growing || previous.streamingMessageId == latestAgent?.id)) latestAgent?.id else null,
                    pendingInteraction = update.pendingInteraction,
                    isSubmittingInteraction = if (interactionChanged) false else previous.isSubmittingInteraction,
                    interactionError = if (interactionChanged || update.pendingInteraction == null) null else previous.interactionError,
                    queuedMessages = update.queuedMessages ?: previous.queuedMessages,
                    runningTasks = update.runningTasks.orEmpty(),
                    canProceed = update.canProceed,
                    proceedArtifactUri = update.proceedArtifactUri,
                    isProceedingArtifact = if (planChanged) false else previous.isProceedingArtifact,
                    proceedError = if (planChanged || !update.canProceed) null else previous.proceedError,
                    planCommentDraft = nextPlanCommentDraft,
                    isSubmittingPlanComments = if (planChanged) false else previous.isSubmittingPlanComments,
                    planCommentError = if (planChanged || !update.canProceed) null else previous.planCommentError,
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

    private fun loadPlanCommentDraft(cascadeId: String, artifactUri: String?) =
        artifactUri?.takeIf(String::isNotBlank)?.let { prefs.loadArtifactCommentDraft(cascadeId, it) }
            ?: ArtifactCommentDraftState()

    private fun savePlanCommentDraft(value: ArtifactCommentDraftState) {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        val uri = current.proceedArtifactUri ?: return
        prefs.saveArtifactCommentDraft(id, uri, value)
        _state.value = current.copy(planCommentDraft = value, planCommentError = null)
    }

    fun selectPlanComment(selection: ArtifactTextSelection) {
        if (selection.content.isBlank() || selection.startLine < 0 || selection.endLine <= selection.startLine) return
        if (_state.value.planCommentDraft.comments.size >= 20) {
            _state.value = _state.value.copy(planCommentError = "每次最多提交 20 条评论")
            return
        }
        savePlanCommentDraft(_state.value.planCommentDraft.copy(activeSelection = selection))
    }

    fun setPlanComment(value: String) {
        savePlanCommentDraft(_state.value.planCommentDraft.copy(activeComment = value.take(4_000)))
    }

    fun addPlanComment() {
        val draft = _state.value.planCommentDraft
        val selection = draft.activeSelection ?: return
        val comment = draft.activeComment.trim()
        if (comment.isEmpty()) {
            _state.value = _state.value.copy(planCommentError = "请填写评论内容")
            return
        }
        if (draft.comments.size >= 20) {
            _state.value = _state.value.copy(planCommentError = "每次最多提交 20 条评论")
            return
        }
        savePlanCommentDraft(draft.copy(
            comments = draft.comments + ArtifactCommentDraft(selection, comment),
            activeSelection = null,
            activeComment = "",
        ))
    }

    fun removePlanComment(index: Int) {
        val draft = _state.value.planCommentDraft
        if (index !in draft.comments.indices) return
        savePlanCommentDraft(draft.copy(comments = draft.comments.filterIndexed { itemIndex, _ -> itemIndex != index }))
    }

    fun clearPlanCommentSelection() {
        savePlanCommentDraft(_state.value.planCommentDraft.copy(activeSelection = null, activeComment = ""))
    }

    private fun readyPlanComments(draft: ArtifactCommentDraftState): List<ArtifactCommentDraft> =
        if (draft.activeSelection != null && draft.activeComment.isNotBlank())
            draft.comments + ArtifactCommentDraft(draft.activeSelection, draft.activeComment.trim())
        else draft.comments

    fun submitPlanComments() {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        val uri = current.proceedArtifactUri?.takeIf { current.canProceed && it.isNotBlank() } ?: return
        if (current.isSubmittingPlanComments || current.isProceedingArtifact) return
        val comments = readyPlanComments(current.planCommentDraft)
        if (comments.isEmpty()) {
            _state.value = current.copy(planCommentError = "请先点选方案段落并填写评论")
            return
        }
        val visit = conversationVisit
        val model = current.modelOverrideId?.let { selected ->
            current.models.firstOrNull { it.id == selected || it.model == selected }?.model
        }
        _state.value = current.copy(isSubmittingPlanComments = true, planCommentError = null)
        viewModelScope.launch {
            api.submitArtifactComments(id, uri, comments, model).fold(
                onSuccess = {
                    if (conversationVisit != visit) return@fold
                    prefs.clearArtifactCommentDraft(id, uri)
                    val latest = _state.value
                    _state.value = latest.copy(
                        canProceed = false,
                        proceedArtifactUri = null,
                        planCommentDraft = ArtifactCommentDraftState(),
                        isSubmittingPlanComments = false,
                        planCommentError = null,
                        planCommentSuccessCount = latest.planCommentSuccessCount + 1,
                        isRunning = true,
                    )
                    loadMessages(id)
                },
                onFailure = { error ->
                    if (conversationVisit != visit) return@fold
                    _state.value = _state.value.copy(
                        isSubmittingPlanComments = false,
                        planCommentError = error.message ?: "方案评论提交失败",
                    )
                },
            )
        }
    }

    fun loadDiffReview() {
        val current = _state.value
        val id = current.selectedConversationId?.takeUnless { it.startsWith("local:") } ?: return
        if (current.isLoadingDiffReview || current.isSubmittingDiffComments) return
        val visit = conversationVisit
        _state.value = current.copy(isLoadingDiffReview = true, diffReviewError = null)
        viewModelScope.launch {
            api.fetchConversationDiffs(id).fold(
                onSuccess = { review ->
                    if (conversationVisit != visit || _state.value.selectedConversationId != id) return@fold
                    val available = review.files.mapTo(hashSetOf(), com.antigravity.mobile.data.model.ConversationDiffFile::fileUri)
                    val draft = _state.value.diffCommentDraft
                    _state.value = _state.value.copy(
                        diffReview = review,
                        isLoadingDiffReview = false,
                        diffReviewError = null,
                        diffCommentDraft = draft.copy(
                            comments = draft.comments.filter { it.fileUri in available },
                            activeFileUri = draft.activeFileUri?.takeIf { it in available },
                            activeSide = draft.activeSide.takeIf { draft.activeFileUri in available },
                            activeSelection = draft.activeSelection.takeIf { draft.activeFileUri in available },
                            activeComment = draft.activeComment.takeIf { draft.activeFileUri in available }.orEmpty(),
                        ),
                    )
                },
                onFailure = { error ->
                    if (conversationVisit != visit || _state.value.selectedConversationId != id) return@fold
                    if (error is GatewayAuthorizationException) { expirePairing(); return@fold }
                    _state.value = _state.value.copy(
                        isLoadingDiffReview = false,
                        diffReviewError = error.message ?: "读取当前变更失败",
                    )
                },
            )
        }
    }

    fun selectDiffComment(fileUri: String, side: DiffSide, selection: ArtifactTextSelection) {
        val current = _state.value
        if (fileUri.isBlank() || selection.content.isBlank() || selection.startLine < 0 ||
            selection.endLine <= selection.startLine || current.diffReview?.files?.none { it.fileUri == fileUri } != false
        ) return
        if (current.diffCommentDraft.comments.size >= 20) {
            _state.value = current.copy(diffReviewError = "每次最多提交 20 条评论")
            return
        }
        _state.value = current.copy(
            diffCommentDraft = current.diffCommentDraft.copy(
                activeFileUri = fileUri,
                activeSide = side,
                activeSelection = selection,
            ),
            diffReviewError = null,
        )
    }

    fun setDiffComment(value: String) {
        _state.value = _state.value.copy(
            diffCommentDraft = _state.value.diffCommentDraft.copy(activeComment = value.take(4_000)),
            diffReviewError = null,
        )
    }

    fun addDiffComment() {
        val current = _state.value
        val draft = current.diffCommentDraft
        val fileUri = draft.activeFileUri ?: return
        val side = draft.activeSide ?: return
        val selection = draft.activeSelection ?: return
        val comment = draft.activeComment.trim()
        if (comment.isEmpty()) {
            _state.value = current.copy(diffReviewError = "请填写评论内容")
            return
        }
        if (draft.comments.size >= 20) {
            _state.value = current.copy(diffReviewError = "每次最多提交 20 条评论")
            return
        }
        _state.value = current.copy(
            diffCommentDraft = draft.copy(
                comments = draft.comments + DiffCommentDraft(fileUri, side, selection, comment),
                activeFileUri = null, activeSide = null, activeSelection = null, activeComment = "",
            ),
            diffReviewError = null,
        )
    }

    fun removeDiffComment(index: Int) {
        val current = _state.value
        if (index !in current.diffCommentDraft.comments.indices || current.isSubmittingDiffComments) return
        _state.value = current.copy(diffCommentDraft = current.diffCommentDraft.copy(
            comments = current.diffCommentDraft.comments.filterIndexed { itemIndex, _ -> itemIndex != index }),
            diffReviewError = null)
    }

    fun clearDiffCommentSelection() {
        val current = _state.value
        if (current.isSubmittingDiffComments) return
        _state.value = current.copy(diffCommentDraft = current.diffCommentDraft.copy(
            activeFileUri = null, activeSide = null, activeSelection = null, activeComment = ""),
            diffReviewError = null)
    }

    fun submitDiffComments() {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        if (current.isSubmittingDiffComments || current.isSending) return
        val draft = current.diffCommentDraft
        val comments = if (draft.activeFileUri != null && draft.activeSide != null &&
            draft.activeSelection != null && draft.activeComment.isNotBlank()
        ) draft.comments + DiffCommentDraft(draft.activeFileUri, draft.activeSide,
            draft.activeSelection, draft.activeComment.trim()) else draft.comments
        if (comments.isEmpty()) {
            _state.value = current.copy(diffReviewError = "请先点选红色或绿色变更行并填写评论")
            return
        }
        val visit = conversationVisit
        val model = current.modelOverrideId?.let { selected ->
            current.models.firstOrNull { it.id == selected || it.model == selected }?.model
        }
        _state.value = current.copy(isSubmittingDiffComments = true, diffReviewError = null)
        viewModelScope.launch {
            api.submitDiffComments(id, comments, model).fold(
                onSuccess = {
                    if (conversationVisit != visit || _state.value.selectedConversationId != id) return@fold
                    val latest = _state.value
                    _state.value = latest.copy(
                        diffCommentDraft = DiffCommentDraftState(),
                        isSubmittingDiffComments = false,
                        diffReviewError = null,
                        diffCommentSuccessCount = latest.diffCommentSuccessCount + 1,
                        isRunning = true,
                    )
                    loadMessages(id)
                },
                onFailure = { error ->
                    if (conversationVisit != visit || _state.value.selectedConversationId != id) return@fold
                    _state.value = _state.value.copy(
                        isSubmittingDiffComments = false,
                        diffReviewError = error.message ?: "变更评论提交失败",
                    )
                },
            )
        }
    }

    private fun currentComposerContext() = DraftComposerContext(_state.value.workspaceContexts, _state.value.slashCommand)

    private fun saveComposerContext(key: String, value: DraftComposerContext = currentComposerContext()) {
        prefs.saveDraftComposerContext(key, value)
    }

    private fun switchDraft(key: String) {
        prefs.setDraftText(draftKey, _state.value.draft)
        if (!_state.value.isRestoringDraft) draftAttachments[draftKey] = _state.value.attachments
        if (!_state.value.isRestoringDraft) draftFiles[draftKey] = _state.value.fileAttachment
        saveComposerContext(draftKey)
        draftRestoreJob?.cancel()
        draftKey = key
        val context = prefs.loadDraftComposerContext(key)
        _state.value = _state.value.copy(
            draft = prefs.getDraftText(key),
            attachments = draftAttachments[key].orEmpty(),
            fileAttachment = draftFiles[key],
            workspaceContexts = context.workspaceItems,
            slashCommand = context.slashCommand,
            contextSuggestions = emptyList(), commandSuggestions = emptyList(),
            contextSuggestionsError = null, commandSuggestionsError = null,
        )
        restoreDraftAttachments(key)
    }

    private fun restoreDraftAttachments(key: String) {
        val needsImages = key !in draftAttachments && prefs.draftImageCount(key) > 0
        val needsFile = key !in draftFiles && prefs.loadDraftFile(key) != null
        _state.value = _state.value.copy(isRestoringDraft = needsImages || needsFile)
        if (_state.value.isRestoringDraft) draftRestoreJob = viewModelScope.launch {
            val (images, fileAttachment) = withContext(Dispatchers.IO) {
                val restoredImages = prefs.loadDraftImages(key).mapNotNull { file ->
                    try { PendingImage(Uri.fromFile(file), file.readBytes(),
                        checkNotNull(MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension))) }
                    catch (_: Exception) { null }
                }
                val restoredFile = prefs.loadDraftFile(key)?.let { stored ->
                    try { PendingFile(Uri.fromFile(stored.file), stored.file.readBytes(), stored.mimeType, stored.displayName, stored.size) }
                    catch (_: Exception) { null }
                }
                restoredImages to restoredFile
            }
            if (draftKey != key) return@launch
            draftAttachments[key] = images
            draftFiles[key] = fileAttachment
            _state.value = _state.value.copy(attachments = images, fileAttachment = fileAttachment, isRestoringDraft = false,
                error = when {
                    images.size < prefs.draftImageCount(key) -> "部分图片草稿无法读取，请重新选择图片"
                    needsFile && fileAttachment == null -> "文件草稿无法读取，请重新选择文件"
                    else -> _state.value.error
                })
        }
    }

    private fun saveImages(key: String, images: List<PendingImage>) {
        draftAttachments[key] = images
        if (images.isEmpty()) prefs.clearDraftImages(key)
        else prefs.saveDraftImages(key, images.map { File(checkNotNull(it.uri.path)) })
    }

    private fun saveFile(key: String, file: PendingFile?) {
        draftFiles[key] = file
        prefs.saveDraftFile(key, file?.let { StoredDraftFile(File(checkNotNull(it.uri.path)), it.displayName, it.mimeType, it.size) })
    }

    private fun clearSendSnapshot() {
        prefs.pendingSendTarget = null
        prefs.clearDraftText(PENDING_SEND_DRAFT)
        prefs.clearDraftImages(PENDING_SEND_DRAFT)
        prefs.clearDraftFile(PENDING_SEND_DRAFT)
        prefs.clearDraftComposerContext(PENDING_SEND_DRAFT)
    }

    private fun moveDraft(from: String, to: String) {
        val active = draftKey == from
        val sourceText = if (active) _state.value.draft else prefs.getDraftText(from)
        val targetText = if (draftKey == to) _state.value.draft else prefs.getDraftText(to)
        val text = listOf(sourceText, targetText).filter { it.isNotEmpty() }.joinToString("\n\n")
        val sourceImages = if (active) _state.value.attachments else draftAttachments[from].orEmpty()
        val targetImages = if (draftKey == to) _state.value.attachments else draftAttachments[to].orEmpty()
        val images = (sourceImages + targetImages).distinctBy { it.uri }
        val sourceFile = if (active) _state.value.fileAttachment else draftFiles[from] ?: prefs.loadDraftFile(from)?.toPendingFile()
        val targetFile = if (draftKey == to) _state.value.fileAttachment else draftFiles[to] ?: prefs.loadDraftFile(to)?.toPendingFile()
        val file = sourceFile ?: targetFile
        val context = mergeComposerContext(
            if (active) currentComposerContext() else prefs.loadDraftComposerContext(from),
            if (draftKey == to) currentComposerContext() else prefs.loadDraftComposerContext(to),
        )
        prefs.setDraftText(to, text)
        saveImages(to, images)
        saveFile(to, file)
        saveComposerContext(to, context)
        prefs.clearDraftText(from)
        prefs.clearDraftImages(from)
        prefs.clearDraftFile(from)
        prefs.clearDraftComposerContext(from)
        draftAttachments.remove(from)
        draftFiles.remove(from)
        if (active) draftKey = to
        if (draftKey == to) _state.value = _state.value.copy(draft = text, attachments = images, fileAttachment = file,
            workspaceContexts = context.workspaceItems, slashCommand = context.slashCommand)
    }

    private fun StoredDraftFile.toPendingFile() = try {
        PendingFile(Uri.fromFile(file), file.readBytes(), mimeType, displayName, size)
    } catch (_: Exception) { null }

    private fun PendingFile.toStoredDraftFile() =
        StoredDraftFile(File(checkNotNull(uri.path)), displayName, mimeType, size)

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

    fun addFile(uri: Uri) {
        val key = draftKey
        val model = selectedModel()
        if (model == null) {
            _state.value = _state.value.copy(error = _state.value.modelsError ?: "请等待模型列表加载完成后重试")
            refreshModels()
            return
        }
        if (_state.value.fileAttachment != null || prefs.loadDraftFile(PENDING_SEND_DRAFT) != null) {
            _state.value = _state.value.copy(error = "一次最多添加 1 个文件，请先发送或移除当前文件")
            return
        }
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { readFile(uri, model.supportedMimeTypes) }
                if (draftKey != key || _state.value.fileAttachment != null) {
                    prefs.deleteUnusedDraftFile(File(checkNotNull(file.uri.path)))
                    return@launch
                }
                _state.value = _state.value.copy(fileAttachment = file)
                saveFile(key, file)
            } catch (error: Exception) {
                if (draftKey == key) _state.value = _state.value.copy(error = error.message ?: "读取文件失败")
            }
        }
    }

    fun removeFile() {
        val file = _state.value.fileAttachment ?: return
        _state.value = _state.value.copy(fileAttachment = null)
        saveFile(draftKey, null)
        file.uri.path?.let { prefs.deleteUnusedDraftFile(File(it)) }
    }

    fun searchWorkspaceContexts(query: String) {
        contextSuggestionsJob?.cancel()
        val workspaceUri = _state.value.workspaceUri
        if (workspaceUri.isBlank()) {
            _state.value = _state.value.copy(contextSuggestions = emptyList(), isLoadingContextSuggestions = false,
                contextSuggestionsError = "当前会话没有关联电脑工作区")
            return
        }
        _state.value = _state.value.copy(isLoadingContextSuggestions = true, contextSuggestionsError = null)
        val token = prefs.deviceToken
        contextSuggestionsJob = viewModelScope.launch {
            delay(180)
            api.fetchWorkspaceContext(workspaceUri, query).fold(onSuccess = { items ->
                if (prefs.deviceToken == token) _state.value = _state.value.copy(
                    contextSuggestions = items.filterNot { item -> _state.value.workspaceContexts.any { it.uri == item.uri } },
                    isLoadingContextSuggestions = false, contextSuggestionsError = null)
            }, onFailure = { error ->
                if (prefs.deviceToken != token) return@fold
                if (error is GatewayAuthorizationException) expirePairing()
                else _state.value = _state.value.copy(contextSuggestions = emptyList(), isLoadingContextSuggestions = false,
                    contextSuggestionsError = error.message ?: "搜索电脑文件失败")
            })
        }
    }

    fun searchSlashCommands(query: String) {
        commandSuggestionsJob?.cancel()
        val current = _state.value
        val model = current.models.firstOrNull {
            it.id == (current.modelOverrideId ?: current.activeModelId ?: current.selectedModelId) ||
                it.model == (current.modelOverrideId ?: current.activeModelId ?: current.selectedModelId)
        }
        if (model == null) {
            _state.value = current.copy(commandSuggestions = emptyList(), isLoadingCommandSuggestions = false,
                commandSuggestionsError = current.modelsError ?: "请等待模型列表加载完成")
            return
        }
        _state.value = current.copy(isLoadingCommandSuggestions = true, commandSuggestionsError = null)
        val token = prefs.deviceToken
        commandSuggestionsJob = viewModelScope.launch {
            delay(180)
            api.fetchSlashCommands(current.selectedConversationId, current.workspaceUri, model.model).fold(onSuccess = { commands ->
                if (prefs.deviceToken != token) return@fold
                val needle = query.trim().lowercase()
                _state.value = _state.value.copy(commandSuggestions = commands.filter {
                    needle.isBlank() || it.info.name.lowercase().contains(needle) ||
                        it.title.lowercase().contains(needle) || it.description.lowercase().contains(needle)
                }.take(30), isLoadingCommandSuggestions = false, commandSuggestionsError = null)
            }, onFailure = { error ->
                if (prefs.deviceToken != token) return@fold
                if (error is GatewayAuthorizationException) expirePairing()
                else _state.value = _state.value.copy(commandSuggestions = emptyList(), isLoadingCommandSuggestions = false,
                    commandSuggestionsError = error.message ?: "获取命令失败")
            })
        }
    }

    fun addWorkspaceContext(item: WorkspaceContextItem) {
        val current = _state.value
        if (current.workspaceContexts.any { it.uri == item.uri }) return
        if (current.workspaceContexts.size >= 8) {
            _state.value = current.copy(error = "一次最多引用 8 个电脑文件或目录")
            return
        }
        _state.value = current.copy(workspaceContexts = current.workspaceContexts + item,
            contextSuggestions = current.contextSuggestions.filterNot { it.uri == item.uri })
        saveComposerContext(draftKey)
    }

    fun removeWorkspaceContext(uri: String) {
        _state.value = _state.value.copy(workspaceContexts = _state.value.workspaceContexts.filterNot { it.uri == uri })
        saveComposerContext(draftKey)
    }

    fun selectSlashCommand(command: SlashCommandDefinition) {
        // shortcut: the current desktop catalog has no free-form arguments; add an editor when that changes.
        if (command.arguments.any { it.required && it.defaultValue.isBlank() }) {
            _state.value = _state.value.copy(error = "/${command.info.name} 需要填写参数，手机端暂不能执行")
            return
        }
        _state.value = _state.value.copy(slashCommand = command, commandSuggestions = emptyList())
        saveComposerContext(draftKey)
    }

    fun removeSlashCommand() {
        _state.value = _state.value.copy(slashCommand = null)
        saveComposerContext(draftKey)
    }

    fun loadPlanReviewPreference() {
        if (_state.value.isLoadingPlanReviewPreference) return
        val token = prefs.deviceToken
        _state.value = _state.value.copy(isLoadingPlanReviewPreference = true, workModeError = null)
        viewModelScope.launch {
            api.fetchPlanReviewPreference().fold(onSuccess = { preference ->
                if (prefs.deviceToken == token) _state.value = _state.value.copy(
                    planReviewPreference = preference,
                    isLoadingPlanReviewPreference = false,
                    workModeError = null,
                )
            }, onFailure = { error ->
                if (prefs.deviceToken != token) return@fold
                if (error is GatewayAuthorizationException) expirePairing()
                else _state.value = _state.value.copy(
                    isLoadingPlanReviewPreference = false,
                    workModeError = error.message ?: "读取计划审阅策略失败",
                )
            })
        }
    }

    fun updatePlanReviewPolicy(policy: String) {
        if (policy !in setOf("ALWAYS", "TURBO") || _state.value.isUpdatingPlanReviewPreference) return
        val token = prefs.deviceToken
        _state.value = _state.value.copy(isUpdatingPlanReviewPreference = true, workModeError = null)
        viewModelScope.launch {
            api.updatePlanReviewPreference(policy).fold(onSuccess = { preference ->
                if (prefs.deviceToken == token) {
                    val current = _state.value
                    _state.value = current.copy(
                        planReviewPreference = preference,
                        isUpdatingPlanReviewPreference = false,
                        workModeError = null,
                        planReviewPolicySuccessCount = current.planReviewPolicySuccessCount + 1,
                    )
                }
            }, onFailure = { error ->
                if (prefs.deviceToken != token) return@fold
                if (error is GatewayAuthorizationException) expirePairing()
                else _state.value = _state.value.copy(
                    isUpdatingPlanReviewPreference = false,
                    workModeError = error.message ?: "更新计划审阅策略失败",
                )
            })
        }
    }

    fun selectPlanningMode(enabled: Boolean) {
        val current = _state.value
        if (!enabled) {
            if (current.slashCommand?.info?.name.equals("plan", ignoreCase = true)) {
                _state.value = current.copy(slashCommand = null, workModeError = null)
                saveComposerContext(draftKey)
            } else {
                _state.value = current.copy(workModeError = null)
            }
            return
        }
        if (current.slashCommand?.info?.name.equals("plan", ignoreCase = true) || current.isSelectingWorkMode) return
        val model = current.models.firstOrNull {
            it.id == (current.modelOverrideId ?: current.activeModelId ?: current.selectedModelId) ||
                it.model == (current.modelOverrideId ?: current.activeModelId ?: current.selectedModelId)
        }
        if (model == null) {
            _state.value = current.copy(workModeError = current.modelsError ?: "请等待模型列表加载完成")
            return
        }
        val token = prefs.deviceToken
        val visit = conversationVisit
        val conversationId = current.selectedConversationId
        val workspaceUri = current.workspaceUri
        val key = draftKey
        _state.value = current.copy(isSelectingWorkMode = true, workModeError = null)
        viewModelScope.launch {
            api.fetchSlashCommands(conversationId, workspaceUri, model.model).fold(onSuccess = { commands ->
                if (prefs.deviceToken != token || conversationVisit != visit ||
                    _state.value.selectedConversationId != conversationId || draftKey != key) return@fold
                val command = commands.firstOrNull { it.info.name.equals("plan", ignoreCase = true) }
                when {
                    command == null -> _state.value = _state.value.copy(
                        isSelectingWorkMode = false,
                        workModeError = "电脑端当前没有提供 /plan 命令",
                    )
                    command.arguments.any { it.required && it.defaultValue.isBlank() } ->
                        _state.value = _state.value.copy(
                            isSelectingWorkMode = false,
                            workModeError = "/plan 需要填写参数，手机端暂不能执行",
                        )
                    else -> {
                        _state.value = _state.value.copy(
                            slashCommand = command,
                            commandSuggestions = emptyList(),
                            isSelectingWorkMode = false,
                            workModeError = null,
                        )
                        saveComposerContext(key)
                    }
                }
            }, onFailure = { error ->
                if (prefs.deviceToken != token || conversationVisit != visit ||
                    _state.value.selectedConversationId != conversationId || draftKey != key) return@fold
                if (error is GatewayAuthorizationException) expirePairing()
                else _state.value = _state.value.copy(
                    isSelectingWorkMode = false,
                    workModeError = error.message ?: "读取 /plan 命令失败",
                )
            })
        }
    }

    fun clearComposerSuggestions() {
        contextSuggestionsJob?.cancel()
        commandSuggestionsJob?.cancel()
        _state.value = _state.value.copy(contextSuggestions = emptyList(), commandSuggestions = emptyList(),
            isLoadingContextSuggestions = false, isLoadingCommandSuggestions = false,
            contextSuggestionsError = null, commandSuggestionsError = null)
    }

    private fun selectedModel(): ChatModel? {
        val state = _state.value
        val modelId = state.modelOverrideId ?: state.activeModelId ?: state.selectedModelId
        return state.models.firstOrNull { it.id == modelId || it.model == modelId }
    }

    private fun readFile(uri: Uri, supportedMimeTypes: Set<String>): PendingFile {
        val resolver = getApplication<Application>().contentResolver
        var displayName = ""
        var reportedSize: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                displayName = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }
                    ?.let(cursor::getString).orEmpty()
                reportedSize = cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let(cursor::getLong)
            }
        }
        if (displayName.isBlank()) displayName = uri.lastPathSegment?.substringAfterLast('/')?.take(255).orEmpty()
        require(displayName.isNotBlank()) { "无法读取文件名" }
        require((reportedSize ?: 1L) in 1..8L * 1024 * 1024) { "单个文件不能超过 8 MB" }
        val extensionMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(displayName.substringAfterLast('.', "").lowercase())
        val reportedMime = resolver.getType(uri)?.substringBefore(';')?.lowercase()
        val mime = reportedMime?.takeUnless { it == "application/octet-stream" } ?: extensionMime ?: reportedMime
            ?: throw IllegalArgumentException("无法识别文件格式，请选择带有标准扩展名的文件")
        require(supportedMimeTypes.isNotEmpty()) { "当前模型没有公布文件格式能力，请切换模型后重试" }
        require(mime in supportedMimeTypes) { "当前模型不支持 $mime 文件，请切换模型或选择其他文件" }
        val input = resolver.openInputStream(uri) ?: throw IllegalArgumentException("无法读取所选文件")
        val output = ByteArrayOutputStream()
        input.use { source ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                if (output.size() + count > 8 * 1024 * 1024) throw IllegalArgumentException("单个文件不能超过 8 MB")
                output.write(buffer, 0, count)
            }
        }
        val bytes = output.toByteArray()
        require(bytes.isNotEmpty()) { "不能发送空文件" }
        val stored = prefs.storeDraftFile(bytes, displayName.take(255), mime)
        return PendingFile(Uri.fromFile(stored.file), bytes, mime, stored.displayName, stored.size)
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
        subagentsJob?.cancel(); subagentsJob = null
        subagentTrail.clear()
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
            isLoadingMessages = false, canProceed = false, proceedArtifactUri = null,
            isProceedingArtifact = false, proceedError = null,
            planCommentDraft = ArtifactCommentDraftState(), isSubmittingPlanComments = false, planCommentError = null,
            diffReview = null, isLoadingDiffReview = false, diffReviewError = null,
            diffCommentDraft = DiffCommentDraftState(), isSubmittingDiffComments = false,
            isSubmittingInteraction = false,
            pendingInteraction = null, interactionError = null, queuedMessages = emptyList(),
            busyQueuedMessageIds = emptySet(), queueError = null,
            runningTasks = emptyList(), stoppingTaskIds = emptySet(), tasksError = null,
            subagents = emptyList(), activeSubagent = null, isLoadingSubagents = false, subagentsError = null,
            planReviewPreference = null, isLoadingPlanReviewPreference = false,
            isUpdatingPlanReviewPreference = false, isSelectingWorkMode = false, workModeError = null,
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
                        workspaceUri = conversations.firstOrNull { it.id == _state.value.selectedConversationId }
                            ?.workspaceUri?.takeIf(String::isNotBlank) ?: _state.value.workspaceUri,
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
                prefs.cachedModelsJson = Json.encodeToString(models)
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
        if (current.fileAttachment?.mimeType?.let { it !in model.supportedMimeTypes } == true) {
            _state.value = current.copy(error = "这个模型不支持当前文件，请先移除文件")
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
                    onCreated(); openConversationTarget(id, null, project.uri)
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
        subagentTrail.clear()
        openConversationTarget(id, null)
    }

    fun openSubagent(subagent: SubagentItem) {
        if (subagent.parentConversationId != _state.value.selectedConversationId) return
        subagentTrail += subagent
        openConversationTarget(subagent.id, subagent, _state.value.workspaceUri)
    }

    fun returnToSubagentParent() {
        val current = subagentTrail.removeLastOrNull() ?: return
        openConversationTarget(current.parentConversationId, subagentTrail.lastOrNull(), _state.value.workspaceUri)
    }

    fun refreshSubagents() {
        val id = _state.value.selectedConversationId ?: return
        subagentsJob?.cancel()
        startSubagentPolling(id, conversationVisit)
    }

    private fun openConversationTarget(id: String, activeSubagent: SubagentItem?, workspaceUri: String? = null) {
        conversationVisit++
        messagesJob?.cancel(); messagesJob = null
        subagentsJob?.cancel(); subagentsJob = null
        switchDraft(id)
        prefs.lastConversationId = (subagentTrail.firstOrNull()?.parentConversationId ?: id).takeUnless { it.startsWith("local:") }
        stream.disconnect()
        _state.value = _state.value.copy(
            selectedConversationId = id,
            workspaceUri = workspaceUri ?: _state.value.conversations.firstOrNull { it.id == id }?.workspaceUri.orEmpty(),
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
            interactionError = null,
            queuedMessages = emptyList(), busyQueuedMessageIds = emptySet(), queueError = null,
            runningTasks = emptyList(), stoppingTaskIds = emptySet(), tasksError = null,
            subagents = emptyList(), activeSubagent = activeSubagent, isLoadingSubagents = true, subagentsError = null,
            canProceed = false, proceedArtifactUri = null, isProceedingArtifact = false, proceedError = null,
            planCommentDraft = ArtifactCommentDraftState(), isSubmittingPlanComments = false, planCommentError = null,
            diffReview = null, isLoadingDiffReview = false, diffReviewError = null,
            diffCommentDraft = DiffCommentDraftState(), isSubmittingDiffComments = false,
            hasMoreMessages = false,
            nextMessageOffset = 0,
            error = null,
            messagesError = null, olderMessagesError = null,
            isSelectingWorkMode = false, workModeError = null,
        )
        stream.connect(id)
        loadMessages(id)
        api.notifySessionFocus(id)
        startSubagentPolling(id, conversationVisit)
    }

    private fun startSubagentPolling(parentId: String, visit: Long) {
        subagentsJob = viewModelScope.launch {
            loadSubagents(parentId, visit, showLoading = true)
            while (isActive) {
                delay(3_000)
                loadSubagents(parentId, visit, showLoading = false)
            }
        }
    }

    private suspend fun loadSubagents(parentId: String, visit: Long, showLoading: Boolean) {
        if (showLoading && conversationVisit == visit) {
            _state.value = _state.value.copy(isLoadingSubagents = true, subagentsError = null)
        }
        api.fetchSubagents(parentId).fold(
            onSuccess = { items ->
                if (conversationVisit == visit && _state.value.selectedConversationId == parentId) {
                    _state.value = _state.value.copy(subagents = items, isLoadingSubagents = false, subagentsError = null)
                }
            },
            onFailure = { error ->
                if (conversationVisit != visit) return@fold
                if (error is GatewayAuthorizationException) { expirePairing(); return@fold }
                _state.value = _state.value.copy(
                    isLoadingSubagents = false,
                    subagentsError = error.message ?: "加载子代理失败",
                )
            },
        )
    }

    fun newConversation() {
        closeConversation()
        switchDraft(NEW_CHAT_DRAFT)
        prefs.clearDraftText(NEW_CHAT_DRAFT)
        prefs.clearDraftImages(NEW_CHAT_DRAFT)
        prefs.clearDraftFile(NEW_CHAT_DRAFT)
        prefs.clearDraftComposerContext(NEW_CHAT_DRAFT)
        draftRestoreJob?.cancel()
        draftAttachments.remove(NEW_CHAT_DRAFT)
        draftFiles.remove(NEW_CHAT_DRAFT)
        _state.value = _state.value.copy(draft = "", attachments = emptyList(), fileAttachment = null,
            workspaceUri = "", workspaceContexts = emptyList(), slashCommand = null, isRestoringDraft = false)
    }

    fun closeConversation() {
        conversationVisit++
        messagesJob?.cancel(); messagesJob = null
        subagentsJob?.cancel(); subagentsJob = null
        subagentTrail.clear()
        stream.disconnect()
        prefs.lastConversationId = null
        switchDraft(NEW_CHAT_DRAFT)
        _state.value = _state.value.copy(
            selectedConversationId = null,
            workspaceUri = "",
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
            interactionError = null,
            queuedMessages = emptyList(), busyQueuedMessageIds = emptySet(), queueError = null,
            runningTasks = emptyList(), stoppingTaskIds = emptySet(), tasksError = null,
            subagents = emptyList(), activeSubagent = null, isLoadingSubagents = false, subagentsError = null,
            canProceed = false, proceedArtifactUri = null, isProceedingArtifact = false, proceedError = null,
            planCommentDraft = ArtifactCommentDraftState(), isSubmittingPlanComments = false, planCommentError = null,
            diffReview = null, isLoadingDiffReview = false, diffReviewError = null,
            diffCommentDraft = DiffCommentDraftState(), isSubmittingDiffComments = false,
            hasMoreMessages = false,
            nextMessageOffset = 0,
            error = null,
            messagesError = null, olderMessagesError = null,
            isSelectingWorkMode = false, workModeError = null,
        )
    }

    fun send() {
        if (_state.value.activeSubagent != null) return
        if (_state.value.isRestoringDraft) return
        val text = _state.value.draft.trim()
        val images = _state.value.attachments
        val file = _state.value.fileAttachment
        val contexts = _state.value.workspaceContexts
        val slashCommand = _state.value.slashCommand
        if ((text.isEmpty() && images.isEmpty() && file == null && contexts.isEmpty() && slashCommand == null) || _state.value.isSending || _state.value.isLoadingMessages || _state.value.isStopping || _state.value.isReverting || _state.value.selectedConversationId in _state.value.busyConversations) return
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
        if (file != null && (model == null || file.mimeType !in model.supportedMimeTypes)) {
            _state.value = _state.value.copy(error = "当前模型不支持 ${file.mimeType} 文件，请切换模型或移除文件")
            return
        }
        val requestedModel = model?.model.takeIf { id == null || _state.value.modelOverrideId != null }
        val token = prefs.deviceToken
        val visit = conversationVisit
        val clientId = UUID.randomUUID().toString()
        val wasRunning = _state.value.isRunning
        if (id != null && wasRunning) {
            queueCurrentMessage(id, text, images, file, contexts, slashCommand, requestedModel, model?.id, token, visit, clientId)
            return
        }
        var targetId = id ?: "local:$clientId"
        val originalDraftKey = draftKey
        val pending = OutgoingMessage(targetId,
            GatewayMessageItem(id = "local:$clientId", text = text, imageDataList = images.map { it.bytes },
                attachments = file?.let { listOf(MessageAttachment(it.mimeType, it.displayName)) }),
            _state.value.messages.filter { it.isUser }.map { it.id }.toSet(),
            _state.value.messages.mapNotNull { it.stepIndex }.maxOrNull(),
            images.isNotEmpty() || file != null)
        prefs.setDraftText(PENDING_SEND_DRAFT, text)
        prefs.saveDraftImages(PENDING_SEND_DRAFT, images.map { File(checkNotNull(it.uri.path)) })
        prefs.saveDraftFile(PENDING_SEND_DRAFT, file?.toStoredDraftFile())
        prefs.saveDraftComposerContext(PENDING_SEND_DRAFT, DraftComposerContext(contexts, slashCommand))
        prefs.pendingSendTarget = originalDraftKey
        setDraft("")
        saveImages(draftKey, emptyList())
        saveFile(draftKey, null)
        saveComposerContext(draftKey, DraftComposerContext())
        _state.value = _state.value.copy(attachments = emptyList(), fileAttachment = null,
            workspaceContexts = emptyList(), slashCommand = null)
        if (id == null) moveDraft(originalDraftKey, targetId)
        if (id == null) conversationVisit++
        _state.value = _state.value.copy(
            selectedConversationId = targetId, outgoing = _state.value.outgoing + pending,
            draft = "", attachments = emptyList(), fileAttachment = null, workspaceContexts = emptyList(), slashCommand = null,
            isSending = true, isRunning = true, streamingMessageId = null, error = null)
        viewModelScope.launch {
            val result = if (id == null && images.isEmpty() && file == null && contexts.isEmpty() && slashCommand == null) {
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
                                file = file?.let { Triple(it.bytes, it.mimeType, it.displayName) },
                                workspaceContexts = contexts,
                                slashCommand = slashCommand,
                                clientMessageId = clientId,
                            ).map { createdId }
                        },
                        onFailure = { Result.failure(it) },
                    )
            } else {
                api.sendMessage(
                    id, text, model = requestedModel, images = images.map { it.bytes to it.mimeType },
                    file = file?.let { Triple(it.bytes, it.mimeType, it.displayName) },
                    workspaceContexts = contexts,
                    slashCommand = slashCommand,
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
                    val restoredFile = if (unconfirmed) file ?: if (draftKey == restoreKey) current.fileAttachment else draftFiles[restoreKey] else current.fileAttachment
                    val restoredContext = if (unconfirmed) mergeComposerContext(DraftComposerContext(contexts, slashCommand),
                        if (draftKey == restoreKey) currentComposerContext() else prefs.loadDraftComposerContext(restoreKey))
                    else currentComposerContext()
                    if (unconfirmed) {
                        prefs.setDraftText(restoreKey, restoredDraft)
                        saveImages(restoreKey, restoredImages)
                        saveFile(restoreKey, restoredFile)
                        saveComposerContext(restoreKey, restoredContext)
                    }
                    _state.value = current.copy(
                        outgoing = current.outgoing.filterNot { it.message.id == pending.message.id },
                        selectedConversationId = if (stillHere && targetId.startsWith("local:")) null else current.selectedConversationId,
                        draft = if (draftKey == restoreKey && unconfirmed) restoredDraft else current.draft,
                        attachments = if (draftKey == restoreKey && unconfirmed) restoredImages else current.attachments,
                        fileAttachment = if (draftKey == restoreKey && unconfirmed) restoredFile else current.fileAttachment,
                        workspaceContexts = if (draftKey == restoreKey && unconfirmed) restoredContext.workspaceItems else current.workspaceContexts,
                        slashCommand = if (draftKey == restoreKey && unconfirmed) restoredContext.slashCommand else current.slashCommand,
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

    private fun queueCurrentMessage(
        id: String,
        text: String,
        images: List<PendingImage>,
        file: PendingFile?,
        contexts: List<WorkspaceContextItem>,
        slashCommand: SlashCommandDefinition?,
        requestedModel: String?,
        selectedModelId: String?,
        token: String?,
        visit: Long,
        clientId: String,
    ) {
        val key = draftKey
        prefs.setDraftText(PENDING_SEND_DRAFT, text)
        prefs.saveDraftImages(PENDING_SEND_DRAFT, images.map { File(checkNotNull(it.uri.path)) })
        prefs.saveDraftFile(PENDING_SEND_DRAFT, file?.toStoredDraftFile())
        prefs.saveDraftComposerContext(PENDING_SEND_DRAFT, DraftComposerContext(contexts, slashCommand))
        prefs.pendingSendTarget = key
        setDraft("")
        saveImages(key, emptyList())
        saveFile(key, null)
        saveComposerContext(key, DraftComposerContext())
        _state.value = _state.value.copy(
            draft = "",
            attachments = emptyList(),
            fileAttachment = null,
            workspaceContexts = emptyList(),
            slashCommand = null,
            isSending = true,
            queueError = null,
        )
        viewModelScope.launch {
            api.sendMessage(
                id,
                text,
                model = requestedModel,
                images = images.map { it.bytes to it.mimeType },
                file = file?.let { Triple(it.bytes, it.mimeType, it.displayName) },
                workspaceContexts = contexts,
                slashCommand = slashCommand,
                deliveryStrategy = 2,
                clientMessageId = clientId,
            ).fold(
                onSuccess = {
                    if (prefs.deviceToken != token) return@fold
                    if (_state.value.selectedConversationId == id && conversationVisit == visit) {
                        _state.value = _state.value.copy(
                            isSending = false,
                            activeModelId = selectedModelId ?: _state.value.activeModelId,
                            modelOverrideId = null,
                        )
                        loadMessages(id)
                    }
                },
                onFailure = { error ->
                    if (prefs.deviceToken != token) return@fold
                    val currentText = if (draftKey == key) _state.value.draft else prefs.getDraftText(key)
                    val restoredText = listOf(text, currentText).filter { it.isNotBlank() }.joinToString("\n\n")
                    val currentImages = if (draftKey == key) _state.value.attachments else draftAttachments[key].orEmpty()
                    val restoredImages = (images + currentImages).distinctBy { it.uri }
                    val restoredFile = file ?: if (draftKey == key) _state.value.fileAttachment else draftFiles[key]
                    val restoredContext = mergeComposerContext(DraftComposerContext(contexts, slashCommand),
                        if (draftKey == key) currentComposerContext() else prefs.loadDraftComposerContext(key))
                    prefs.setDraftText(key, restoredText)
                    saveImages(key, restoredImages)
                    saveFile(key, restoredFile)
                    saveComposerContext(key, restoredContext)
                    if (_state.value.selectedConversationId == id && conversationVisit == visit) {
                        _state.value = _state.value.copy(
                            draft = restoredText,
                            attachments = restoredImages,
                            fileAttachment = restoredFile,
                            workspaceContexts = restoredContext.workspaceItems,
                            slashCommand = restoredContext.slashCommand,
                            isSending = false,
                            queueError = "${error.message ?: "加入队列失败"}（内容已恢复到输入框）",
                        )
                    }
                },
            )
            clearSendSnapshot()
        }
    }

    fun deleteQueuedMessage(message: QueuedMessageItem) {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        if (message.id.isBlank() || message.id in current.busyQueuedMessageIds) return
        val visit = conversationVisit
        _state.value = current.copy(
            busyQueuedMessageIds = current.busyQueuedMessageIds + message.id,
            queueError = null,
        )
        viewModelScope.launch {
            api.deleteAgentMessage(id, message.id).fold(
                onSuccess = {
                    if (conversationVisit == visit && _state.value.selectedConversationId == id) {
                        _state.value = _state.value.copy(
                            queuedMessages = _state.value.queuedMessages.filterNot { it.id == message.id },
                            busyQueuedMessageIds = _state.value.busyQueuedMessageIds - message.id,
                            queueError = null,
                        )
                    }
                },
                onFailure = { error ->
                    if (conversationVisit == visit && _state.value.selectedConversationId == id) {
                        _state.value = _state.value.copy(
                            busyQueuedMessageIds = _state.value.busyQueuedMessageIds - message.id,
                            queueError = error.message ?: "删除排队消息失败",
                        )
                    }
                },
            )
        }
    }

    fun stopRunningTask(task: RunningTaskItem) {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        val taskId = task.stableId
        if (taskId in current.stoppingTaskIds || task !in current.runningTasks) return
        val visit = conversationVisit
        _state.value = current.copy(
            stoppingTaskIds = current.stoppingTaskIds + taskId,
            tasksError = null,
        )
        viewModelScope.launch {
            api.stopTask(id, taskId, task.stepIndex).fold(
                onSuccess = {
                    if (conversationVisit == visit && _state.value.selectedConversationId == id) {
                        _state.value = _state.value.copy(
                            runningTasks = _state.value.runningTasks.filterNot { it.stableId == taskId },
                            stoppingTaskIds = _state.value.stoppingTaskIds - taskId,
                            tasksError = null,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is GatewayAuthorizationException) {
                        expirePairing()
                    } else if (conversationVisit == visit && _state.value.selectedConversationId == id) {
                        _state.value = _state.value.copy(
                            stoppingTaskIds = _state.value.stoppingTaskIds - taskId,
                            tasksError = error.message ?: "停止后台任务失败",
                        )
                    }
                },
            )
        }
    }

    fun editQueuedMessage(message: QueuedMessageItem) {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        if (message.id.isBlank() || message.id in current.busyQueuedMessageIds) return
        val visit = conversationVisit
        _state.value = current.copy(
            busyQueuedMessageIds = current.busyQueuedMessageIds + message.id,
            queueError = null,
        )
        viewModelScope.launch {
            val stagedImages = mutableListOf<File>()
            val stagedDocuments = mutableListOf<File>()
            var keepStagedFiles = false
            var deletedUpstream = false
            var staged = StagedQueuedAttachments()
            try {
                staged = stageQueuedAttachments(message, id, stagedImages, stagedDocuments)
                if (conversationVisit != visit || _state.value.selectedConversationId != id) return@launch
                check(staged.images.size + _state.value.attachments.size <= 4) { "输入框最多保留 4 张图片，请先发送或移除部分图片" }
                check(staged.file == null || _state.value.fileAttachment == null) { "输入框已有文件，请先发送或移除后再编辑这条消息" }
                api.deleteAgentMessage(id, message.id).getOrThrow()
                deletedUpstream = true
                val latest = _state.value
                val targetDraft = if (draftKey == id) latest.draft else prefs.getDraftText(id)
                val targetImages = if (draftKey == id) latest.attachments else draftAttachments[id].orEmpty()
                val targetFile = if (draftKey == id) latest.fileAttachment else draftFiles[id]
                val text = listOf(message.text, targetDraft).filter { it.isNotBlank() }.joinToString("\n\n")
                val images = (staged.images + targetImages).distinctBy { it.uri }
                val file = staged.file ?: targetFile
                check(images.size <= 4) { "输入框最多保留 4 张图片" }
                prefs.setDraftText(id, text)
                saveImages(id, images)
                saveFile(id, file)
                keepStagedFiles = true
                if (latest.selectedConversationId == id && draftKey == id) {
                    _state.value = latest.copy(
                        queuedMessages = latest.queuedMessages.filterNot { it.id == message.id },
                        busyQueuedMessageIds = latest.busyQueuedMessageIds - message.id,
                        queueError = null,
                        draft = text,
                        attachments = images,
                        fileAttachment = file,
                    )
                }
            } catch (error: Exception) {
                val latest = _state.value
                if (deletedUpstream) {
                    val targetDraft = if (draftKey == id) latest.draft else prefs.getDraftText(id)
                    val targetImages = if (draftKey == id) latest.attachments else draftAttachments[id].orEmpty()
                    val targetFile = if (draftKey == id) latest.fileAttachment else draftFiles[id]
                    val text = listOf(message.text, targetDraft).filter { it.isNotBlank() }.joinToString("\n\n")
                    val restored = (staged.images + targetImages).distinctBy { it.uri }
                    val file = staged.file ?: targetFile
                    prefs.setDraftText(id, text)
                    saveImages(id, restored)
                    saveFile(id, file)
                    keepStagedFiles = true
                    if (latest.selectedConversationId == id && draftKey == id) {
                        _state.value = latest.copy(
                            queuedMessages = latest.queuedMessages.filterNot { it.id == message.id },
                            busyQueuedMessageIds = latest.busyQueuedMessageIds - message.id,
                            queueError = "${error.message ?: "编辑排队消息失败"}（内容已恢复到输入框）",
                            draft = text,
                            attachments = restored,
                            fileAttachment = file,
                        )
                    }
                } else if (conversationVisit == visit && latest.selectedConversationId == id) {
                    _state.value = latest.copy(
                        busyQueuedMessageIds = latest.busyQueuedMessageIds - message.id,
                        queueError = error.message ?: "编辑排队消息失败",
                    )
                }
            } finally {
                if (!keepStagedFiles) {
                    stagedImages.forEach(prefs::deleteUnusedDraftImage)
                    stagedDocuments.forEach(prefs::deleteUnusedDraftFile)
                }
            }
        }
    }

    fun sendQueuedMessageNow(message: QueuedMessageItem) {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        if (message.id.isBlank() || message.id in current.busyQueuedMessageIds) return
        val visit = conversationVisit
        _state.value = current.copy(
            busyQueuedMessageIds = current.busyQueuedMessageIds + message.id,
            queueError = null,
        )
        viewModelScope.launch {
            val stagedImages = mutableListOf<File>()
            val stagedDocuments = mutableListOf<File>()
            var keepStagedFiles = false
            var deletedUpstream = false
            var staged = StagedQueuedAttachments()
            try {
                staged = stageQueuedAttachments(message, id, stagedImages, stagedDocuments)
                if (conversationVisit != visit || _state.value.selectedConversationId != id) return@launch
                check(staged.images.size + _state.value.attachments.size <= 4) { "输入框图片较多，无法在失败时完整恢复；请先发送或移除部分图片" }
                check(staged.file == null || _state.value.fileAttachment == null) { "输入框已有文件，无法在失败时完整恢复；请先发送或移除当前文件" }
                api.deleteAgentMessage(id, message.id).getOrThrow()
                deletedUpstream = true
                api.sendMessage(
                    id,
                    message.text,
                    images = staged.images.map { it.bytes to it.mimeType },
                    file = staged.file?.let { Triple(it.bytes, it.mimeType, it.displayName) },
                    deliveryStrategy = 1,
                    clientMessageId = UUID.randomUUID().toString(),
                ).getOrThrow()
                val latest = _state.value
                if (conversationVisit == visit && latest.selectedConversationId == id) {
                    _state.value = latest.copy(
                        queuedMessages = latest.queuedMessages.filterNot { it.id == message.id },
                        busyQueuedMessageIds = latest.busyQueuedMessageIds - message.id,
                        queueError = null,
                        isRunning = true,
                    )
                    loadMessages(id)
                }
            } catch (error: Exception) {
                val latest = _state.value
                if (deletedUpstream) {
                    val targetDraft = if (draftKey == id) latest.draft else prefs.getDraftText(id)
                    val targetImages = if (draftKey == id) latest.attachments else draftAttachments[id].orEmpty()
                    val targetFile = if (draftKey == id) latest.fileAttachment else draftFiles[id]
                    val text = listOf(message.text, targetDraft).filter { it.isNotBlank() }.joinToString("\n\n")
                    val images = (staged.images + targetImages).distinctBy { it.uri }
                    val file = staged.file ?: targetFile
                    prefs.setDraftText(id, text)
                    saveImages(id, images)
                    saveFile(id, file)
                    keepStagedFiles = true
                    if (latest.selectedConversationId == id && draftKey == id) {
                        _state.value = latest.copy(
                            queuedMessages = latest.queuedMessages.filterNot { it.id == message.id },
                            busyQueuedMessageIds = latest.busyQueuedMessageIds - message.id,
                            queueError = "${error.message ?: "立即发送失败"}（内容已恢复到输入框）",
                            draft = text,
                            attachments = images,
                            fileAttachment = file,
                        )
                    }
                } else if (conversationVisit == visit && latest.selectedConversationId == id) {
                    _state.value = latest.copy(
                        busyQueuedMessageIds = latest.busyQueuedMessageIds - message.id,
                        queueError = error.message ?: "立即发送失败",
                    )
                }
            } finally {
                if (!keepStagedFiles) {
                    stagedImages.forEach(prefs::deleteUnusedDraftImage)
                    stagedDocuments.forEach(prefs::deleteUnusedDraftFile)
                }
            }
        }
    }

    private data class StagedQueuedAttachments(
        val images: List<PendingImage> = emptyList(),
        val file: PendingFile? = null,
    )

    private suspend fun stageQueuedAttachments(
        message: QueuedMessageItem,
        cascadeId: String,
        stagedImages: MutableList<File>,
        stagedDocuments: MutableList<File>,
    ): StagedQueuedAttachments {
        val structured = message.attachments.orEmpty()
        val imagePayloads = if (structured.isNotEmpty()) {
            structured.filter { it.mimeType.startsWith("image/") }.map { attachment ->
                val encoded = attachment.inlineData?.takeIf { it.isNotBlank() }
                    ?: attachment.thumbnail?.takeIf { it.isNotBlank() }
                if (encoded != null) Base64.decode(encoded.substringAfter(',', encoded), Base64.DEFAULT) to attachment.mimeType
                else attachment.uri?.takeIf { it.isNotBlank() }?.let { api.imageForDraft(it, cascadeId).getOrThrow() }
                    ?: throw IllegalArgumentException("排队消息中的图片无法恢复，请在电脑端处理")
            }
        } else if (!message.imageUrls.isNullOrEmpty()) {
            message.imageUrls.distinct().map { api.imageForDraft(it, cascadeId).getOrThrow() }
        } else {
            message.media.orEmpty().distinct().map { encoded ->
                val bytes = Base64.decode(encoded.substringAfter(',', encoded), Base64.DEFAULT)
                bytes to queuedImageMime(bytes)
            }
        }
        check(imagePayloads.size <= 4) { "排队消息包含超过 4 张图片，请在电脑端处理" }
        val images = imagePayloads.map { (bytes, mime) ->
            withContext(Dispatchers.IO) {
                val file = prefs.storeDraftImage(bytes, mime).also(stagedImages::add)
                PendingImage(Uri.fromFile(file), bytes, mime)
            }
        }
        val document = structured.filterNot { it.mimeType.startsWith("image/") }.also {
            check(it.size <= 1) { "排队消息包含多个文件，请在电脑端处理" }
        }.singleOrNull()?.let { attachment ->
            val model = selectedModel()
            check(model != null && attachment.mimeType in model.supportedMimeTypes) {
                "当前模型不支持 ${attachment.mimeType} 文件，请在电脑端处理"
            }
            val encoded = attachment.inlineData?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("排队文件正文无法恢复，请在电脑端处理")
            val bytes = Base64.decode(encoded.substringAfter(',', encoded), Base64.DEFAULT)
            val name = attachment.displayName?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("排队文件缺少文件名，请在电脑端处理")
            withContext(Dispatchers.IO) {
                val stored = prefs.storeDraftFile(bytes, name.take(255), attachment.mimeType)
                stagedDocuments += stored.file
                PendingFile(Uri.fromFile(stored.file), bytes, stored.mimeType, stored.displayName, stored.size)
            }
        }
        return StagedQueuedAttachments(images, document)
    }

    private fun queuedImageMime(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10)) -> "image/png"
        bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII).startsWith("GIF8") -> "image/gif"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> throw IllegalArgumentException("无法识别排队消息中的图片格式，请在电脑端处理")
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
            prefs.clearDraftFile(id)
            prefs.clearDraftComposerContext(id)
            draftAttachments.remove(id)
            draftFiles.remove(id)
            if (_state.value.selectedConversationId == id) {
                _state.value = _state.value.copy(draft = "", attachments = emptyList(), fileAttachment = null,
                    workspaceContexts = emptyList(), slashCommand = null)
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
                        pendingInteraction = null, interactionError = null, queuedMessages = emptyList(), busyQueuedMessageIds = emptySet(),
                        queueError = null, runningTasks = emptyList(), stoppingTaskIds = emptySet(), tasksError = null,
                        canProceed = false, proceedArtifactUri = null, isProceedingArtifact = false,
                        proceedError = null, planCommentDraft = ArtifactCommentDraftState(),
                        isSubmittingPlanComments = false, planCommentError = null,
                        revertMessage = null, revertPreview = null, isReverting = false, isLoadingMessages = true)
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

    fun respondToInteraction(options: List<InteractionOption>, writeInResponse: String = "") {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        val interaction = current.pendingInteraction ?: return
        val interactionKey = interaction.trajectoryId to interaction.stepIndex
        val visit = conversationVisit
        if (current.isSubmittingInteraction) return
        val answer = writeInResponse.trim()
        val selected = if (interaction.isMultiSelect) options.distinctBy { it.id } else options.take(1)
        if (interaction.type == "ask_question" && selected.isEmpty() && answer.isEmpty()) {
            _state.value = current.copy(interactionError = "请至少选择一项或填写回复")
            return
        }
        _state.value = current.copy(isSubmittingInteraction = true, interactionError = null)
        viewModelScope.launch {
            api.submitInteraction(id, interaction, selected, answer).fold(
                onSuccess = {
                    if (conversationVisit != visit ||
                        _state.value.pendingInteraction?.let { it.trajectoryId to it.stepIndex } != interactionKey
                    ) return@fold
                    _state.value = _state.value.copy(
                        pendingInteraction = null,
                        isSubmittingInteraction = false,
                        interactionError = null,
                    )
                    loadMessages(id)
                },
                onFailure = { error ->
                    if (conversationVisit != visit ||
                        _state.value.pendingInteraction?.let { it.trajectoryId to it.stepIndex } != interactionKey
                    ) return@fold
                    _state.value = _state.value.copy(
                        isSubmittingInteraction = false,
                        interactionError = error.message ?: "提交选择失败",
                    )
                },
            )
        }
    }

    fun proceedArtifact() {
        val current = _state.value
        val id = current.selectedConversationId ?: return
        val uri = current.proceedArtifactUri?.takeIf { current.canProceed && it.isNotBlank() } ?: return
        if (current.isProceedingArtifact || current.isSubmittingPlanComments) return
        val visit = conversationVisit
        val model = current.modelOverrideId?.let { selected ->
            current.models.firstOrNull { it.id == selected || it.model == selected }?.model
        }
        val comments = readyPlanComments(current.planCommentDraft)
        _state.value = current.copy(isProceedingArtifact = true, proceedError = null)
        viewModelScope.launch {
            api.proceedArtifact(id, uri, comments, model).fold(
                onSuccess = {
                    if (conversationVisit != visit) return@fold
                    prefs.clearArtifactCommentDraft(id, uri)
                    val latest = _state.value
                    _state.value = latest.copy(
                        canProceed = false,
                        proceedArtifactUri = null,
                        isProceedingArtifact = false,
                        proceedError = null,
                        planCommentDraft = ArtifactCommentDraftState(),
                        isSubmittingPlanComments = false,
                        planCommentError = null,
                        proceedSuccessCount = latest.proceedSuccessCount + 1,
                        isRunning = true,
                    )
                    loadMessages(id)
                },
                onFailure = { error ->
                    if (conversationVisit != visit) return@fold
                    _state.value = _state.value.copy(
                        isProceedingArtifact = false,
                        proceedError = error.message ?: "继续执行失败",
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
                        val incomingPlanUri = if (changed) current.proceedArtifactUri else payload.proceedArtifactUri
                        val planChanged = current.proceedArtifactUri != incomingPlanUri
                        val incomingPlanCommentDraft = if (planChanged) loadPlanCommentDraft(id, incomingPlanUri)
                            else current.planCommentDraft
                        val incomingInteraction = if (changed) current.pendingInteraction else payload.pendingInteraction
                        val interactionChanged = current.pendingInteraction?.let { it.trajectoryId to it.stepIndex } !=
                            incomingInteraction?.let { it.trajectoryId to it.stepIndex }
                        _state.value = _state.value.copy(
                            messages = merged,
                            outgoing = outgoing,
                            isRunning = (if (changed) current.isRunning else payload.status.contains("RUNNING", ignoreCase = true)) ||
                                outgoing.any { it.conversationId == id },
                            pendingInteraction = incomingInteraction,
                            isSubmittingInteraction = if (interactionChanged) false else current.isSubmittingInteraction,
                            interactionError = if (interactionChanged || incomingInteraction == null) null else current.interactionError,
                            queuedMessages = if (changed) current.queuedMessages else payload.queuedMessages.orEmpty(),
                            runningTasks = if (changed) current.runningTasks else payload.runningTasks.orEmpty(),
                            canProceed = if (changed) current.canProceed else payload.canProceed,
                            proceedArtifactUri = incomingPlanUri,
                            isProceedingArtifact = if (planChanged) false else current.isProceedingArtifact,
                            proceedError = if (planChanged || (!changed && !payload.canProceed)) null else current.proceedError,
                            planCommentDraft = incomingPlanCommentDraft,
                            isSubmittingPlanComments = if (planChanged) false else current.isSubmittingPlanComments,
                            planCommentError = if (planChanged || (!changed && !payload.canProceed)) null else current.planCommentError,
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
        subagentsJob?.cancel()
        stream.close()
        connectionManager.stopMonitoring()
        super.onCleared()
    }
}

private const val DEFAULT_CHAT_MODEL = "gemini-3.8-flash-high"
private const val NEW_CHAT_DRAFT = "android_new_chat"
private const val PENDING_SEND_DRAFT = "android_pending_send"

private fun mergeComposerContext(first: DraftComposerContext, second: DraftComposerContext) = DraftComposerContext(
    workspaceItems = (first.workspaceItems + second.workspaceItems).distinctBy { it.uri }.take(8),
    slashCommand = first.slashCommand ?: second.slashCommand,
)

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
