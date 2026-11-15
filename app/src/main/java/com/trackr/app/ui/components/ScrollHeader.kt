package com.trackr.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.statusBarsPadding
import com.trackr.app.ui.theme.TrackrSpace

private const val BarMs = 180
private const val AwayPx = 24

/** 0 at rest, 1 once the header has scrolled away. The change eases over [BarMs]. */
@Composable
fun LazyListState.barCollapse(): Float = collapseFlag(firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > AwayPx)

@Composable
fun LazyGridState.barCollapse(): Float = collapseFlag(firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > AwayPx)

@Composable
fun ScrollState.barCollapse(): Float = collapseFlag(value > AwayPx)

@Composable
private fun collapseFlag(away: Boolean): Float {
    val value by animateFloatAsState(if (away) 1f else 0f, tween(BarMs), label = "bar")
    return value
}

/**
 * Compact title drawn over the canvas, including behind the status bar.
 * Invisible at rest, so it does not form a bar until content passes underneath.
 */
@Composable
fun CollapsedTitleBar(
    collapse: Float,
    title: String,
    avatarUrl: String?,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (collapse <= 0.01f) return
    Column(
        modifier.fillMaxWidth()
            .graphicsLayer { alpha = collapse }
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().height(48.dp).padding(horizontal = TrackrSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TrackrSpace.sm),
        ) {
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            actions()
            UserAvatar(avatarUrl, size = 28.dp, onClick = onAvatarClick)
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
    }
}

/** Large title row that sits in the page, with the avatar beside the words. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    avatarUrl: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = TrackrSpace.lg,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = TrackrSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TrackrSpace.sm),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (eyebrow != null) {
                Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
        if (onAvatarClick != null) UserAvatar(avatarUrl, size = 36.dp, onClick = onAvatarClick)
    }
}

/**
 * Shrinks a pinned block as the list under it scrolls, then gives that scroll back
 * once the block is closed. Used where a field has to stay put (Search).
 */
class HeaderCollapse(private val maxPx: Float) {
    var offsetPx by mutableFloatStateOf(0f)
    val fraction: Float get() = if (maxPx <= 0f) 0f else (offsetPx / maxPx).coerceIn(0f, 1f)
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f) return Offset.Zero
            val next = (offsetPx - available.y).coerceIn(0f, maxPx)
            val consumed = next - offsetPx
            offsetPx = next
            return Offset(0f, -consumed)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f) return Offset.Zero
            val next = (offsetPx - available.y).coerceIn(0f, maxPx)
            val used = offsetPx - next
            offsetPx = next
            return Offset(0f, used)
        }
    }
}

@Composable
fun rememberHeaderCollapse(expanded: androidx.compose.ui.unit.Dp, startCollapsed: Boolean): HeaderCollapse {
    val px = with(LocalDensity.current) { expanded.toPx() }
    return remember(px) { HeaderCollapse(px).also { if (startCollapsed) it.offsetPx = px } }
}
