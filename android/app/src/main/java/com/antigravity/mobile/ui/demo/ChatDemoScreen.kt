package com.antigravity.mobile.ui.demo

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.mobile.ui.chat.ChatViewModel
import com.antigravity.mobile.ui.chat.PairingScreen
import com.antigravity.mobile.ui.chat.InteractionPanel
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatDemoScreen(viewModel: ChatViewModel, onLeaveApp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeConversations = state.conversations.filterNot { it.isArchived }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val keyboardHeight = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    var keyboardWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardHeight) {
        val visible = keyboardHeight > 0.dp
        if (keyboardWasVisible && !visible) focusManager.clearFocus()
        keyboardWasVisible = visible
    }
    var composerHeightPx by remember { mutableStateOf(0) }
    val composerHeight = with(density) { composerHeightPx.toDp() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var pairingSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var pairSuccessAtOpen by rememberSaveable { mutableStateOf(0) }
    var projectsSelected by rememberSaveable { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = if (projectsSelected) 1 else 0, pageCount = { 2 })
    var openedFromProject by rememberSaveable { mutableStateOf(false) }
    var expandedProjectKeys by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val projectListState = rememberLazyListState()
    var archivedOpen by rememberSaveable { mutableStateOf(false) }
    var openedFromArchive by rememberSaveable { mutableStateOf(false) }
    var renameConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var renameTitle by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris -> viewModel.addImages(uris) }
    val unsupported: () -> Unit = {
        scope.launch { snackbar.showSnackbar("已支持文字和图片，语音功能暂未开放") }
    }
    LaunchedEffect(state.selectedConversationId) {
        showDeleteConfirm = false
        showRename = false
        if (state.selectedConversationId == null && openedFromArchive) {
            archivedOpen = true
            openedFromArchive = false
        }
    }

    LaunchedEffect(state.pairSuccessCount) {
        if (pairingSettingsOpen && state.pairSuccessCount > pairSuccessAtOpen) {
            pairingSettingsOpen = false
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        projectsSelected = pagerState.settledPage == 1
        if (projectsSelected) viewModel.refreshProjects()
    }
    LaunchedEffect(projectsSelected, state.selectedConversationId) {
        if (state.selectedConversationId == null && pagerState.settledPage != (if (projectsSelected) 1 else 0)) {
            pagerState.animateScrollToPage(if (projectsSelected) 1 else 0)
        }
    }

    if (!state.isPaired || pairingSettingsOpen) {
        BackHandler(pairingSettingsOpen && state.isPaired) { pairingSettingsOpen = false }
        Box(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding()) {
            PairingScreen(
                isPairing = state.isPairing,
                error = state.error,
                currentGatewayUrl = viewModel.gatewayUrl,
                onBack = if (state.isPaired) ({ pairingSettingsOpen = false }) else null,
                onPair = viewModel::pair,
            )
        }
        return
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }
    val returnFromConversation: () -> Unit = {
        viewModel.closeConversation()
        projectsSelected = openedFromProject
        openedFromProject = false
    }

    val openDrawer: () -> Unit = {
        viewModel.refreshConversations()
        viewModel.refreshProjects()
        scope.launch { drawerState.open() }
        Unit
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !openedFromProject,
        drawerContent = {
            DemoDrawer(
                modifier = Modifier.fillMaxWidth(0.80f).fillMaxHeight()
                    .statusBarsPadding().navigationBarsPadding(),
                conversations = activeConversations,
                projects = state.projects,
                isLoading = state.isLoadingConversations,
                isLoadingProjects = state.isLoadingProjects,
                selectedConversationId = state.selectedConversationId,
                conversationsError = state.conversationsError, projectsError = state.projectsError,
                onRetryConversations = viewModel::refreshConversations, onRetryProjects = viewModel::refreshProjects,
                onOpenConversation = { id ->
                    archivedOpen = false; openedFromArchive = false
                    openedFromProject = false
                    projectsSelected = false
                    viewModel.openConversation(id)
                    scope.launch { drawerState.close() }
                },
                onOpenProjectConversation = { id ->
                    archivedOpen = false; openedFromArchive = false
                    openedFromProject = true
                    projectsSelected = true
                    viewModel.openConversation(id)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    archivedOpen = false; openedFromArchive = false
                    viewModel.newConversation()
                    projectsSelected = false
                    openedFromProject = false
                    scope.launch { drawerState.close() }
                },
                onArchived = {
                    viewModel.closeConversation()
                    openedFromProject = false; openedFromArchive = false; archivedOpen = true
                    viewModel.refreshConversations()
                    scope.launch { drawerState.close() }
                },
                onPairing = {
                    scope.launch {
                        drawerState.close()
                        pairSuccessAtOpen = state.pairSuccessCount
                        pairingSettingsOpen = true
                    }
                },
            )
        },
    ) {
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (archivedOpen) {
                state.conversationsError?.let { LoadFailure(it, state.isLoadingConversations, viewModel::refreshConversations) }
                ArchivedConversations(state.conversations, state.isLoadingConversations, state.busyConversations,
                    onBack = { archivedOpen = false }, onRefresh = viewModel::refreshConversations,
                    onOpen = { id -> archivedOpen = false; openedFromArchive = true; viewModel.openConversation(id) },
                    onRename = { item -> renameConversationId = item.id; renameTitle = item.title; showRename = true },
                    onRestore = { viewModel.setArchived(it, false) },
                    onDelete = { deleteConversationId = it; showDeleteConfirm = true }, entranceReady = !drawerState.isOpen)
            } else if (state.selectedConversationId != null) {
                key(state.selectedConversationId) {
                ConversationTopBar(
                    returnToProjects = openedFromProject || openedFromArchive,
                    leadingDescription = if (openedFromArchive) "返回归档列表" else if (openedFromProject) "返回项目列表" else "打开菜单",
                    onLeading = if (openedFromArchive) ({ viewModel.closeConversation(); archivedOpen = true; openedFromArchive = false })
                        else if (openedFromProject) returnFromConversation else openDrawer,
                    onNewChat = {
                        projectsSelected = false
                        openedFromProject = false
                        archivedOpen = false; openedFromArchive = false
                        viewModel.newConversation()
                    },
                    onRename = {
                        renameConversationId = state.selectedConversationId
                        renameTitle = state.conversations.firstOrNull { it.id == state.selectedConversationId }?.title.orEmpty()
                        showRename = true
                    },
                    onDelete = { deleteConversationId = state.selectedConversationId; showDeleteConfirm = true },
                    title = state.conversations.firstOrNull { it.id == state.selectedConversationId }?.displayTitle ?: "未命名会话",
                    isPinned = state.conversations.firstOrNull { it.id == state.selectedConversationId }?.isPinned == true,
                    isArchived = state.conversations.firstOrNull { it.id == state.selectedConversationId }?.isArchived == true,
                    onPin = { state.selectedConversationId?.let { id -> viewModel.setPinned(id, state.conversations.firstOrNull { it.id == id }?.isPinned != true) } },
                    onArchive = { state.selectedConversationId?.let { id -> viewModel.setArchived(id, state.conversations.firstOrNull { it.id == id }?.isArchived != true) } },
                    actionsEnabled = !state.selectedConversationId.orEmpty().startsWith("local:") &&
                        state.selectedConversationId !in state.busyConversations && !state.isReverting,
                )
                }
                state.messagesError?.let { LoadFailure(it, state.isLoadingMessages, viewModel::retryMessages) }
                if (!state.selectedConversationId.orEmpty().startsWith("local:") && state.connectionStatus != com.antigravity.mobile.data.service.ConnectionStatus.CONNECTED) {
                    LoadFailure(if (state.connectionStatus == com.antigravity.mobile.data.service.ConnectionStatus.CONNECTING)
                        "正在连接，生成状态待同步…" else "连接已断开，正在自动重连…", state.isLoadingMessages, viewModel::retryMessages)
                } else if (state.pendingInteraction != null) {
                    Text("等待你的操作", color = SecondaryInk, modifier = Modifier.padding(horizontal = 22.dp))
                }
                key(state.selectedConversationId) {
                Box(Modifier.weight(1f)) {
                    ConversationContent(
                        viewModel = viewModel,
                        messages = state.messages + state.outgoing.filter { it.conversationId == state.selectedConversationId }.map { it.message },
                        streamingMessageId = state.streamingMessageId,
                        isRunning = state.isRunning,
                        isLoading = state.isLoadingMessages,
                        hasMore = state.hasMoreMessages,
                        isLoadingOlder = state.isLoadingOlder,
                        onLoadOlder = viewModel::loadOlderMessages,
                        olderError = state.olderMessagesError,
                        entranceOffset = if (openedFromProject || openedFromArchive) 10.dp else 0.dp,
                        entranceReady = !drawerState.isOpen,
                        bottomSpace = composerHeight + keyboardHeight + 23.dp,
                        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                var travel = 0f
                                var elapsed = 0L
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { change ->
                                        travel += abs(change.positionChange().x) + abs(change.positionChange().y)
                                        elapsed = change.uptimeMillis - down.uptimeMillis
                                    }
                                } while (event.changes.any { it.pressed })
                                if (travel < viewConfiguration.touchSlop && elapsed < viewConfiguration.longPressTimeoutMillis) focusManager.clearFocus()
                            }
                        },
                    )
                }
                }
            } else {
                EmptyTopBar(
                    onMenu = openDrawer,
                    tabPosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f),
                    onChat = { scope.launch { pagerState.animateScrollToPage(0) } },
                    onProjects = {
                        viewModel.refreshProjects()
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    onUnsupported = unsupported,
                )
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)
                    .then(contentEntrance("home", if (projectsSelected) (-10).dp else 0.dp, !drawerState.isOpen)),
                    userScrollEnabled = pagerState.settledPage == 1,
                    beyondViewportPageCount = 1) { page ->
                if (page == 1) {
                    ProjectOverview(
                        projects = state.projects,
                        conversations = activeConversations,
                        isLoading = state.isLoadingProjects,
                        error = state.projectsError, onRetry = viewModel::refreshProjects,
                        onOpenConversation = { id ->
                            archivedOpen = false; openedFromArchive = false
                            openedFromProject = true
                            viewModel.openConversation(id)
                        },
                        expandedProjects = expandedProjectKeys.toSet(),
                        onToggleProject = { key ->
                            expandedProjectKeys = ArrayList(expandedProjectKeys).apply {
                                if (key in this) remove(key) else add(key)
                            }
                        },
                        listState = projectListState,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(Modifier.fillMaxSize().padding(bottom = composerHeight + keyboardHeight).pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var horizontal = 0f
                            var vertical = 0f
                            var travel = 0f
                            var elapsed = 0L
                            var draggingPage = false
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { change ->
                                    val delta = change.positionChange()
                                    horizontal += delta.x
                                    vertical += delta.y
                                    travel += abs(delta.x) + abs(delta.y)
                                    elapsed = change.uptimeMillis - down.uptimeMillis
                                    if (!draggingPage && horizontal < -viewConfiguration.touchSlop &&
                                        abs(horizontal) > abs(vertical) * 1.2f) draggingPage = true
                                    if (draggingPage) {
                                        pagerState.dispatchRawDelta(-delta.x)
                                        change.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                            if (draggingPage) {
                                val progress = pagerState.currentPage + pagerState.currentPageOffsetFraction
                                scope.launch { pagerState.animateScrollToPage(if (progress > 0.38f) 1 else 0) }
                            } else if (travel < viewConfiguration.touchSlop && elapsed < viewConfiguration.longPressTimeoutMillis) {
                                focusManager.clearFocus()
                            }
                        }
                    }) {
                    Spacer(Modifier.weight(1f))
                    Column(modifier = Modifier.padding(horizontal = 28.dp)) {
                        val recent = activeConversations.filter { it.isPureChat && !it.isSubagent }.take(2)
                        if (recent.isEmpty() && state.isLoadingConversations) {
                            Text("正在加载会话…", color = SecondaryInk, fontSize = 16.sp)
                        }
                        state.conversationsError?.let { LoadFailure(it, state.isLoadingConversations, viewModel::refreshConversations) }
                        recent.forEach { conversation ->
                            Text(
                                conversation.displayTitle,
                                modifier = Modifier.fillMaxWidth()
                                    .quietClickable {
                                        openedFromProject = false
                                        viewModel.openConversation(conversation.id)
                                    }
                                    .padding(vertical = 12.dp),
                                color = SecondaryInk,
                                fontSize = 16.sp,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                    }
                }
                }
            }
        }

        if (!archivedOpen && (state.selectedConversationId != null || !projectsSelected)) {
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()) {
                Column(Modifier.onSizeChanged { composerHeightPx = it.height }) {
                    state.pendingInteraction?.let { interaction ->
                        InteractionPanel(interaction = interaction,
                            isSubmitting = state.isSubmittingInteraction,
                            onChoose = viewModel::respondToInteraction)
                    }
                    Composer(
                        activeConversation = state.selectedConversationId != null,
                        draft = state.draft,
                        attachments = state.attachments,
                        onDraftChange = viewModel::setDraft,
                        onAddImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onRemoveImage = viewModel::removeImage,
                        onSend = viewModel::send,
                        isSending = state.isSending || state.isLoadingMessages || state.isReverting || state.selectedConversationId in state.busyConversations,
                        isRunning = state.isRunning,
                        isStopping = state.isStopping,
                        onStop = viewModel::stopGeneration,
                        onUnsupported = unsupported,
                    )
                    Spacer(Modifier.height(23.dp))
                }
            }
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        state.revertMessage?.let { message ->
            var conversationOnly by remember(message.id) { mutableStateOf(true) }
            AlertDialog(onDismissRequest = viewModel::dismissRevert, title = { Text("回退到这条消息？") },
                text = {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("这条消息及之后的对话会被撤回，原消息将恢复到草稿。", color = SecondaryInk)
                        Text(message.effectiveText, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (state.isLoadingRevert) CircularProgressIndicator(color = AccentBlue)
                        state.revertPreview?.let { preview ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = !conversationOnly, enabled = !state.isReverting, onCheckedChange = { conversationOnly = !it })
                                Text("同时回退工作区文件")
                            }
                            Text(if (conversationOnly) "仅回退对话，文件保持现状。" else "将恢复工作区到此前状态；影响可能不止下方预览文件。", color = SecondaryInk)
                            if (!conversationOnly) preview.files.forEach { file ->
                                Text("${file.fileName} · ${file.actionType} · +${file.additions} / −${file.deletions}", fontSize = 14.sp)
                            }
                        }
                        state.revertError?.let { Text(it, color = Color(0xFFB3261E)) }
                    }
                }, confirmButton = {
                    TextButton(enabled = state.revertPreview != null && !state.isReverting && !state.isRunning,
                        onClick = { viewModel.executeRevert(conversationOnly) }) { Text(if (state.isReverting) "正在回退…" else "确认回退") }
                }, dismissButton = {
                    TextButton(enabled = !state.isReverting, onClick = viewModel::dismissRevert) { Text("取消") }
                    if (state.revertError != null && state.revertPreview == null) TextButton(onClick = { viewModel.previewRevert(message) }) { Text("重试") }
                })
        }
        if (showRename) {
            AlertDialog(
                onDismissRequest = { if (renameConversationId !in state.busyConversations) showRename = false },
                title = { Text("重命名会话") },
                text = {
                    OutlinedTextField(value = renameTitle, onValueChange = { renameTitle = it },
                        label = { Text("会话标题") }, singleLine = true, enabled = renameConversationId !in state.busyConversations)
                },
                confirmButton = {
                    TextButton(enabled = renameTitle.isNotBlank() && renameConversationId !in state.busyConversations,
                        onClick = { renameConversationId?.let { id -> viewModel.renameConversation(id, renameTitle) {
                            if (renameConversationId == id) showRename = false
                        } } }) {
                        Text(if (renameConversationId in state.busyConversations) "保存中…" else "保存")
                    }
                },
                dismissButton = {
                    TextButton(enabled = renameConversationId !in state.busyConversations, onClick = { showRename = false }) { Text("取消") }
                },
            )
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("删除当前会话？") },
                text = { Text("这条会话会从网关中删除。") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        deleteConversationId?.let(viewModel::deleteConversation)
                    }) { Text("删除", color = Color(0xFFB3261E)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
                },
            )
        }
    }
    }
    BackHandler {
        when {
            keyboardHeight > 0.dp -> {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
            archivedOpen -> archivedOpen = false
            state.selectedConversationId != null && openedFromArchive -> { viewModel.closeConversation(); archivedOpen = true; openedFromArchive = false }
            drawerState.isOpen -> scope.launch { drawerState.close() }
            state.selectedConversationId != null && openedFromProject -> returnFromConversation()
            state.selectedConversationId == null && projectsSelected -> projectsSelected = false
            else -> onLeaveApp()
        }
    }
}
