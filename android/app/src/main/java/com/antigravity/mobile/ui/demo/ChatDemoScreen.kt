package com.antigravity.mobile.ui.demo

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.material3.Surface
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.mobile.ui.chat.ChatViewModel
import com.antigravity.mobile.ui.chat.PairingScreen
import com.antigravity.mobile.ui.chat.InteractionPanel
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    var modelPickerOpen by remember { mutableStateOf(false) }
    val composerFocusRequester = remember { FocusRequester() }
    var focusAfterModelDone by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusAfterModelDone) {
        if (focusAfterModelDone > 0) {
            withFrameNanos { }
            composerFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    val chosenModelId = state.modelOverrideId ?: if (state.selectedConversationId == null) state.selectedModelId else state.activeModelId
    val chosenModel = state.models.firstOrNull { it.id == chosenModelId || it.model == chosenModelId }
    val modelLabel = chosenModel?.label ?: "选择模型"
    val gptModelBadge = formatGptModelBadge(chosenModel)
    val modelEnabled = !state.isSending && !state.isReverting && state.creatingProjectKey == null
    val canCreate = modelEnabled && state.models.isNotEmpty() && !state.isLoadingModels
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            tempPhotoUri?.let { uri -> viewModel.addImages(listOf(uri)) }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris -> viewModel.addImages(uris) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { viewModel.addImages(listOf(it)) }
    }
    val unsupported: () -> Unit = {
        scope.launch { snackbar.showSnackbar("已支持文字和图片，语音功能暂未开放") }
    }
    LaunchedEffect(state.selectedConversationId) {
        modelPickerOpen = false
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
                canCreate = canCreate,
                onNewProjectConversation = { project -> viewModel.createProjectConversation(project) {
                    archivedOpen = false; openedFromArchive = false; openedFromProject = true; projectsSelected = true
                    scope.launch { drawerState.close() }
                } },
                modifier = Modifier.fillMaxWidth(0.80f).fillMaxHeight()
                    .statusBarsPadding().navigationBarsPadding(),
                conversations = activeConversations,
                projects = state.projects,
                isLoading = state.isLoadingConversations,
                isLoadingProjects = state.isLoadingProjects,
                selectedConversationId = state.selectedConversationId,
                conversationsError = state.conversationsError, projectsError = state.projectsError,
                onRetryConversations = viewModel::refreshConversations, onRetryProjects = viewModel::refreshProjects,
                busy = state.busyConversations,
                onRename = { item -> renameConversationId = item.id; renameTitle = item.title; showRename = true },
                onPin = { item -> viewModel.setPinned(item.id, !item.isPinned) },
                onArchive = { item -> viewModel.setArchived(item.id, true) },
                onDelete = { item -> deleteConversationId = item.id; showDeleteConfirm = true },
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
                    var topBarHeight by remember { mutableStateOf(64.dp) }
                    val refreshState = rememberPullToRefreshState()
                    Box(Modifier.weight(1f)) {
                        PullToRefreshBox(isRefreshing = state.isLoadingMessages && state.messages.isNotEmpty(),
                            onRefresh = viewModel::retryMessages, state = refreshState,
                            modifier = Modifier.fillMaxSize(),
                            indicator = {
                                PullToRefreshDefaults.Indicator(state = refreshState,
                                    isRefreshing = state.isLoadingMessages && state.messages.isNotEmpty(),
                                    modifier = Modifier.align(Alignment.TopCenter).padding(top = topBarHeight))
                            }) {
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
                                allowRevert = !state.isSending && !state.isReverting && state.selectedConversationId !in state.busyConversations,
                                topSpace = topBarHeight,
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
                        GradientTopBar(topBarHeight) {
                            Box(Modifier.onSizeChanged { topBarHeight = with(density) { it.height.toDp() } }) {
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
                        }
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
                        canCreate = canCreate,
                        onNewConversation = { project -> viewModel.createProjectConversation(project) {
                            archivedOpen = false; openedFromArchive = false; openedFromProject = true
                        } },
                        projects = state.projects,
                        conversations = activeConversations,
                        isLoading = state.isLoadingProjects || state.isLoadingConversations,
                        error = state.projectsError ?: state.conversationsError,
                        onRetry = { viewModel.refreshProjects(); viewModel.refreshConversations(); viewModel.refreshModels() },
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
                                        abs(horizontal) > abs(vertical) * 1.2f) {
                                        draggingPage = true
                                        focusManager.clearFocus()
                                    }
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

        val homeSwipeProgress = if (state.selectedConversationId == null) {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
        } else {
            0f
        }
        val isHome = state.selectedConversationId == null
        val showComposer = !archivedOpen && (state.selectedConversationId != null || homeSwipeProgress < 1f || !projectsSelected)

        if (showComposer) {
            val widthFactor = if (isHome) {
                (1f - homeSwipeProgress * 0.65f).coerceIn(0.35f, 1f)
            } else 1f
            val alphaFactor = if (isHome) {
                (1f - homeSwipeProgress).coerceIn(0f, 1f)
            } else 1f
            val translationYPx = if (isHome) {
                homeSwipeProgress * with(density) { 16.dp.toPx() }
            } else 0f

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.onSizeChanged { composerHeightPx = it.height },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    state.pendingInteraction?.let { interaction ->
                        InteractionPanel(
                            interaction = interaction,
                            isSubmitting = state.isSubmittingInteraction,
                            onChoose = viewModel::respondToInteraction,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(widthFactor)
                            .graphicsLayer {
                                // Keep the changing native shadow outside the rectangular bounds of this layer.
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                alpha = alphaFactor
                                translationY = translationYPx
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Composer(
                            activeConversation = state.selectedConversationId != null,
                            draft = state.draft,
                            attachments = state.attachments,
                            onDraftChange = viewModel::setDraft,
                            onTakePhoto = {
                                val tempFile = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                                tempPhotoUri = uri
                                cameraLauncher.launch(uri)
                            },
                            onPickImage = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onPickFile = {
                                filePicker.launch("*/*")
                            },
                            onRemoveImage = viewModel::removeImage,
                            onSend = viewModel::send,
                            isSending = state.isSending || state.isRestoringDraft || state.isLoadingMessages || state.isReverting || state.selectedConversationId in state.busyConversations,
                            isRunning = state.isRunning,
                            isStopping = state.isStopping,
                            onStop = viewModel::stopGeneration,
                            onUnsupported = unsupported,
                            shadowAlpha = alphaFactor,
                            modelBadge = gptModelBadge,
                            onOpenModelConfig = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                modelPickerOpen = true
                                if (state.models.isEmpty()) viewModel.refreshModels()
                            },
                            modelEnabled = modelEnabled,
                            inputFocusRequester = composerFocusRequester,
                        )
                    }
                    Spacer(Modifier.height(23.dp))
                }
            }
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        if (modelPickerOpen && state.isPaired) {
            ModelConfigBottomSheet(
                onDismiss = { modelPickerOpen = false },
                models = state.models,
                currentChosenModelId = chosenModelId,
                onSelectModel = viewModel::selectModel,
                isLoadingModels = state.isLoadingModels,
                modelsError = state.modelsError,
                onRetryModels = viewModel::refreshModels,
                modelSelectionEnabled = modelEnabled,
                onDone = { modelPickerOpen = false; focusAfterModelDone++ },
            )
        }
        state.revertMessage?.let { message ->
            var revertFiles by remember(message.id) { mutableStateOf(true) }
            Dialog(onDismissRequest = viewModel::dismissRevert) {
                Surface(modifier = Modifier.width(300.dp), shape = RoundedCornerShape(20.dp), color = Color.White) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("确认回退吗？", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = revertFiles, role = Role.Checkbox,
                            enabled = !state.isReverting, onValueChange = { revertFiles = it }),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = revertFiles, onCheckedChange = null, enabled = !state.isReverting,
                                modifier = Modifier.padding(end = 10.dp), colors = CheckboxDefaults.colors(checkedColor = AccentBlue))
                            Text("回退文件改动", color = Ink, fontSize = 14.sp)
                        }
                        state.revertError?.let {
                            Text(it, color = Color(0xFFB3261E), fontSize = 13.sp, lineHeight = 18.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = !state.isReverting, onClick = viewModel::dismissRevert) {
                                Text("取消", color = SecondaryInk, fontSize = 14.sp)
                            }
                            if (state.revertError != null && state.revertPreview == null) {
                                TextButton(onClick = { viewModel.previewRevert(message) }) {
                                    Text("重试", color = AccentBlue, fontSize = 14.sp)
                                }
                            } else TextButton(enabled = state.revertPreview != null && !state.isReverting && !state.isSending,
                                onClick = { viewModel.executeRevert(!revertFiles) }) {
                                Box(contentAlignment = Alignment.Center) {
                                    val waiting = state.isLoadingRevert || state.isReverting
                                    Text("确认回退", color = if (state.revertPreview != null && !state.isSending) AccentBlue else SecondaryInk.copy(alpha = 0.4f),
                                        fontSize = 14.sp, modifier = Modifier.graphicsLayer { alpha = if (waiting) 0f else 1f })
                                    if (waiting) CircularProgressIndicator(Modifier.size(18.dp), color = AccentBlue, strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
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
                title = { Text("删除会话？") },
                text = { Text(state.conversations.firstOrNull { it.id == deleteConversationId }?.displayTitle ?: "未命名会话",
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
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
