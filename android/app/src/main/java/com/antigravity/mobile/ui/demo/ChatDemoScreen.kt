package com.antigravity.mobile.ui.demo

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.mobile.ui.chat.ChatViewModel
import com.antigravity.mobile.ui.chat.PairingScreen
import com.antigravity.mobile.ui.chat.InteractionPanel
import kotlinx.coroutines.launch

@Composable
fun ChatDemoScreen(viewModel: ChatViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var pairingSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var pairSuccessAtOpen by rememberSaveable { mutableStateOf(0) }
    var projectsSelected by rememberSaveable { mutableStateOf(false) }
    var openedFromProject by rememberSaveable { mutableStateOf(false) }
    var expandedProjectKeys by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val projectListState = rememberLazyListState()
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris -> viewModel.addImages(uris) }
    val unsupported: () -> Unit = {
        scope.launch { snackbar.showSnackbar("当前版本先支持文字聊天") }
    }

    LaunchedEffect(state.pairSuccessCount) {
        if (pairingSettingsOpen && state.pairSuccessCount > pairSuccessAtOpen) {
            pairingSettingsOpen = false
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
    BackHandler(drawerState.isOpen) { scope.launch { drawerState.close() } }
    val returnFromConversation: () -> Unit = {
        viewModel.newConversation()
        projectsSelected = openedFromProject
        openedFromProject = false
    }
    BackHandler(!drawerState.isOpen && state.selectedConversationId != null) { returnFromConversation() }
    BackHandler(!drawerState.isOpen && state.selectedConversationId == null && projectsSelected) {
        projectsSelected = false
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
                conversations = state.conversations,
                projects = state.projects,
                isLoading = state.isLoadingConversations,
                isLoadingProjects = state.isLoadingProjects,
                onOpenConversation = { id ->
                    openedFromProject = false
                    projectsSelected = false
                    viewModel.openConversation(id)
                    scope.launch { drawerState.close() }
                },
                onOpenProjectConversation = { id ->
                    openedFromProject = true
                    projectsSelected = true
                    viewModel.openConversation(id)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.newConversation()
                    projectsSelected = false
                    openedFromProject = false
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
            if (state.selectedConversationId != null) {
                ConversationTopBar(
                    returnToProjects = openedFromProject,
                    onLeading = if (openedFromProject) returnFromConversation else openDrawer,
                    onNewChat = {
                        projectsSelected = false
                        openedFromProject = false
                        viewModel.newConversation()
                    },
                    onMore = { showDeleteConfirm = true },
                )
                key(state.selectedConversationId) {
                    ConversationContent(
                        viewModel = viewModel,
                        messages = state.messages,
                        isLoading = state.isLoadingMessages,
                        hasMore = state.hasMoreMessages,
                        isLoadingOlder = state.isLoadingOlder,
                        onLoadOlder = viewModel::loadOlderMessages,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                EmptyTopBar(
                    onMenu = openDrawer,
                    projectsSelected = projectsSelected,
                    onChat = { projectsSelected = false },
                    onProjects = {
                        projectsSelected = true
                        viewModel.refreshProjects()
                    },
                    onUnsupported = unsupported,
                )
                if (projectsSelected) {
                    ProjectOverview(
                        projects = state.projects,
                        conversations = state.conversations,
                        isLoading = state.isLoadingProjects,
                        onOpenConversation = { id ->
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
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                    Column(modifier = Modifier.padding(horizontal = 28.dp)) {
                        val recent = state.conversations.filter { it.isPureChat && !it.isSubagent }.take(2)
                        if (recent.isEmpty() && state.isLoadingConversations) {
                            Text("正在加载会话…", color = SecondaryInk, fontSize = 16.sp)
                        }
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
            if (state.selectedConversationId != null && state.isRunning) {
                Text("正在回复…", modifier = Modifier.padding(start = 28.dp, bottom = 12.dp),
                    color = SecondaryInk, fontSize = 14.sp)
            }
            state.pendingInteraction?.let { interaction ->
                InteractionPanel(
                    interaction = interaction,
                    isSubmitting = state.isSubmittingInteraction,
                    onChoose = viewModel::respondToInteraction,
                )
            }
            if (!projectsSelected || state.selectedConversationId != null) Composer(
                activeConversation = state.selectedConversationId != null,
                draft = state.draft,
                attachments = state.attachments,
                onDraftChange = viewModel::setDraft,
                onAddImage = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemoveImage = viewModel::removeImage,
                onSend = viewModel::send,
                isSending = state.isSending,
                onUnsupported = unsupported,
            )
            Spacer(Modifier.height(23.dp))
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("删除当前会话？") },
                text = { Text("这条会话会从网关中删除。") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteSelectedConversation()
                    }) { Text("删除", color = Color(0xFFB3261E)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
                },
            )
        }
    }
    }
}
