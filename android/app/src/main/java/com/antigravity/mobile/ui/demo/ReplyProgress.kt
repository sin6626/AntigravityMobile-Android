package com.antigravity.mobile.ui.demo

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun ReplyProgress(waiting: Boolean, stopping: Boolean, animate: Boolean, modifier: Modifier = Modifier) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val motionScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val moving = animate && !stopping && lifecycle.isAtLeast(Lifecycle.State.RESUMED) && motionScale > 0f
    val phase: State<Float> = if (moving) {
        rememberInfiniteTransition(label = "reply progress").animateFloat(0f, 1f,
            infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "reply phase")
    } else remember { mutableFloatStateOf(0f) }
    Row(modifier.padding(top = 12.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        // Keep the slot after the first body arrives so the status text never shifts sideways.
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(waiting, enter = EnterTransition.None,
                exit = fadeOut(tween(260, easing = FastOutSlowInEasing)) +
                    scaleOut(tween(260, easing = FastOutSlowInEasing), targetScale = .72f)) {
                Box(Modifier.testTag("reply-wait-ribbon")) {
                    ReplyRibbon(moving, phase)
                }
            }
        }
        Text(if (stopping) "正在停止…" else "正在回复…", color = SecondaryInk,
            fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = .2.sp,
            modifier = Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val bandWidth = size.width * .9f
                    val sheen = Brush.linearGradient(
                        0f to Color.Transparent, .28f to Color(0xFF537DAE),
                        .5f to Color(0xFF9CAAC2), .72f to Color(0xFF8974B0), 1f to Color.Transparent,
                        start = Offset.Zero, end = Offset(bandWidth, size.height * .35f))
                    onDrawWithContent {
                        drawContent()
                        // Tint only the glyphs; sharing the ribbon clock keeps layout and semantics still.
                        if (moving) translate(left = phase.value * (size.width + bandWidth) - bandWidth) {
                            drawRect(sheen, size = Size(bandWidth, size.height), blendMode = BlendMode.SrcAtop)
                        }
                    }
                })
    }
}

@Composable
private fun ReplyRibbon(moving: Boolean, phase: State<Float>) {
    val path = remember { Path() }
    val highlight = remember { Path() }
    val measure = remember { PathMeasure() }
    val colors = remember { listOf(Color(0xFF32BFCF), Color(0xFF328BFF), Color(0xFF9570E8)) }
    Canvas(Modifier.size(32.dp).then(if (moving) Modifier.testTag("reply-wait-motion") else Modifier)
        .clearAndSetSemantics {}) {
        // Read the clock only while drawing; neither text layout nor Markdown runs on animation frames.
        val cycle = phase.value % 1f
        val wave = sin(cycle * 2f * PI.toFloat())
        val bend = .035f * wave
        val w = size.width
        val h = size.height
        path.reset()
        path.moveTo(.5f * w, .5f * h)
        path.cubicTo(.68f * w, (.12f + bend) * h, .94f * w, (.12f + bend) * h, .94f * w, (.5f + bend) * h)
        path.cubicTo(.94f * w, (.88f + bend) * h, .68f * w, (.88f + bend) * h, .5f * w, .5f * h)
        path.cubicTo(.32f * w, (.12f - bend) * h, .06f * w, (.12f - bend) * h, .06f * w, (.5f - bend) * h)
        path.cubicTo(.06f * w, (.88f - bend) * h, .32f * w, (.88f - bend) * h, .5f * w, .5f * h)
        path.close()
        val brush = Brush.linearGradient(colors, Offset(0f, h * (.3f + .15f * wave)), Offset(w, h * .7f))
        scale(.91f + .035f * wave) {
            rotate(8f * wave) {
                drawPath(path, brush, alpha = .045f, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, brush, alpha = .10f, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, brush, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                measure.setPath(path, false)
                val length = measure.length
                val start = cycle * length
                val end = start + length * .24f
                highlight.reset()
                measure.getSegment(start, minOf(end, length), highlight, true)
                if (end > length) measure.getSegment(0f, end - length, highlight, false)
                drawPath(highlight, Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color(0xFFC9F0FF)),
                    measure.getPosition(start), measure.getPosition(end % length)), alpha = .85f,
                    style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
