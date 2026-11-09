package com.trackr.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

private const val PressMs = 160

/** Short press: the card or row eases down slightly and loses a little opacity. */
@Composable
fun Modifier.trackrPressClick(onClick: () -> Unit, onClickLabel: String? = null): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(PressMs), label = "pressScale")
    val alpha by animateFloatAsState(if (pressed) 0.72f else 1f, tween(PressMs), label = "pressAlpha")
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }.clickable(
        interactionSource = interaction,
        indication = null,
        onClickLabel = onClickLabel,
        onClick = onClick,
    )
}
