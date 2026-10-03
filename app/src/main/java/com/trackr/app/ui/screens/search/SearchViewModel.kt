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
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    /** Results for a non-blank query. */
    val results: Load<List<MediaItem>> = Load.Success(emptyList()),
    /** Trending suggestions shown when the query is blank. */
    val suggestions: Load<List<MediaItem>> = Load.Loading,
    val recents: List<String> = emptyList(),
    val entries: Map<String, ListEntry> = emptyMap(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
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

    private val retryTick = MutableStateFlow(0)

    private val results: Flow<Load<List<MediaItem>>> = combine(query.debounce(400).distinctUntilChanged(), filter, retryTick) { q, f, t -> Triple(q.trim(), f, t) }
        .distinctUntilChanged()
        .flatMapLatest { (q, f, _) ->
            flow {
                if (q.length < 2) { emit(Load.Success(emptyList())); return@flow }
                emit(Load.Loading)
                try {
                    val r = media.search(q, f)
                    emit(Load.Success(r))
                    if (r.isNotEmpty()) prefs.addRecent(q)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emit(Load.Failure(e.userMessage()))
                }
            }
        }

    private val suggestions: Flow<Load<List<MediaItem>>> = combine(filter, retryTick) { f, _ -> f }.flatMapLatest { f ->
        flow {
            emit(Load.Loading)
            try {
                emit(Load.Success(trendingFor(f)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(Load.Failure(e.userMessage()))
            }
        }
    }

    private suspend fun trendingFor(f: SearchFilter): List<MediaItem> = when (f) {
        SearchFilter.MOVIES -> media.trendingMovies()
        SearchFilter.TV -> media.trendingTv()
        SearchFilter.ANIME -> media.trendingAnime()
        SearchFilter.ALL -> MediaRepository.interleave(
            listOf(runCatching { media.trendingMovies() }.getOrDefault(emptyList()).take(6),
                runCatching { media.trendingTv() }.getOrDefault(emptyList()).take(6),
                runCatching { media.trendingAnime() }.getOrDefault(emptyList()).take(6)),
        ).ifEmpty { media.trendingMovies() }
    }

    val state: StateFlow<SearchUiState> = combine(
        query, filter, results, suggestions, combine(prefs.recentSearches, lists.entries) { r, e -> r to e },
    ) { q, f, res, sug, (recents, entries) ->
        SearchUiState(q, f, if (q.trim().length < 2) Load.Success(emptyList()) else res, sug, recents, entries.associateBy { it.key })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState(query.value, filter.value))

    fun setQuery(q: String) { query.value = q; saved["q"] = q }
    fun setFilter(f: SearchFilter) { filter.value = f; saved["f"] = f.name }
    fun retry() { retryTick.value++ }
    fun applyRecent(q: String) = setQuery(q)
    fun removeRecent(q: String) { viewModelScope.launch { prefs.removeRecent(q) } }
    fun clearRecents() { viewModelScope.launch { prefs.clearRecent() } }

    fun quickAdd(item: MediaItem) { viewModelScope.launch { lists.save(item, ListStatus.PLAN_TO_WATCH, null, 0) } }
    fun markCompleted(item: MediaItem, entry: ListEntry) {
        viewModelScope.launch { lists.update(entry, status = ListStatus.COMPLETED, progress = entry.totalEpisodes ?: item.totalEpisodes ?: entry.progress) }
    }
}
