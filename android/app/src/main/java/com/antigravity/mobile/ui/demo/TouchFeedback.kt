package com.antigravity.mobile.ui.demo

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer

internal fun Modifier.quietClickable(
    shape: Shape = RectangleShape,
    enabled: Boolean = true,
    pressedColor: Color? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed && pressedColor == null) 0.72f else 1f, tween(90), label = "press")
    val background by animateColorAsState(if (pressed) pressedColor ?: Color.Transparent else Color.Transparent,
        tween(90), label = "press background")
    this.clip(shape).background(background).graphicsLayer(alpha = alpha)
        .combinedClickable(interactionSource = interaction, indication = null, enabled = enabled,
            onLongClick = onLongClick, onClick = onClick)
}

/** Readiness delays the first entrance; it never hides content already on screen. */
@Composable
internal fun contentEntrance(visit: Any, offset: Dp = 0.dp, ready: Boolean = true): Modifier {
    val progress = remember(visit) { Animatable(0f) }
    val visible by remember(progress) { derivedStateOf { progress.value > 0f } }
    val density = LocalDensity.current
    val distance = with(density) { if (fontScale >= 1.3f) 0f else offset.toPx() }
    LaunchedEffect(visit, ready) {
        if (ready || progress.value > 0f) progress.animateTo(1f, tween(if (offset == 0.dp) 140 else 180))
    }
    return Modifier.graphicsLayer {
        alpha = progress.value
        translationX = distance * (1f - progress.value)
    }.then(if (!visible) Modifier.clearAndSetSemantics {} else Modifier)
}
