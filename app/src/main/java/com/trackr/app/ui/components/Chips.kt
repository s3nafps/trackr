package com.trackr.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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

/** Filter chip. Selected inverts to the foreground; idle is a hairline. */
@Composable
fun TrackrChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val bg = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    val line = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    Row(
        modifier.height(30.dp).clip(PillShape).background(bg).border(1.dp, line, PillShape)
            .clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label + (count?.let { "  $it" } ?: ""), style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/** Small uppercase label for Movie / TV / Anime. */
@Composable
fun MediaTypePill(type: MediaType, modifier: Modifier = Modifier) {
    Surface(
        modifier, shape = MaterialTheme.shapes.extraSmall,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            type.shortLabel().uppercase(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Status as a hairline label with a single colored dot. */
@Composable
fun StatusBadge(status: ListStatus, modifier: Modifier = Modifier, suffix: String? = null) {
    val c = status.color()
    Row(
        modifier.height(24.dp).clip(PillShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, PillShape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(c))
        Text(status.label + (suffix?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier, container: Color = Color.Transparent) {
    Box(
        modifier.clip(PillShape).background(container)
            .border(1.dp, MaterialTheme.colorScheme.outline, PillShape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OutlinedPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier, shape = PillShape, color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
