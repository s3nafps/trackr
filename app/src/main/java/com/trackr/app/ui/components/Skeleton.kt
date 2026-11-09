package com.trackr.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Theme-aware skeleton block. A standing sheen plus a moving band, so a still frame is not a flat fill. */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    outlined: Boolean = false,
) {
    val shift by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "shift",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val sheen = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val peak = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
    val frame = if (outlined) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
    } else {
        Modifier
    }
    Box(
        modifier.clip(shape).then(frame).drawBehind {
            drawRect(base)
            drawRect(
                brush = Brush.linearGradient(
                    listOf(Color.Transparent, sheen, Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
            val band = size.width * 0.55f
            val x = -band + (size.width + band) * shift
            drawRect(
                brush = Brush.linearGradient(
                    listOf(Color.Transparent, peak, Color.Transparent),
                    start = Offset(x, 0f),
                    end = Offset(x + band, size.height * 0.4f),
                ),
            )
        },
    )
}

@Composable
fun PosterCarouselSkeleton(modifier: Modifier = Modifier, count: Int = 4) {
    LazyRow(
        modifier, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(count) {
            Column(Modifier.width(148.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(Modifier.width(148.dp).height(222.dp), MaterialTheme.shapes.small, outlined = true)
                ShimmerBox(Modifier.width(110.dp).height(12.dp), MaterialTheme.shapes.extraSmall)
                ShimmerBox(Modifier.width(72.dp).height(10.dp), MaterialTheme.shapes.extraSmall)
            }
        }
    }
}

@Composable
fun ListRowSkeleton(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ShimmerBox(Modifier.width(72.dp).height(108.dp), MaterialTheme.shapes.small, outlined = true)
        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerBox(Modifier.width(56.dp).height(10.dp), MaterialTheme.shapes.extraSmall)
            ShimmerBox(Modifier.fillMaxWidth(0.72f).height(14.dp), MaterialTheme.shapes.extraSmall)
            ShimmerBox(Modifier.fillMaxWidth(0.9f).height(10.dp), MaterialTheme.shapes.extraSmall)
            ShimmerBox(Modifier.fillMaxWidth(0.4f).height(10.dp), MaterialTheme.shapes.extraSmall)
        }
    }
}
