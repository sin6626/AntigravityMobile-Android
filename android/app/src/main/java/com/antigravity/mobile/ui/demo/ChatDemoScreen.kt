package com.antigravity.mobile.ui.demo

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var projectsSelected by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris -> viewModel.addImages(uris) }
    val unsupported: () -> Unit = {
        scope.launch { snackbar.showSnackbar("当前版本先支持文字聊天") }
    }

    if (!state.isPaired) {
        Box(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding()) {
            PairingScreen(state.isPairing, state.error, viewModel::pair)
        }
        return
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }
    BackHandler(drawerOpen) { drawerOpen = false }
    BackHandler(!drawerOpen && state.selectedConversationId != null) { viewModel.newConversation() }
    BackHandler(!drawerOpen && state.selectedConversationId == null && projectsSelected) {
        projectsSelected = false
    }

    val openDrawer = {
        viewModel.refreshConversations()
        viewModel.refreshProjects()
        drawerOpen = true
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (state.selectedConversationId != null) {
                ConversationTopBar(
                    onMenu = openDrawer,
                    onNewChat = {
                        projectsSelected = false
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
                        onOpenConversation = viewModel::openConversation,
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
                                    .clickable { viewModel.openConversation(conversation.id) }
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

        if (drawerOpen) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                .clickable { drawerOpen = false })
        }
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
        ) {
            DemoDrawer(
                modifier = Modifier.fillMaxWidth(0.80f).fillMaxHeight()
                    .statusBarsPadding().navigationBarsPadding(),
                conversations = state.conversations,
                projects = state.projects,
                isLoading = state.isLoadingConversations,
                isLoadingProjects = state.isLoadingProjects,
                onOpenConversation = { id ->
                    viewModel.openConversation(id)
                    drawerOpen = false
                },
                onNewChat = {
                    viewModel.newConversation()
                    projectsSelected = false
                    drawerOpen = false
                },
            )
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
