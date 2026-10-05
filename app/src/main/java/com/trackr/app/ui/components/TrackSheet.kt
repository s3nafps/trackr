package com.trackr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.util.SeasonProgress
import com.trackr.app.ui.theme.RatingAmber
import com.trackr.app.ui.theme.SheetShape
import com.trackr.app.ui.theme.color

val RatingLabels = listOf(
    "Appalling", "Horrible", "Very Bad", "Bad", "Average", "Fine", "Good", "Great", "Amazing", "Masterpiece",
)

/**
 * Track-status bottom sheet (Stitch "Track Status"): watch-state segmented control, 10-star rating,
 * episode stepper and Save. Used from Detail and My List (edit).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(
    title: String,
    initial: ListEntry?,
    totalEpisodes: Int?,
    showProgress: Boolean,
    onDismiss: () -> Unit,
    onSave: (status: ListStatus, rating: Int?, progress: Int) -> Unit,
    onRemove: (() -> Unit)? = null,
    /** Episode counts of a TV show's seasons: with more than one, progress is picked as season + episode. */
    seasons: List<Int> = emptyList(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var status by rememberSaveable { mutableStateOf(initial?.status ?: ListStatus.PLAN_TO_WATCH) }
    var rating by rememberSaveable { mutableIntStateOf(initial?.rating ?: 0) }
    var progress by rememberSaveable { mutableIntStateOf(initial?.progress ?: 0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("TRACK STATUS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2)
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, "Close", Modifier.size(20.dp)) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Watch State", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SegmentedControl(
                    options = ListStatus.entries, selected = status, label = { it.shortLabel },
                    onSelect = {
                        status = it
                        if (it == ListStatus.COMPLETED) {
                            // With seasons known, every season is watched; otherwise the episode total.
                            val all = if (showProgress && SeasonProgress.bySeason(seasons)) seasons.sum() else totalEpisodes
                            if (all != null) progress = all
                        }
                    },
                    textColor = { it.color() },
                )
            }

            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.StarBorder, null, tint = RatingAmber)
                        Text("Your Rating", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        if (rating == 0) "Not rated" else "$rating / 10 (${RatingLabels[rating - 1]})",
                        style = MaterialTheme.typography.labelLarge, color = RatingAmber,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    (1..10).forEach { n ->
                        val on = n <= rating
                        Box(
                            Modifier.weight(1f).aspectRatio(0.8f).clip(MaterialTheme.shapes.small)
                                .background(if (on) RatingAmber.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { rating = if (rating == n) 0 else n },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (on) Icons.Filled.Star else Icons.Outlined.StarBorder, "$n",
                                Modifier.size(16.dp), tint = if (on) RatingAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (showProgress && SeasonProgress.bySeason(seasons)) {
                SeasonEpisodePicker(progress, seasons) { progress = it }
            } else if (showProgress) {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Episode Progress", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (totalEpisodes != null) "of $totalEpisodes episodes" else "episodes watched",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Stepper(
                        value = progress, onMinus = { if (progress > 0) progress-- },
                        onPlus = { if (totalEpisodes == null || progress < totalEpisodes) progress++ },
                    )
                }
            }

            Button(
                onClick = {
                    onSave(status, rating.takeIf { it > 0 }, progress)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Filled.Check, null, Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Save Changes", style = MaterialTheme.typography.titleMedium)
            }
            if (onRemove != null) {
                TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    Text("Remove from list", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** [TrackSheet] for a title picked from a list (a search result or a poster): its status, rating and progress are chosen before it is saved. */
@Composable
fun TrackSheet(
    item: MediaItem,
    entry: ListEntry?,
    onDismiss: () -> Unit,
    onSave: (status: ListStatus, rating: Int?, progress: Int) -> Unit,
    seasons: List<Int> = emptyList(),
) = TrackSheet(
    title = item.title, initial = entry, totalEpisodes = entry?.totalEpisodes ?: item.totalEpisodes,
    showProgress = item.type != MediaType.MOVIE, onDismiss = onDismiss, onSave = onSave, seasons = seasons,
)

/**
 * Progress by season, over the show-wide episode count. The chips are "watched through this season" (tap S1 for a show
 * dropped after its first season; tap the last chip again to undo it), and the stepper counts episodes of the season
 * after the finished ones.
 */
@Composable
private fun SeasonEpisodePicker(progress: Int, seasons: List<Int>, onChange: (Int) -> Unit) {
    val done = SeasonProgress.finished(progress, seasons)
    val current = SeasonProgress.inProgress(progress, seasons)
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text("Seasons watched", style = MaterialTheme.typography.titleSmall)
            Text(
                SeasonProgress.summary(progress, seasons),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons.size) { i ->
                val n = i + 1
                TrackrChip(
                    "S$n", selected = n <= done,
                    onClick = { onChange(SeasonProgress.through(if (n == done) n - 1 else n, seasons)) },
                )
            }
        }
        if (current != null) {
            val (season, episode) = current
            val size = seasons[season - 1]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Season $season", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (episode == 0) "not started · $size episodes" else "of $size episodes",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Stepping past the last episode finishes the season, and the stepper moves on to the next one.
                Stepper(value = episode, onMinus = { if (episode > 0) onChange(progress - 1) }, onPlus = { onChange(progress + 1) })
            }
        }
    }
}

/** Dual-button segmented module: − value +. */
@Composable
fun Stepper(value: Int, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clickable(onClick = onMinus), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Remove, "Decrease") }
        Text(value.toString(), Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.titleMedium)
        Box(Modifier.size(44.dp).clickable(onClick = onPlus), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, "Increase") }
    }
}
