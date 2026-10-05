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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Recommend
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackr.app.data.repository.SearchFilter
import com.trackr.app.domain.model.Genre
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.util.PageState
import com.trackr.app.ui.components.AiringCard
import com.trackr.app.ui.components.ContinueWatchingCard
import com.trackr.app.ui.components.ErrorState
import com.trackr.app.ui.components.GenreChips
import com.trackr.app.ui.components.LocalProfile
import com.trackr.app.ui.components.OfflineNote
import com.trackr.app.ui.components.PagingFooter
import com.trackr.app.ui.components.PosterCard
import com.trackr.app.ui.components.PosterCarouselSkeleton
import com.trackr.app.ui.components.rememberNotificationPermission
import com.trackr.app.ui.components.SectionHeader
import com.trackr.app.ui.components.TrackSheet
import com.trackr.app.ui.components.TrackrChip
import com.trackr.app.ui.components.TrackrTopBar
import com.trackr.app.ui.components.metaLine
import com.trackr.app.ui.theme.PillShape
import com.trackr.app.ui.theme.RatingAmber
import java.util.Calendar

private val CarouselPadding = PaddingValues(horizontal = 16.dp)
private val SectionGap = 24.dp
/** A 148dp-wide poster at 2:3, the height of carousel cards without their captions. */
private val PosterHeight = 222.dp

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
    val ensureNotifications = rememberNotificationPermission {}
    HomeContent(
        state, LocalProfile.current?.avatarUrl, LocalProfile.current?.username,
        onRefresh = vm::refresh, onRetry = vm::retry, onLoadMore = vm::loadMore,
        onTrack = { item, status, rating, progress ->
            vm.track(item, status, rating, progress)
            // New-episode alerts are on for Watching titles, so ask now rather than when the first one is due.
            if (status == ListStatus.WATCHING) ensureNotifications {}
        },
        onPlusOne = vm::plusOne,
        onDiscoverFilter = vm::setDiscoverFilter, onDiscoverGenre = vm::setDiscoverGenre, onDiscoverMore = vm::discoverMore, onDiscoverRetry = vm::retryDiscover,
        onOpenDetail = onOpenDetail, onOpenEntry = onOpenEntry, onSeeAllWatching = onSeeAllWatching,
        onExplore = onExplore, onOpenProfile = onOpenProfile,
        seasonsFor = vm::seasonsFor,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    avatarUrl: String?,
    username: String?,
    onRefresh: () -> Unit,
    onRetry: (HomeSection) -> Unit,
    onLoadMore: (HomeSection) -> Unit,
    onTrack: (MediaItem, ListStatus, rating: Int?, progress: Int) -> Unit,
    onPlusOne: (com.trackr.app.domain.model.ListEntry) -> Unit,
    onDiscoverFilter: (SearchFilter) -> Unit,
    onDiscoverGenre: (Genre?) -> Unit,
    onDiscoverMore: () -> Unit,
    onDiscoverRetry: () -> Unit,
    onOpenDetail: (MediaItem) -> Unit,
    onOpenEntry: (source: String, type: String, id: String) -> Unit,
    onSeeAllWatching: () -> Unit,
    onExplore: (MediaType?) -> Unit,
    onOpenProfile: () -> Unit,
    /** Season sizes of a TV show, to pick progress by season in the status sheet. */
    seasonsFor: suspend (MediaItem) -> List<Int> = { emptyList() },
) {
    val columns = maxOf(3, (LocalConfiguration.current.screenWidthDp - 20) / 116)
    val discoverRows = remember(state.discover.items, columns) { state.discover.items.chunked(columns) }
    // The title whose status sheet is open; the bookmark on a poster that isn't in the list opens it.
    var tracking by remember { mutableStateOf<MediaItem?>(null) }
    val onAdd: (MediaItem) -> Unit = { tracking = it }
    Column(Modifier.fillMaxSize()) {
        TrackrTopBar("Home", avatarUrl, onOpenProfile)
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item("greeting") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("WELCOME BACK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                buildString { append(greeting()); username?.let { append(", $it") }; append(" 👋") },
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
                }

                if (state.offline) item("offline") { OfflineNote(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) }

                if (state.continueWatching.isNotEmpty()) {
                    item("continue") {
                        Column(Modifier.padding(top = SectionGap), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeader("Continue Watching", icon = Icons.Outlined.PlayCircle, actionLabel = "See all (${state.continueWatching.size})", onAction = onSeeAllWatching)
                            LazyRow(contentPadding = CarouselPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.continueWatching, key = { it.key }) { e ->
                                    ContinueWatchingCard(
                                        e, onClick = { onOpenEntry(e.source.key, e.mediaType.key, e.externalId) }, onPlusOne = { onPlusOne(e) },
                                        seasons = state.seasons[e.key].orEmpty(),
                                    )
                                }
                            }
                        }
                    }
                }

                val forYou = state.forYou
                if (forYou.items.isNotEmpty() || forYou.loading) {
                    item("for-you") {
                        Column(Modifier.padding(top = SectionGap), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                SectionHeader("For You", icon = Icons.Outlined.Recommend)
                                if (forYou.because.isNotEmpty()) {
                                    Text(
                                        "Because you liked ${forYou.because.take(3).joinToString(", ")}",
                                        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (forYou.items.isEmpty()) {
                                PosterCarouselSkeleton()
                            } else {
                                LazyRow(contentPadding = CarouselPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(forYou.items, key = { it.key }) { item -> MediaPoster(item, item.key in state.listKeys, onOpenDetail, onAdd) }
                                }
                            }
                        }
                    }
                }

                item("movies") {
                    PosterSection("Trending Movies", Icons.Outlined.Movie, "Explore", { onExplore(MediaType.MOVIE) }, state.section(HomeSection.MOVIES), state.listKeys, { onRetry(HomeSection.MOVIES) }, { onLoadMore(HomeSection.MOVIES) }, onOpenDetail, onAdd)
                }
                item("tv") {
                    PosterSection("Trending TV Shows", Icons.Outlined.Tv, "See all", { onExplore(MediaType.TV) }, state.section(HomeSection.TV), state.listKeys, { onRetry(HomeSection.TV) }, { onLoadMore(HomeSection.TV) }, onOpenDetail, onAdd)
                }
                item("anime") {
                    PosterSection("Trending Anime", Icons.Outlined.AutoAwesome, "See ranking", { onExplore(MediaType.ANIME) }, state.section(HomeSection.ANIME), state.listKeys, { onRetry(HomeSection.ANIME) }, { onLoadMore(HomeSection.ANIME) }, onOpenDetail, onAdd)
                }

                item("airing") {
                    Column(Modifier.padding(top = SectionGap), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(end = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader("Airing This Week", icon = Icons.Outlined.CalendarMonth, modifier = Modifier.weight(1f))
                            Text(
                                "Local time", Modifier.clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val a = state.section(HomeSection.AIRING)
                        when {
                            a.items.isEmpty() && a.error != null -> ErrorState(a.error, { onRetry(HomeSection.AIRING) })
                            a.items.isEmpty() && !a.endReached -> PosterCarouselSkeleton(count = 2)
                            a.items.isEmpty() -> Text("Nothing scheduled this week.", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> LazyRow(contentPadding = CarouselPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(a.items, key = { it.key }) { AiringCard(it, onClick = { onOpenDetail(it) }) }
                            }
                        }
                    }
                }

                discoverFeed(state, discoverRows, columns, onDiscoverFilter, onDiscoverGenre, onDiscoverMore, onDiscoverRetry, onOpenDetail, onAdd)
            }
        }
    }
    tracking?.let { item ->
        // The title's seasons arrive a moment after the sheet opens; until then it just counts episodes.
        var seasons by remember(item.key) { mutableStateOf(emptyList<Int>()) }
        LaunchedEffect(item.key) { seasons = seasonsFor(item) }
        TrackSheet(
            item, entry = null, onDismiss = { tracking = null },
            onSave = { status, rating, progress -> onTrack(item, status, rating, progress); tracking = null },
            seasons = seasons,
        )
    }
}

/** "Discover More": popular titles in a grid that keeps loading pages as you scroll down. */
private fun LazyListScope.discoverFeed(
    state: HomeUiState,
    rows: List<List<MediaItem>>,
    columns: Int,
    onFilter: (SearchFilter) -> Unit,
    onGenre: (Genre?) -> Unit,
    onMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onAdd: (MediaItem) -> Unit,
) {
    val feed = state.discover
    item("discover-header") {
        Column(Modifier.padding(top = SectionGap), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Discover More", icon = Icons.Outlined.Explore)
            LazyRow(contentPadding = CarouselPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SearchFilter.entries) { f -> TrackrChip(f.label, selected = state.discoverFilter == f, onClick = { onFilter(f) }) }
            }
            GenreChips(state.discoverFilter.type, state.discoverGenre, onGenre)
        }
    }
    when {
        feed.items.isEmpty() && feed.error != null -> item("discover-error") { ErrorState(feed.error, onRetry) }
        feed.items.isEmpty() && feed.endReached -> item("discover-empty") {
            Text("Nothing to show here yet.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        feed.items.isEmpty() -> item("discover-loading") {
            // The feed's first page is only requested once it scrolls into view.
            LaunchedEffect(feed.loading) { if (!feed.loading) onMore() }
            PosterCarouselSkeleton(Modifier.padding(top = 16.dp), count = columns)
        }
        else -> {
            items(rows, key = { "discover:${it.first().key}" }) { row ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { MediaPoster(it, it.key in state.listKeys, onOpen, onAdd, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item("discover-footer") { PagingFooter(feed, onMore, onRetry, Modifier.fillMaxWidth().padding(16.dp)) }
        }
    }
}

@Composable
private fun PosterSection(
    title: String,
    icon: ImageVector,
    action: String,
    onAction: () -> Unit,
    load: PageState,
    listKeys: Set<String>,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onAdd: (MediaItem) -> Unit,
) {
    Column(Modifier.padding(top = SectionGap), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title, icon = icon, actionLabel = action, onAction = onAction)
        when {
            load.items.isEmpty() && load.error != null -> ErrorState(load.error, onRetry)
            load.items.isEmpty() && !load.endReached -> PosterCarouselSkeleton()
            else -> LazyRow(contentPadding = CarouselPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(load.items, key = { it.key }) { item -> MediaPoster(item, item.key in listKeys, onOpen, onAdd) }
                // Swiping to the end of the row loads the next page.
                if (!load.endReached || load.error != null) item("more") {
                    PagingFooter(load, onLoadMore, onRetry, Modifier.height(PosterHeight).widthIn(min = 64.dp, max = 160.dp))
                }
            }
        }
    }
}

@Composable
private fun MediaPoster(item: MediaItem, inList: Boolean, onOpen: (MediaItem) -> Unit, onAdd: (MediaItem) -> Unit, modifier: Modifier = Modifier) {
    PosterCard(
        title = item.title, posterUrl = item.posterUrl, meta = item.metaLine(), modifier = modifier,
        score = item.score, overlayLabel = item.genres.firstOrNull(), inList = inList,
        onToggleList = { if (inList) onOpen(item) else onAdd(item) },
        onClick = { onOpen(item) },
    )
}
