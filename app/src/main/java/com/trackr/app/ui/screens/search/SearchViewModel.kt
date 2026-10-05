package com.trackr.app.ui.screens.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.local.UserPrefs
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.MediaRepository
import com.trackr.app.data.repository.SearchFilter
import com.trackr.app.data.repository.userMessage
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaPage
import com.trackr.app.domain.util.PageState
import com.trackr.app.domain.util.Paginator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    /** Results for a non-blank query, a page at a time. */
    val results: PageState = PageState.of(emptyList()),
    /** Trending titles shown while the query is blank, also paged. */
    val suggestions: PageState = PageState(),
    val recents: List<String> = emptyList(),
    val entries: Map<String, ListEntry> = emptyMap(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val media: MediaRepository,
    private val lists: ListRepository,
    private val prefs: UserPrefs,
) : ViewModel() {
    private val query = MutableStateFlow(saved.get<String>("q").orEmpty())
    private val filter = MutableStateFlow(
        saved.get<String>("f")?.let { runCatching { SearchFilter.valueOf(it) }.getOrNull() } ?: SearchFilter.ALL,
    )

    private val results = Paginator(viewModelScope, { it.userMessage() }) { _, _ -> MediaPage(emptyList(), hasMore = false) }

    private val suggestions = Paginator(viewModelScope, { it.userMessage() }) { page, force ->
        when (val type = filter.value.type) {
            null -> media.trendingAll(page, force)
            else -> media.trending(type, page, force)
        }
    }

    init {
        viewModelScope.launch {
            combine(query.debounce(400), filter) { q, f -> q.trim() to f }.distinctUntilChanged().collect { (q, f) ->
                if (q.length < 2) {
                    results.clear()
                } else {
                    results.reset { page, _ ->
                        media.search(q, f, page).also { if (page == 1 && it.items.isNotEmpty()) prefs.addRecent(q) }
                    }
                }
            }
        }
        viewModelScope.launch { filter.collect { suggestions.reset() } }
    }

    val state: StateFlow<SearchUiState> = combine(
        query, filter, results.state, suggestions.state, combine(prefs.recentSearches, lists.entries) { r, e -> r to e },
    ) { q, f, res, sug, (recents, entries) ->
        SearchUiState(q, f, if (q.trim().length < 2) PageState.of(emptyList()) else res, sug, recents, entries.associateBy { it.key })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState(query.value, filter.value))

    fun setQuery(q: String) { query.value = q; saved["q"] = q }
    fun setFilter(f: SearchFilter) { filter.value = f; saved["f"] = f.name }
    fun retry() { results.retry(); suggestions.retry() }
    fun loadMoreResults() = results.loadMore()
    fun loadMoreSuggestions() = suggestions.loadMore()
    fun applyRecent(q: String) = setQuery(q)
    fun removeRecent(q: String) { viewModelScope.launch { prefs.removeRecent(q) } }
    fun clearRecents() { viewModelScope.launch { prefs.clearRecent() } }

    fun quickAdd(item: MediaItem) { viewModelScope.launch { lists.save(item, ListStatus.PLAN_TO_WATCH, null, 0) } }
    fun markCompleted(item: MediaItem, entry: ListEntry) {
        viewModelScope.launch { lists.update(entry, status = ListStatus.COMPLETED, progress = entry.totalEpisodes ?: item.totalEpisodes ?: entry.progress) }
    }
}
