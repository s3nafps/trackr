package com.trackr.app.ui.screens.mylist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaType
import com.trackr.app.ui.components.CountBadge
import com.trackr.app.ui.components.EmptyState
import com.trackr.app.ui.components.LocalProfile
import com.trackr.app.ui.components.MediaTypePill
import com.trackr.app.ui.components.PlusOneButton
import com.trackr.app.ui.components.PosterImage
import com.trackr.app.ui.components.RatingBadge
import com.trackr.app.ui.components.StatusBadge
import com.trackr.app.ui.components.TrackSheet
import com.trackr.app.ui.components.TrackrChip
import com.trackr.app.ui.components.TrackrProgressBar
import com.trackr.app.ui.components.TrackrTopBar
import com.trackr.app.ui.theme.PillShape
import com.trackr.app.ui.theme.color
import com.trackr.app.ui.theme.shortLabel

@Composable
fun MyListScreen(
    onOpenEntry: (ListEntry) -> Unit,
    onOpenProfile: () -> Unit,
    onDiscover: () -> Unit,
    vm: MyListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    MyListContent(
        state, LocalProfile.current?.avatarUrl, onOpenProfile, onDiscover, onOpenEntry,
        vm::setStatus, vm::setType, vm::setSort, vm::toggleGrid, { vm.refresh() }, vm::plusOne, vm::remove, vm::save,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListContent(
    state: MyListUiState,
    avatarUrl: String?,
    onOpenProfile: () -> Unit,
    onDiscover: () -> Unit,
    onOpenEntry: (ListEntry) -> Unit,
    onStatus: (ListStatus) -> Unit,
    onType: (MediaType?) -> Unit,
    onSort: (ListSort) -> Unit,
    onToggleGrid: () -> Unit,
    onRefresh: () -> Unit,
    onPlusOne: (ListEntry) -> Unit,
    onRemove: (ListEntry) -> Unit,
    onSave: (ListEntry, ListStatus, Int?, Int) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val editEntry = state.items.firstOrNull { it.key == editing }

    Column(Modifier.fillMaxSize()) {
        TrackrTopBar("My List", avatarUrl, onOpenProfile)
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            val header: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("My Library", style = MaterialTheme.typography.displayMedium)
                            Text("${state.total} titles", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SortMenu(state.sort, onSort)
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onToggleGrid),
                            contentAlignment = Alignment.Center,
                        ) { Icon(if (state.grid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView, if (state.grid) "List view" else "Grid view") }
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ListStatus.entries) { s ->
                            val sel = s == state.status
                            Row(
                                Modifier.height(44.dp).clip(PillShape)
                                    .background(if (sel) s.color().copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer)
                                    .then(if (sel) Modifier.border(1.dp, s.color().copy(alpha = 0.5f), PillShape) else Modifier)
                                    .clickable { onStatus(s) }.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (sel) Box(Modifier.size(8.dp).clip(CircleShape).background(s.color()))
                                Text(s.label, style = MaterialTheme.typography.labelLarge, color = if (sel) s.color() else MaterialTheme.colorScheme.onSurface)
                                CountBadge(state.statusCounts[s] ?: 0)
                            }
                        }
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf<MediaType?>(null) + MediaType.entries) { t ->
                            TrackrChip(
                                when (t) { null -> "All"; MediaType.MOVIE -> "Movies"; MediaType.TV -> "TV Shows"; MediaType.ANIME -> "Anime" },
                                selected = state.type == t, onClick = { onType(t) }, count = state.typeCounts[t],
                            )
                        }
                    }
                    state.message?.let {
                        Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (state.items.isEmpty()) {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { header() }
                    item {
                        EmptyState(
                            Icons.Outlined.VideoLibrary,
                            if (state.total == 0) "Your library is empty" else "Nothing in ${state.status.label}",
                            if (state.total == 0) "Search for movies, shows and anime and add them to start tracking."
                            else "Titles you mark as ${state.status.label.lowercase()} will show up here.",
                            actionLabel = "Discover titles", onAction = onDiscover,
                        )
                    }
                }
            } else if (state.grid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 11.dp, end = 11.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(0.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        // header content has its own 16dp margins: undo the grid's 11dp content padding
                        Box(
                            Modifier.layout { m, c ->
                                val extra = 11.dp.roundToPx()
                                val p = m.measure(c.copy(maxWidth = c.maxWidth + 2 * extra, minWidth = c.maxWidth + 2 * extra))
                                layout(c.maxWidth, p.height) { p.place(-extra, 0) }
                            },
                        ) { header() }
                    }
                    items(state.items, key = { it.key }) { e ->
                        Column(
                            Modifier.padding(horizontal = 0.dp).clickable { onOpenEntry(e) }.padding(start = 0.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.padding(horizontal = 5.dp)) {
                                PosterImage(e.posterUrl, Modifier.fillMaxWidth(), contentDescription = e.title)
                                e.rating?.let { RatingBadge(it.toDouble(), Modifier.align(Alignment.TopStart).padding(6.dp)) }
                                if (e.status == ListStatus.WATCHING && e.totalEpisodes != null) {
                                    TrackrProgressBar(e.fraction, Modifier.align(Alignment.BottomCenter).padding(8.dp))
                                }
                            }
                            Text(e.title, Modifier.padding(horizontal = 5.dp), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { header() }
                    items(state.items, key = { it.key }) { e ->
                        ListEntryCard(
                            e, Modifier.padding(horizontal = 16.dp), onClick = { onOpenEntry(e) }, onPlusOne = { onPlusOne(e) },
                            onEdit = { editing = e.key }, onRemove = { onRemove(e) }, airsIn = state.airsIn[e.key],
                        )
                    }
                }
            }
        }
    }

    if (editEntry != null) {
        TrackSheet(
            title = editEntry.title, initial = editEntry, totalEpisodes = editEntry.totalEpisodes,
            showProgress = editEntry.mediaType != MediaType.MOVIE,
            onDismiss = { editing = null },
            onSave = { s, r, p -> onSave(editEntry, s, r, p); editing = null },
            onRemove = { onRemove(editEntry); editing = null },
        )
    }
}

