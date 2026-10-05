package com.trackr.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackr.app.data.repository.SearchFilter
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.util.PageState
import com.trackr.app.ui.components.CountBadge
import com.trackr.app.ui.components.EmptyState
import com.trackr.app.ui.components.ErrorState
import com.trackr.app.ui.components.ListRowSkeleton
import com.trackr.app.ui.components.LocalProfile
import com.trackr.app.ui.components.MediaResultCard
import com.trackr.app.ui.components.PagingFooter
import com.trackr.app.ui.components.rememberNotificationPermission
import com.trackr.app.ui.components.TrackSheet
import com.trackr.app.ui.components.TrackrChip
import com.trackr.app.ui.components.TrackrTopBar
import com.trackr.app.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    initialFilter: SearchFilter?,
    onOpenDetail: (MediaItem) -> Unit,
    onOpenProfile: () -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ensureNotifications = rememberNotificationPermission {}
    LaunchedEffect(initialFilter) { initialFilter?.let(vm::setFilter) }
    SearchContent(
        state,
        SearchActions(
            vm::setQuery, vm::setFilter, vm::applyRecent, vm::removeRecent, vm::clearRecents,
            track = { item, status, rating, progress ->
                vm.track(item, status, rating, progress)
                // New-episode alerts are on for Watching titles, so ask now rather than when the first one is due.
                if (status == ListStatus.WATCHING) ensureNotifications {}
            },
            vm::markCompleted, vm::retry, vm::loadMoreResults, vm::loadMoreSuggestions,
        ),
        onOpenDetail, onOpenProfile,
    )
}

class SearchActions(
    val setQuery: (String) -> Unit,
    val setFilter: (SearchFilter) -> Unit,
    val applyRecent: (String) -> Unit,
    val removeRecent: (String) -> Unit,
    val clearRecents: () -> Unit,
    val track: (MediaItem, ListStatus, rating: Int?, progress: Int) -> Unit,
    val markCompleted: (MediaItem, com.trackr.app.domain.model.ListEntry) -> Unit,
    val retry: () -> Unit,
    val loadMoreResults: () -> Unit,
    val loadMoreSuggestions: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchContent(
    state: SearchUiState,
    vm: SearchActions,
    onOpenDetail: (MediaItem) -> Unit,
    onOpenProfile: () -> Unit,
    avatarUrl: String? = LocalProfile.current?.avatarUrl,
) {
    val focus = LocalFocusManager.current
    // The title whose status sheet is open.
    var tracking by remember { mutableStateOf<MediaItem?>(null) }
    Column(Modifier.fillMaxSize().imePadding()) {
        TrackrTopBar("Search", avatarUrl, onOpenProfile)
        TextField(
            value = state.query, onValueChange = vm.setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(PillShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PillShape),
            placeholder = { Text("Search movies, shows, anime") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    Icon(Icons.Filled.Close, "Clear", Modifier.clip(PillShape).clickable { vm.setQuery("") }.padding(8.dp))
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SearchFilter.entries) { f -> TrackrChip(f.label, selected = state.filter == f, onClick = { vm.setFilter(f) }) }
        }

        val blank = state.query.trim().length < 2
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (blank) {
                if (state.recents.isNotEmpty()) item("recents") {
                    Column(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.History, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Recent Searches", style = MaterialTheme.typography.titleSmall)
                            }
                            Text("Clear all", Modifier.clickable { vm.clearRecents() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.recents.forEach { r ->
                                Row(
                                    Modifier.clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { vm.applyRecent(r) }.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(r, style = MaterialTheme.typography.labelLarge)
                                    Icon(Icons.Filled.Close, "Remove $r", Modifier.size(16.dp).clickable { vm.removeRecent(r) }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                item("trending-title") { Text("Trending now", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp)) }
                resultItems(state.suggestions, state, onOpenDetail, { tracking = it }, vm, vm.loadMoreSuggestions, emptyText = "Nothing trending right now.")
            } else {
                item("results-title") {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Top Matches", style = MaterialTheme.typography.headlineSmall)
                            if (state.results.items.isNotEmpty()) CountBadge(state.results.items.size)
                        }
                        Text("Sorted by relevance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                resultItems(state.results, state, onOpenDetail, { tracking = it }, vm, vm.loadMoreResults, emptyText = "No results for \"${state.query.trim()}\".")
            }
        }
    }
    tracking?.let { item ->
        TrackSheet(
            item, state.entries[item.key], onDismiss = { tracking = null },
            onSave = { status, rating, progress -> vm.track(item, status, rating, progress); tracking = null },
        )
    }
}

/** A paged list of results: skeletons, then rows, then a footer that loads the next page when scrolled to. */
private fun androidx.compose.foundation.lazy.LazyListScope.resultItems(
    page: PageState,
    state: SearchUiState,
    onOpen: (MediaItem) -> Unit,
    onTrack: (MediaItem) -> Unit,
    vm: SearchActions,
    onLoadMore: () -> Unit,
    emptyText: String,
) {
    when {
        page.items.isEmpty() && page.error != null -> item("err") { ErrorState(page.error, onRetry = vm.retry) }
        page.items.isEmpty() && page.endReached -> item("empty") {
            EmptyState(Icons.Outlined.SearchOff, "Nothing found", emptyText)
        }
        page.items.isEmpty() && (page.loading || page.pages == 0) -> items(4, key = { "sk$it" }) { ListRowSkeleton(Modifier.padding(vertical = 4.dp)) }
        else -> {
            items(page.items, key = { it.key }) { item ->
                val entry = state.entries[item.key]
                MediaResultCard(
                    item, entry, onClick = { onOpen(item) },
                    onTrack = { onTrack(item) },
                    onMarkCompleted = { entry?.let { vm.markCompleted(item, it) } },
                )
            }
            if (!page.endReached || page.error != null) item("more") {
                PagingFooter(page, onLoadMore, vm.retry, Modifier.fillMaxWidth().padding(8.dp))
            }
        }
    }
}
