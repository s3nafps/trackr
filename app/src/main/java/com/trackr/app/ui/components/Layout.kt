package com.trackr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trackr.app.ui.theme.TrackrSpace

/** Title on the left, actions and avatar on the right, closed by a hairline. */
@Composable
fun TrackrTopBar(
    screenName: String,
    avatarUrl: String?,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(end = TrackrSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }
            Text(
                screenName,
                modifier = Modifier.weight(1f).padding(start = if (onBack != null) 0.dp else TrackrSpace.lg, end = TrackrSpace.sm),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing()
            UserAvatar(avatarUrl, Modifier.padding(start = TrackrSpace.xs), size = 28.dp, onClick = onAvatarClick)
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun UserAvatar(url: String?, modifier: Modifier = Modifier, size: Dp = 40.dp, onClick: (() -> Unit)? = null) {
    val base = modifier.size(size).clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    Box(if (onClick != null) base.clickable(onClick = onClick) else base, contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Filled.Person, null, Modifier.size(size * 0.6f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(url, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** Section title row with optional leading icon and trailing action text. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TrackrSpace.sm)) {
            if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        if (actionLabel != null) {
            Text(
                actionLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(enabled = onAction != null) { onAction?.invoke() },
            )
        }
    }
}

/** Segmented selector (e.g. Watching | Completed | Plan | Dropped). */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: ((T) -> Color)? = null,
    textColor: ((T) -> Color)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .clip(MaterialTheme.shapes.small)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { opt ->
            val sel = opt == selected
            val fill = if (sel) (selectedColor?.invoke(opt) ?: MaterialTheme.colorScheme.surfaceContainerHigh) else Color.Transparent
            Box(
                Modifier.weight(1f).height(32.dp).clip(MaterialTheme.shapes.extraSmall)
                    .background(fill)
                    .clickable { onSelect(opt) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(opt), style = MaterialTheme.typography.labelMedium,
                    color = if (sel) MaterialTheme.colorScheme.onSurface else (textColor?.invoke(opt) ?: MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Stat tile used on Profile & friend profile. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .clip(MaterialTheme.shapes.small)
            .padding(TrackrSpace.md),
        verticalArrangement = Arrangement.spacedBy(TrackrSpace.xs),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(TrackrSpace.xs)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
            if (unit != null) Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}
