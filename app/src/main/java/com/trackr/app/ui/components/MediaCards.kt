package com.trackr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaType
import com.trackr.app.ui.theme.PillShape
import com.trackr.app.ui.theme.StatusCompleted
import com.trackr.app.ui.theme.StatusDropped
import com.trackr.app.ui.theme.StatusWatching
import java.text.DateFormat
import java.util.Date

fun MediaItem.metaLine(): String = buildList {
    year?.let { add(it.toString()) }
    when {
        type == MediaType.MOVIE && runtimeMinutes != null -> add(formatRuntime(runtimeMinutes))
        totalEpisodes != null && type != MediaType.MOVIE -> add("$totalEpisodes ep")
        runtimeMinutes != null -> add("${runtimeMinutes}m")
    }
}.joinToString(" · ")

fun formatRuntime(minutes: Int): String = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

/** Search result row (Stitch "Top Matches" card). */
@Composable
fun MediaResultCard(
    item: MediaItem,
    entry: ListEntry?,
    onClick: () -> Unit,
    onQuickAdd: () -> Unit,
    onMarkCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            PosterImage(item.posterUrl, Modifier.width(80.dp), shape = MaterialTheme.shapes.small, contentDescription = item.title)
            item.score?.let { RatingBadge(it, Modifier.align(Alignment.TopStart).padding(4.dp)) }
        }
        Column(Modifier.weight(1f).height(120.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MediaTypePill(item.type)
                    Text(item.metaLine(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                }
                Text(item.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.overview ?: item.genres.joinToString(" / "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (entry == null) {
                    Text(
                        item.subtitle ?: item.genres.firstOrNull().orEmpty(),
                        Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        Modifier.height(36.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(onClick = onQuickAdd).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.BookmarkAdd, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Add to List", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                } else {
                    StatusBadge(
                        entry.status,
                        suffix = if (entry.status == ListStatus.WATCHING && entry.totalEpisodes != null && entry.mediaType != MediaType.MOVIE)
                            "Ep ${entry.progress}/${entry.totalEpisodes}" else null,
                    )
                    if (entry.status != ListStatus.COMPLETED) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable(onClick = onMarkCompleted),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Check, "Mark completed", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                }
            }
        }
    }
}

/** Horizontal "Continue Watching" progress card. */
@Composable
fun ContinueWatchingCard(
    entry: ListEntry,
    onClick: () -> Unit,
    onPlusOne: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 300.dp,
) {
    Column(
        modifier.width(width).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PosterImage(entry.posterUrl, Modifier.width(64.dp), shape = MaterialTheme.shapes.small, contentDescription = entry.title)
            Column(Modifier.weight(1f).height(96.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    MediaTypePill(entry.mediaType)
                    Text(entry.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        entry.totalEpisodes?.let { "Ep ${entry.progress} of $it" } ?: "Ep ${entry.progress}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (entry.totalEpisodes != null) "${(entry.fraction * 100).toInt()}% done" else "",
                        style = MaterialTheme.typography.labelMedium, color = StatusWatching,
                    )
                    PlusOneButton(onPlusOne)
                }
            }
        }
        TrackrProgressBar(entry.fraction, Modifier.padding(horizontal = 0.dp))
    }
}

@Composable
fun PlusOneButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(32.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
        Text("1 Ep", style = MaterialTheme.typography.labelLarge)
    }
}

fun airingLabel(airingAtEpoch: Long?, nowMillis: Long = System.currentTimeMillis()): Pair<String, Boolean>? {
    val at = airingAtEpoch?.times(1000) ?: return null
    val delta = at - nowMillis
    return when {
        delta < 0 -> "Aired" to false
        delta < 3600_000 -> "Air in ${(delta / 60_000).coerceAtLeast(1)}m" to true
        delta < 24 * 3600_000 -> "Air in ${delta / 3600_000}h" to true
        else -> {
            val day = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(Date(at))
            val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))
            (if (delta < 48 * 3600_000) "Tomorrow" else day) + " · $time" to false
        }
    }
}

/** "Airing this week" card. */
@Composable
fun AiringCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 250.dp) {
    val label = airingLabel(item.airingAtEpoch)
    Row(
        modifier.width(width).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(item.posterUrl, Modifier.width(56.dp), shape = MaterialTheme.shapes.small, contentDescription = item.title)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (label != null) {
                val soon = label.second
                Row(
                    Modifier.clip(PillShape).background((if (soon) StatusDropped else MaterialTheme.colorScheme.outline).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (soon) Box(Modifier.size(6.dp).clip(CircleShape).background(StatusDropped))
                    Text(label.first, style = MaterialTheme.typography.labelSmall, color = if (soon) StatusDropped else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.airingEpisode?.let { Text("Episode $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
