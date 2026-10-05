package com.trackr.app.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.airing.AiringRefreshScheduler
import com.trackr.app.data.local.AiringEntity
import com.trackr.app.data.local.UserPrefs
import com.trackr.app.data.repository.AiringRepository
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.util.CalendarDay
import com.trackr.app.domain.util.EpisodeCalendar
import com.trackr.app.domain.util.SeasonProgress
import com.trackr.app.domain.util.UpcomingEpisode
import com.trackr.app.ui.components.EmptyState
import com.trackr.app.ui.components.LocalProfile
import com.trackr.app.ui.components.MediaTypePill
import com.trackr.app.ui.components.PosterImage
import com.trackr.app.ui.components.TrackrTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

data class CalendarUiState(
    val days: List<CalendarDay> = emptyList(),
    val loading: Boolean = true,
    /** New-episode alerts off means no schedule is kept, so the calendar can't fill. */
    val alertsOn: Boolean = true,
)

/** Upcoming episodes of the titles being watched (and planned ones with alerts on), from the airing schedule. */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    airing: AiringRepository,
    lists: ListRepository,
    prefs: UserPrefs,
    private val refresher: AiringRefreshScheduler,
) : ViewModel() {
    val state: StateFlow<CalendarUiState> = combine(airing.upcoming, lists.entries, prefs.airingEnabled) { rows, entries, on ->
        val posters = entries.associate { it.key to it.posterUrl }
        CalendarUiState(EpisodeCalendar.days(rows.map { it.toUpcoming(posters) }, System.currentTimeMillis()), loading = false, alertsOn = on)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    /** Asks the sources again (the schedule otherwise refreshes on its own a few times a day). */
    fun refresh() = refresher.refreshNow()

    companion object {
        fun AiringEntity.toUpcoming(posters: Map<String, String?>) = UpcomingEpisode(
            MediaSource.fromKey(source), externalId, MediaType.fromKey(mediaType), title, posters["$source:$externalId"],
            season, episode, airAt, exactTime = precision == "TIME",
        )
    }
}

@Composable
fun CalendarScreen(onBack: () -> Unit, onOpen: (source: String, type: String, id: String) -> Unit, vm: CalendarViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    CalendarContent(state, onBack, onOpen, vm::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarContent(
    state: CalendarUiState,
    onBack: () -> Unit,
    onOpen: (source: String, type: String, id: String) -> Unit,
    onRefresh: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Column(Modifier.fillMaxSize()) {
        TrackrTopBar("Upcoming", LocalProfile.current?.avatarUrl, {}, onBack = onBack)
        PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            if (!state.loading && state.days.isEmpty()) {
                EmptyState(
                    Icons.Outlined.EventAvailable, "Nothing scheduled yet",
                    if (state.alertsOn) "New episodes of what you're watching appear here as soon as they're announced."
                    else "New episode alerts are off. Turn them on in Settings and your upcoming episodes appear here.",
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                    state.days.forEach { day ->
                        item(key = "day:${day.date}") {
                            Text(
                                day.label, Modifier.padding(top = 20.dp, bottom = 8.dp),
                                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(day.episodes, key = { "${day.date}:${it.source.key}:${it.externalId}" }) { ep ->
                            EpisodeRow(ep, zone, onClick = { onOpen(ep.source.key, ep.type.key, ep.externalId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: UpcomingEpisode, zone: ZoneId, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(ep.posterUrl, Modifier.width(44.dp), shape = MaterialTheme.shapes.small, contentDescription = ep.title)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MediaTypePill(ep.type)
            Text(ep.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                SeasonProgress.upcoming(ep.season, ep.episode) ?: if (ep.type == MediaType.MOVIE) "Release" else "New episode",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ep.exactTime) {
            Text(
                Instant.ofEpochMilli(ep.airAt).atZone(zone).toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
