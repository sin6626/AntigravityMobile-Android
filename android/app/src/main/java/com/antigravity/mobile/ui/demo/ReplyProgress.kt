package com.antigravity.mobile.ui.demo

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle

@Composable
internal fun ReplyProgress(waiting: Boolean, stopping: Boolean, animate: Boolean, modifier: Modifier = Modifier) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val motionScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    Row(modifier.padding(top = 12.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Keep the slot after the first body arrives so the status text never shifts sideways.
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(waiting, enter = EnterTransition.None, exit = fadeOut(tween(120))) {
                val spinning = animate && lifecycle.isAtLeast(Lifecycle.State.RESUMED) && motionScale > 0f
                Box(Modifier.testTag("reply-wait-spinner")) {
                    val indicator = Modifier.size(16.dp).clearAndSetSemantics {}
                    if (spinning) Box(Modifier.testTag("reply-wait-motion")) {
                        CircularProgressIndicator(modifier = indicator, color = AccentBlue, strokeWidth = 2.dp)
                    }
                    else CircularProgressIndicator(progress = { .25f }, modifier = indicator, color = AccentBlue, strokeWidth = 2.dp)
                }
            }
        }
        Text(if (stopping) "正在停止…" else "正在回复…", color = SecondaryInk, fontSize = 14.sp)
    }
}
