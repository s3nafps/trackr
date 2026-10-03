package com.trackr.app.ui.components

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -400f, targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "x",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val hi = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier.clip(shape).background(
            Brush.linearGradient(listOf(base, hi, base), start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x + 400f, 200f)),
        ),
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
                ShimmerBox(Modifier.width(148.dp).height(222.dp))
                ShimmerBox(Modifier.width(110.dp).height(14.dp), MaterialTheme.shapes.small)
                ShimmerBox(Modifier.width(70.dp).height(12.dp), MaterialTheme.shapes.small)
            }
        }
    }
}

@Composable
fun ListRowSkeleton(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ShimmerBox(Modifier.width(80.dp).height(120.dp), MaterialTheme.shapes.medium)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerBox(Modifier.width(90.dp).height(14.dp), MaterialTheme.shapes.small)
            ShimmerBox(Modifier.fillMaxWidth(0.8f).height(18.dp), MaterialTheme.shapes.small)
            ShimmerBox(Modifier.fillMaxWidth().height(14.dp), MaterialTheme.shapes.small)
        }
    }
}
