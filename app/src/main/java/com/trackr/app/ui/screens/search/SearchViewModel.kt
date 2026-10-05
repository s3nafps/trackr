package com.trackr.app.ui.screens.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.local.UserPrefs
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.MediaRepository
import com.trackr.app.data.repository.SearchFilter
import com.trackr.app.data.repository.userMessage
import com.trackr.app.domain.model.Genre
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
    /** Narrows both lists; null means every genre. */
    val genre: Genre? = null,
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

    private val genre = MutableStateFlow(saved.get<String>("g")?.let { runCatching { Genre.valueOf(it) }.getOrNull() })

    private val results = Paginator(viewModelScope, { it.userMessage() }) { _, _ -> MediaPage(emptyList(), hasMore = false) }

    // Trending can't be narrowed by genre, so a genre switches the blank screen to that genre's popular titles.
    private val suggestions = Paginator(viewModelScope, { it.userMessage() }) { page, force ->
        val type = filter.value.type
        val g = genre.value
        when {
            g != null -> media.popular(type, page, force, g)
            type == null -> media.trendingAll(page, force)
            else -> media.trending(type, page, force)
        }
    }

    init {
        viewModelScope.launch {
            combine(query.debounce(400), filter, genre) { q, f, g -> Triple(q.trim(), f, g) }.distinctUntilChanged().collect { (q, f, g) ->
                if (q.length < 2) {
                    results.clear()
                } else {
                    results.reset { page, _ ->
                        val found = media.search(q, f, page)
                        if (page == 1 && found.items.isNotEmpty()) prefs.addRecent(q)
                        // Search APIs can't filter by genre, so results are narrowed here, page by page.
                        if (g == null) found else found.copy(items = found.items.filter(g::matches))
                    }
                }
            }
        }
        viewModelScope.launch { combine(filter, genre) { f, g -> f to g }.distinctUntilChanged().collect { suggestions.reset() } }
    }

    val state: StateFlow<SearchUiState> = combine(
        combine(query, filter, genre, ::Triple), results.state, suggestions.state, prefs.recentSearches, lists.entries,
    ) { (q, f, g), res, sug, recents, entries ->
        SearchUiState(q, f, g, if (q.trim().length < 2) PageState.of(emptyList()) else res, sug, recents, entries.associateBy { it.key })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState(query.value, filter.value, genre.value))

    fun setQuery(q: String) { query.value = q; saved["q"] = q }
    fun setFilter(f: SearchFilter) {
        // Drop a genre the new type doesn't have (e.g. Crime for anime) before the lists reload.
        if (genre.value?.appliesTo(f.type) == false) setGenre(null)
        filter.value = f; saved["f"] = f.name
    }
    fun setGenre(g: Genre?) { genre.value = g; saved["g"] = g?.name }
    fun retry() { results.retry(); suggestions.retry() }
    fun loadMoreResults() = results.loadMore()
    fun loadMoreSuggestions() = suggestions.loadMore()
    fun applyRecent(q: String) = setQuery(q)
    fun removeRecent(q: String) { viewModelScope.launch { prefs.removeRecent(q) } }
    fun clearRecents() { viewModelScope.launch { prefs.clearRecent() } }

    /** Season sizes of [item], so the sheet can offer progress by season (see [MediaRepository.seasonsOf]). */
    suspend fun seasonsFor(item: MediaItem): List<Int> = media.seasonsOf(item)

    /** Adds [item] with the status, rating and progress picked in the sheet (or updates it if it's already listed). */
    fun track(item: MediaItem, status: ListStatus, rating: Int?, progress: Int) {
        viewModelScope.launch { lists.save(item, status, rating, progress) }
    }
    fun markCompleted(item: MediaItem, entry: ListEntry) {
        viewModelScope.launch { lists.update(entry, status = ListStatus.COMPLETED, progress = entry.totalEpisodes ?: item.totalEpisodes ?: entry.progress) }
    }
}
