package com.trackr.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.meta.TitleMetaRepository
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.MediaRepository
import com.trackr.app.data.repository.SearchFilter
import com.trackr.app.data.repository.userMessage
import com.trackr.app.domain.model.Genre
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaPage
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.util.ForYou
import com.trackr.app.domain.util.PageState
import com.trackr.app.domain.util.Paginator
import com.trackr.app.domain.util.computeStreak
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeSection { MOVIES, TV, ANIME, AIRING }

/** The "For You" row: recommendations for the titles the user liked, ranked by [ForYou.rank]. */
data class ForYouRow(
    val items: List<MediaItem> = emptyList(),
    /** Titles of the liked titles the picks are based on, for the "Because you liked …" line. */
    val because: List<String> = emptyList(),
    val loading: Boolean = false,
)

data class HomeUiState(
    val sections: Map<HomeSection, PageState> = HomeSection.entries.associateWith { PageState() },
    /** The endless "Discover more" feed under the carousels. */
    val discover: PageState = PageState(),
    val discoverFilter: SearchFilter = SearchFilter.ALL,
    val discoverGenre: Genre? = null,
    val forYou: ForYouRow = ForYouRow(),
    val continueWatching: List<ListEntry> = emptyList(),
    /** Season episode counts per entry key, for "S2 · E5" on Continue Watching. */
    val seasons: Map<String, List<Int>> = emptyMap(),
    val listKeys: Set<String> = emptySet(),
    val streak: Int = 0,
) {
    fun section(s: HomeSection): PageState = sections[s] ?: PageState()

    val refreshing: Boolean get() = sections.values.any { it.refreshing } || discover.refreshing

    /** Some of what's shown is the saved copy, because the sources couldn't be reached. */
    val offline: Boolean get() = sections.values.any { it.stale } || discover.stale
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val media: MediaRepository,
    private val lists: ListRepository,
    titleMeta: TitleMetaRepository,
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
    private val discoverGenre = MutableStateFlow<Genre?>(null)

    /** Starts when the feed first scrolls into view (see [discoverMore]), so opening Home costs no extra requests. */
    private val discover = paginator { page, force -> media.popular(discoverFilter.value.type, page, force, discoverGenre.value) }

    private val sectionStates = combine(sections.map { (s, p) -> p.state.map { s to it } }) { it.toMap() }

    /** Bumped by pull to refresh; the flag says whether to bypass the cache. */
    private val forYouReload = MutableStateFlow(0 to false)

    /** Each seed's recommendations, reloaded when the liked titles change. Ranking happens in [state]. */
    private val forYouRecs: Flow<Pair<List<ListEntry>, Load<List<List<MediaItem>>>>> =
        combine(
            lists.entries.map { ForYou.seeds(it) }.distinctUntilChanged { a, b -> a.map { it.key } == b.map { it.key } },
            forYouReload,
        ) { seeds, reload -> seeds to reload.second }
            .transformLatest { (seeds, force) ->
                if (seeds.isEmpty()) {
                    emit(seeds to Load.Success(emptyList()))
                    return@transformLatest
                }
                emit(seeds to Load.Loading)
                // A seed whose recommendations fail just contributes nothing; the row is a bonus, not worth an error.
                val recs = coroutineScope {
                    seeds.map { e -> async { runCatching { media.recommendationsFor(e, force) }.getOrDefault(emptyList()) } }.awaitAll()
                }
                emit(seeds to Load.Success(recs))
            }

    private val discoverOptions = combine(discoverFilter, discoverGenre) { f, g -> f to g }

    val state: StateFlow<HomeUiState> = combine(
        sectionStates, discover.state, discoverOptions, combine(lists.entries, titleMeta.all, ::Pair), forYouRecs,
    ) { secs, feed, (filter, genre), (entries, meta), (seeds, recs) ->
        val listKeys = entries.map { it.key }.toSet()
        HomeUiState(
            sections = secs,
            discover = feed,
            discoverFilter = filter,
            discoverGenre = genre,
            forYou = ForYouRow(
                items = (recs as? Load.Success)?.data?.let { ForYou.rank(it, listKeys) }.orEmpty(),
                because = seeds.map { it.title },
                loading = recs is Load.Loading && seeds.isNotEmpty(),
            ),
            continueWatching = entries.filter { it.status == ListStatus.WATCHING }.sortedByDescending { it.updatedAt },
            seasons = meta.mapValues { it.value.seasonEpisodes }.filterValues { it.isNotEmpty() },
            listKeys = listKeys,
            streak = computeStreak(entries.map { it.updatedAt }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    // Sections load in parallel and each one appears as soon as it is ready.
    init { sections.values.forEach { it.loadMore() } }

    /** Pull to refresh: reloads from page 1, keeping the current titles on screen until the new ones arrive. */
    fun refresh() {
        sections.values.forEach { it.reset(force = true, keepItems = true) }
        if (discover.state.value.pages > 0) discover.reset(force = true, keepItems = true)
        forYouReload.update { (n, _) -> n + 1 to true }
    }

    fun retry(section: HomeSection) = sections.getValue(section).retry()

    fun loadMore(section: HomeSection) = sections.getValue(section).loadMore()

    fun discoverMore() = discover.loadMore()

    fun retryDiscover() = discover.retry()

    fun setDiscoverFilter(filter: SearchFilter) {
        if (filter == discoverFilter.value) return
        discoverFilter.value = filter
        // A genre the new type doesn't have (e.g. Crime for anime) would leave the feed empty.
        if (discoverGenre.value?.appliesTo(filter.type) == false) discoverGenre.value = null
        discover.reset()
    }

    /** Null shows every genre. */
    fun setDiscoverGenre(genre: Genre?) {
        if (genre == discoverGenre.value) return
        discoverGenre.value = genre
        discover.reset()
    }

    /** Season sizes of [item], so the sheet can offer progress by season (see [MediaRepository.seasonsOf]). */
    suspend fun seasonsFor(item: MediaItem): List<Int> = media.seasonsOf(item)

    /** Adds [item] with the status, rating and progress picked in the sheet. */
    fun track(item: MediaItem, status: ListStatus, rating: Int?, progress: Int) {
        viewModelScope.launch { lists.save(item, status, rating, progress) }
    }

    fun plusOne(entry: ListEntry) {
        viewModelScope.launch { lists.incrementProgress(entry) }
    }
}
