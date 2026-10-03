package com.trackr.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaType
import com.trackr.app.ui.theme.PillShape
import com.trackr.app.ui.theme.color
import com.trackr.app.ui.theme.shortLabel

/** Filter chip: selected = primary fill + check, unselected = surface-container-high. */
@Composable
fun TrackrChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.height(36.dp).clip(PillShape).background(bg).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = fg)
        Text(label + (count?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** Small uppercase pill for Movie / TV / Anime. */
@Composable
fun MediaTypePill(type: MediaType, modifier: Modifier = Modifier) {
    Surface(
        modifier, shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            type.shortLabel().uppercase(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Status badge: 15% tint of the status colour, solid text and a leading dot. */
@Composable
fun StatusBadge(status: ListStatus, modifier: Modifier = Modifier, suffix: String? = null) {
    val c = status.color()
    Row(
        modifier.height(26.dp).clip(PillShape).background(c.copy(alpha = 0.15f)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c))
        Text(status.label + (suffix?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.labelMedium, color = c)
    }
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    Box(modifier.clip(PillShape).background(container).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OutlinedPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier, shape = PillShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}
