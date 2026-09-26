package com.antigravity.mobile.ui.demo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatDemoScreen() {
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var conversationOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(drawerOpen) { drawerOpen = false }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        ) {
            if (conversationOpen) {
                ConversationTopBar(onMenu = { drawerOpen = true }, onNewChat = { conversationOpen = false })
                ConversationContent(modifier = Modifier.weight(1f))
            } else {
                EmptyTopBar(onMenu = { drawerOpen = true })
                Spacer(Modifier.weight(1f))
                Column(modifier = Modifier.padding(horizontal = 28.dp)) {
                    Text("Fingerling 语音链路仍待接入。", color = SecondaryInk, fontSize = 16.sp)
                    Spacer(Modifier.height(24.dp))
                    Text("英语口语与发音练习", color = SecondaryInk, fontSize = 16.sp)
                }
                Spacer(Modifier.height(30.dp))
            }
            Composer(activeConversation = conversationOpen)
            Spacer(Modifier.height(23.dp))
        }

        if (drawerOpen) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                    .clickable { drawerOpen = false },
            )
        }
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
        ) {
            DemoDrawer(
                modifier = Modifier.fillMaxWidth(0.80f).fillMaxHeight()
                    .statusBarsPadding().navigationBarsPadding(),
                onOpenConversation = {
                    conversationOpen = true
                    drawerOpen = false
                },
                onNewChat = {
                    conversationOpen = false
                    drawerOpen = false
                },
            )
        }
    }
}
