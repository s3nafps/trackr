package com.trackr.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaType
import com.trackr.app.ui.components.AiringCard
import com.trackr.app.ui.components.ContinueWatchingCard
import com.trackr.app.ui.components.ErrorState
import com.trackr.app.ui.components.LocalProfile
import com.trackr.app.ui.components.PosterCard
import com.trackr.app.ui.components.PosterCarouselSkeleton
import com.trackr.app.ui.components.SectionHeader
import com.trackr.app.ui.components.TrackrTopBar
import com.trackr.app.ui.components.metaLine
import com.trackr.app.ui.theme.PillShape
import com.trackr.app.ui.theme.RatingAmber
import java.util.Calendar

private val CarouselPadding = PaddingValues(horizontal = 16.dp)

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDetail: (MediaItem) -> Unit,
    onOpenEntry: (source: String, type: String, id: String) -> Unit,
    onSeeAllWatching: () -> Unit,
    onExplore: (MediaType?) -> Unit,
    onOpenProfile: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val profile = LocalProfile.current

    Column(Modifier.fillMaxSize()) {
        TrackrTopBar("Home", profile?.avatarUrl, onOpenProfile)
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("WELCOME BACK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            buildString { append(greeting()); profile?.username?.let { append(", $it") }; append(" 👋") },
                            style = MaterialTheme.typography.headlineMedium, maxLines = 1,
                        )
                    }
                    if (state.streak > 0) {
                        Row(
                            Modifier.clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("🔥")
                            Text("${state.streak} day${if (state.streak == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = RatingAmber)
                        }
                    }
                }

                if (state.continueWatching.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("Continue Watching", icon = Icons.Outlined.PlayCircle, actionLabel = "See all (${state.continueWatching.size})", onAction = onSeeAllWatching)
                        LazyRow(contentPadding = CarouselPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.continueWatching, key = { it.key }) { e ->
                                ContinueWatchingCard(e, onClick = { onOpenEntry(e.source.key, e.mediaType.key, e.externalId) }, onPlusOne = { vm.plusOne(e) })
                            }
                        }
                    }
                }

                PosterSection("Trending Movies", Icons.Outlined.Movie, "Explore", { onExplore(MediaType.MOVIE) }, state.section(HomeSection.MOVIES), state.listKeys, { vm.retry(HomeSection.MOVIES) }, onOpenDetail, vm::quickAdd)
                PosterSection("Trending TV Shows", Icons.Outlined.Tv, "See all", { onExplore(MediaType.TV) }, state.section(HomeSection.TV), state.listKeys, { vm.retry(HomeSection.TV) }, onOpenDetail, vm::quickAdd)
                PosterSection("Trending Anime", Icons.Outlined.AutoAwesome, "See ranking", { onExplore(MediaType.ANIME) }, state.section(HomeSection.ANIME), state.listKeys, { vm.retry(HomeSection.ANIME) }, onOpenDetail, vm::quickAdd)

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(end = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Airing This Week", icon = Icons.Outlined.CalendarMonth, modifier = Modifier.weight(1f))
                        Text(
                            "Local time", Modifier.clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when (val a = state.section(HomeSection.AIRING)) {
                        Load.Loading -> PosterCarouselSkeleton(count = 2)
                        is Load.Failure -> ErrorState(a.message, { vm.retry(HomeSection.AIRING) })
                        is Load.Success -> if (a.data.isEmpty()) {
                            Text("Nothing scheduled this week.", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else LazyRow(contentPadding = CarouselPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(a.data, key = { it.key }) { AiringCard(it, onClick = { onOpenDetail(it) }) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PosterSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    action: String,
    onAction: () -> Unit,
    load: Load<List<MediaItem>>,
    listKeys: Set<String>,
    onRetry: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onQuickAdd: (MediaItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title, icon = icon, actionLabel = action, onAction = onAction)
        when (load) {
            Load.Loading -> PosterCarouselSkeleton()
            is Load.Failure -> ErrorState(load.message, onRetry)
            is Load.Success -> LazyRow(contentPadding = CarouselPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(load.data, key = { it.key }) { item ->
                    val inList = item.key in listKeys
                    PosterCard(
                        title = item.title, posterUrl = item.posterUrl, meta = item.metaLine(),
                        score = item.score, overlayLabel = item.genres.firstOrNull(), inList = inList,
                        onToggleList = { if (inList) onOpen(item) else onQuickAdd(item) },
                        onClick = { onOpen(item) },
                    )
                }
            }
        }
    }
}
