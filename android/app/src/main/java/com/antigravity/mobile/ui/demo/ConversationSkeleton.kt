package com.antigravity.mobile.ui.demo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun ConversationSkeleton(
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
) {
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1300f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )

    val neutralShimmer = Brush.linearGradient(
        colors = listOf(
            Color(0xFFEBECEF),
            Color(0xFFF7F8FA),
            Color(0xFFEBECEF),
        ),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 400f, 0f),
    )

    val userBubbleShimmer = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE2EEFA),
            Color(0xFFF2F7FD),
            Color(0xFFE2EEFA),
        ),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 400f, 0f),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), enabled = false)
            .padding(start = 18.dp, end = 18.dp, top = 22.dp).padding(bottomPadding),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // 第一轮：用户提问气泡骨架
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(44.dp)
                    .background(userBubbleShimmer, RoundedCornerShape(22.dp)),
            )
        }

        // 第一轮：AI 回复文本骨架
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(15.dp)
                    .background(neutralShimmer, RoundedCornerShape(7.5.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .height(15.dp)
                    .background(neutralShimmer, RoundedCornerShape(7.5.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(15.dp)
                    .background(neutralShimmer, RoundedCornerShape(7.5.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.38f)
                    .height(15.dp)
                    .background(neutralShimmer, RoundedCornerShape(7.5.dp)),
            )
        }

        // 第二轮：用户追问气泡骨架
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(48.dp)
                    .background(userBubbleShimmer, RoundedCornerShape(22.dp)),
            )
        }

        // 第二轮：AI 回复文本骨架
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .height(15.dp)
                    .background(neutralShimmer, RoundedCornerShape(7.5.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .height(15.dp)
                    .background(neutralShimmer, RoundedCornerShape(7.5.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(15.dp)
                    .background(neutralShimmer, RoundedCornerShape(7.5.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.46f)
                    .height(15.dp)
                    .background(neutralShimmer, RoundedCornerShape(7.5.dp)),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
