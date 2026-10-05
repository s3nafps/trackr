package com.trackr.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.MediaRepository
import com.trackr.app.data.repository.SearchFilter
import com.trackr.app.data.repository.userMessage
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaPage
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.util.PageState
import com.trackr.app.domain.util.Paginator
import com.trackr.app.domain.util.computeStreak
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeSection { MOVIES, TV, ANIME, AIRING }

data class HomeUiState(
    val sections: Map<HomeSection, PageState> = HomeSection.entries.associateWith { PageState() },
    /** The endless "Discover more" feed under the carousels. */
    val discover: PageState = PageState(),
    val discoverFilter: SearchFilter = SearchFilter.ALL,
    val continueWatching: List<ListEntry> = emptyList(),
    val listKeys: Set<String> = emptySet(),
    val streak: Int = 0,
) {
    fun section(s: HomeSection): PageState = sections[s] ?: PageState()

    val refreshing: Boolean get() = sections.values.any { it.refreshing } || discover.refreshing
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val media: MediaRepository,
    private val lists: ListRepository,
) : ViewModel() {
    private fun paginator(fetch: suspend (page: Int, force: Boolean) -> MediaPage) =
        Paginator(viewModelScope, { it.userMessage() }, fetch)

    // Each carousel keeps loading further pages as it is swiped to the end.
    private val sections: Map<HomeSection, Paginator> = HomeSection.entries.associateWith { s ->
        paginator { page, force ->
            when (s) {
                HomeSection.MOVIES -> media.trending(MediaType.MOVIE, page, force)
                HomeSection.TV -> media.trending(MediaType.TV, page, force)
                HomeSection.ANIME -> media.trending(MediaType.ANIME, page, force)
                HomeSection.AIRING -> MediaPage(media.airingThisWeek(force), hasMore = false)
            }
        }
    }

    private val discoverFilter = MutableStateFlow(SearchFilter.ALL)

    /** Starts when the feed first scrolls into view (see [discoverMore]), so opening Home costs no extra requests. */
    private val discover = paginator { page, force -> media.popular(discoverFilter.value.type, page, force) }

    private val sectionStates = combine(sections.map { (s, p) -> p.state.map { s to it } }) { it.toMap() }

    val state: StateFlow<HomeUiState> = combine(sectionStates, discover.state, discoverFilter, lists.entries) { secs, feed, filter, entries ->
        HomeUiState(
            sections = secs,
            discover = feed,
            discoverFilter = filter,
            continueWatching = entries.filter { it.status == ListStatus.WATCHING }.sortedByDescending { it.updatedAt },
            listKeys = entries.map { it.key }.toSet(),
            streak = computeStreak(entries.map { it.updatedAt }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    // Sections load in parallel and each one appears as soon as it is ready.
    init { sections.values.forEach { it.loadMore() } }

    /** Pull to refresh: reloads from page 1, keeping the current titles on screen until the new ones arrive. */
    fun refresh() {
        sections.values.forEach { it.reset(force = true, keepItems = true) }
        if (discover.state.value.pages > 0) discover.reset(force = true, keepItems = true)
    }

    fun retry(section: HomeSection) = sections.getValue(section).retry()

    fun loadMore(section: HomeSection) = sections.getValue(section).loadMore()

    fun discoverMore() = discover.loadMore()

    fun retryDiscover() = discover.retry()

    fun setDiscoverFilter(filter: SearchFilter) {
        if (filter == discoverFilter.value) return
        discoverFilter.value = filter
        discover.reset()
    }

    /** Adds [item] with the status, rating and progress picked in the sheet. */
    fun track(item: MediaItem, status: ListStatus, rating: Int?, progress: Int) {
        viewModelScope.launch { lists.save(item, status, rating, progress) }
    }

    fun plusOne(entry: ListEntry) {
        viewModelScope.launch { lists.incrementProgress(entry) }
    }
}
