package com.trackr.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

/** 2:3 poster with rounded corners, 1px border and a graceful placeholder. */
@Composable
fun PosterImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    contentDescription: String? = null,
    aspect: Float = 2f / 3f,
) {
    Box(
        modifier.aspectRatio(aspect).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Outlined.Movie, null, tint = MaterialTheme.colorScheme.outline)
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true)
                    .diskCachePolicy(CachePolicy.ENABLED).build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                loading = { ShimmerBox(Modifier.matchParentSize()) },
                error = { Icon(Icons.Outlined.Movie, null, tint = MaterialTheme.colorScheme.outline) },
            )
        }
    }
}

@Composable
fun TonalIconButtonSmall(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Carousel poster card (Home): rating chip top-left, bookmark top-right, label over bottom gradient,
 * title + meta underneath.
 */
@Composable
fun PosterCard(
    title: String,
    posterUrl: String?,
    meta: String,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
    score: Double? = null,
    overlayLabel: String? = null,
    inList: Boolean = false,
    onToggleList: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(modifier.width(width).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            PosterImage(posterUrl, Modifier.fillMaxWidth(), contentDescription = title)
            Box(
                Modifier.matchParentSize().clip(MaterialTheme.shapes.large).background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                    ),
                ),
            )
            if (score != null && score > 0) RatingBadge(score, Modifier.align(Alignment.TopStart).padding(8.dp))
            if (onToggleList != null) {
                TonalIconButtonSmall(onToggleList, Modifier.align(Alignment.TopEnd).padding(6.dp), size = 28.dp) {
                    Icon(
                        if (inList) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        if (inList) "In your list" else "Add to list",
                        Modifier.size(16.dp),
                        tint = if (inList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (!overlayLabel.isNullOrBlank()) {
                Text(
                    overlayLabel.uppercase(), Modifier.align(Alignment.BottomStart).padding(10.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                meta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