@Composable
private fun SortMenu(sort: ListSort, onSort: (ListSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(44.dp).clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { open = true }.padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sort.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, "Sort")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ListSort.entries.forEach { s ->
                DropdownMenuItem(text = { Text(s.label) }, onClick = { onSort(s); open = false })
            }
        }
    }
}

/** My List row (Stitch "Progress Card"): poster, title, rating, progress and +1 Ep. */
@Composable
fun ListEntryCard(
    e: ListEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPlusOne: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    airsIn: String? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), MaterialTheme.shapes.large)
            .clickable(onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box {
            PosterImage(e.posterUrl, Modifier.width(88.dp), shape = MaterialTheme.shapes.medium, contentDescription = e.title)
            MediaTypePill(e.mediaType, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Column(Modifier.weight(1f).height(132.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(e.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    airsIn?.let {
                        Text(
                            it, Modifier.clip(PillShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                e.rating?.let { RatingBadge(it.toDouble(), Modifier.padding(start = 8.dp), outOfTen = false, scrim = false) }
                Box {
                    Icon(Icons.Filled.MoreVert, "More", Modifier.size(28.dp).clip(CircleShape).clickable { menu = true }.padding(4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Remove", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onRemove() })
                    }
                }
            }
            if (e.status == ListStatus.WATCHING && e.mediaType != MediaType.MOVIE) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            buildString { append("Ep "); append(e.progress); e.totalEpisodes?.let { append(" of $it") } },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (e.totalEpisodes != null) Text("${(e.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { TrackrProgressBar(e.fraction) }
                        PlusOneButton(onPlusOne)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(e.status)
                    if (e.mediaType != MediaType.MOVIE && e.progress > 0) {
                        Text(
                            e.totalEpisodes?.let { "${e.progress}/$it ep" } ?: "${e.progress} ep",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
