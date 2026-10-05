package com.trackr.app.data

import androidx.lifecycle.SavedStateHandle
import com.trackr.app.data.local.UserPrefs
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.MediaRepository
import com.trackr.app.data.repository.SearchFilter
import com.trackr.app.domain.model.Genre
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaPage
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.ui.screens.home.HomeSection
import com.trackr.app.ui.screens.home.HomeViewModel
import com.trackr.app.ui.screens.search.SearchViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Home and Search keep loading pages as the user scrolls. */
@OptIn(ExperimentalCoroutinesApi::class)
class EndlessBrowsingTest {
    private val main = UnconfinedTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(main)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun item(prefix: String, n: Int) = MediaItem(MediaSource.TMDB, "$prefix$n", MediaType.MOVIE, "$prefix $n", null)
    private fun page(prefix: String, n: Int) = MediaPage(listOf(item(prefix, n * 2 - 1), item(prefix, n * 2)), hasMore = true)
    private fun ids(items: List<MediaItem>) = items.map { it.externalId }

    private val media = mockk<MediaRepository> {
        coEvery { trending(any(), any(), any()) } answers { page(firstArg<MediaType>().key, secondArg()) }
        coEvery { airingThisWeek(any(), any()) } returns emptyList()
        coEvery { popular(any(), any(), any(), any()) } answers {
            page("pop-${firstArg<MediaType?>()?.key ?: "all"}-${arg<Genre?>(3)?.name?.lowercase()?.plus("-").orEmpty()}", secondArg())
        }
        coEvery { recommendationsFor(any(), any()) } answers { listOf(item("rec-${firstArg<ListEntry>().externalId}-", 1), item("common", 0)) }
        coEvery { trendingAll(any(), any()) } answers { page("all", firstArg()) }
        coEvery { search(any(), any(), any<Int>()) } answers { page("q-${firstArg<String>()}-", thirdArg()) }
    }
    private val lists = mockk<ListRepository>(relaxed = true) { every { entries } returns flowOf(emptyList()) }
    private val prefs = mockk<UserPrefs>(relaxed = true) { every { recentSearches } returns flowOf(emptyList()) }

    @Test fun `home loads the carousels, not the discover feed, until it is scrolled to`() = runTest(main) {
        val vm = HomeViewModel(media, lists)
        backgroundScope.launch { vm.state.collect {} }
        assertEquals(listOf("movie1", "movie2"), ids(vm.state.value.section(HomeSection.MOVIES).items))
        assertTrue(vm.state.value.discover.firstLoad)
        coVerify(exactly = 0) { media.popular(any(), any(), any(), any()) }

        vm.loadMore(HomeSection.MOVIES)
        assertEquals(listOf("movie1", "movie2", "movie3", "movie4"), ids(vm.state.value.section(HomeSection.MOVIES).items))
        assertEquals(2, vm.state.value.section(HomeSection.TV).items.size)
    }

    @Test fun `the discover feed pages endlessly and restarts when its filter changes`() = runTest(main) {
        val vm = HomeViewModel(media, lists)
        backgroundScope.launch { vm.state.collect {} }
        vm.discoverMore(); vm.discoverMore()
        assertEquals(listOf("pop-all-1", "pop-all-2", "pop-all-3", "pop-all-4"), ids(vm.state.value.discover.items))

        vm.setDiscoverFilter(SearchFilter.ANIME)
        assertEquals(SearchFilter.ANIME, vm.state.value.discoverFilter)
        assertEquals(listOf("pop-anime-1", "pop-anime-2"), ids(vm.state.value.discover.items))
    }

    @Test fun `pull to refresh reloads from page 1 without the cache`() = runTest(main) {
        val vm = HomeViewModel(media, lists)
        backgroundScope.launch { vm.state.collect {} }
        vm.loadMore(HomeSection.TV)
        vm.refresh()
        coVerify { media.trending(MediaType.TV, 1, true) }
        assertEquals(listOf("tv1", "tv2"), ids(vm.state.value.section(HomeSection.TV).items))
        coVerify(exactly = 0) { media.popular(any(), any(), any(), any()) }
    }

    @Test fun `search results keep paging for the same query, and trending fills the blank screen`() = runTest(main) {
        val vm = SearchViewModel(SavedStateHandle(), media, lists, prefs)
        backgroundScope.launch { vm.state.collect {} }
        vm.loadMoreSuggestions()
        assertEquals(listOf("all1", "all2", "all3", "all4"), ids(vm.state.value.suggestions.items))

        vm.setQuery("dune")
        advanceTimeBy(500)
        vm.loadMoreResults()
        assertEquals(listOf("q-dune-1", "q-dune-2", "q-dune-3", "q-dune-4"), ids(vm.state.value.results.items))
        coVerify { media.search("dune", SearchFilter.ALL, 2) }

        vm.setQuery("")
        advanceTimeBy(500)
        assertTrue(vm.state.value.results.items.isEmpty())
    }

    @Test fun `adding from search or home saves the status, rating and progress picked in the sheet`() = runTest(main) {
        val show = item("show", 1)
        SearchViewModel(SavedStateHandle(), media, lists, prefs).track(show, ListStatus.WATCHING, 8, 3)
        coVerify { lists.save(show, ListStatus.WATCHING, 8, 3) }
        HomeViewModel(media, lists).track(show, ListStatus.COMPLETED, null, 0)
        coVerify { lists.save(show, ListStatus.COMPLETED, null, 0) }
    }

    @Test fun `discover genres narrow the feed and are dropped when the type doesn't have them`() = runTest(main) {
        val vm = HomeViewModel(media, lists)
        backgroundScope.launch { vm.state.collect {} }
        vm.setDiscoverGenre(Genre.CRIME)
        assertEquals(listOf("pop-all-crime-1", "pop-all-crime-2"), ids(vm.state.value.discover.items))
        vm.setDiscoverFilter(SearchFilter.ANIME)
        assertEquals(null, vm.state.value.discoverGenre)
        assertEquals(listOf("pop-anime-1", "pop-anime-2"), ids(vm.state.value.discover.items))
    }

    @Test fun `for you merges recommendations for liked titles and hides what's already listed`() = runTest(main) {
        val liked = ListEntry(MediaSource.TMDB, "dune", MediaType.MOVIE, "Dune", null, null, ListStatus.COMPLETED, 9, 0, null, 1)
        val listed = ListEntry(MediaSource.TMDB, "common0", MediaType.MOVIE, "Listed", null, null, ListStatus.PLAN_TO_WATCH, null, 0, null, 2)
        every { lists.entries } returns flowOf(listOf(liked, listed))
        val vm = HomeViewModel(media, lists)
        backgroundScope.launch { vm.state.collect {} }
        val row = vm.state.value.forYou
        assertEquals(listOf("Dune"), row.because)
        assertEquals(listOf("rec-dune-1"), ids(row.items))
        assertTrue(!row.loading)
    }

    @Test fun `a search genre filters results and switches the blank screen to that genre's popular titles`() = runTest(main) {
        coEvery { media.search(any(), any(), any<Int>()) } returns MediaPage(
            listOf(item("x", 1).copy(genres = listOf("Comedy")), item("x", 2).copy(genres = listOf("Drama"))), hasMore = false,
        )
        val vm = SearchViewModel(SavedStateHandle(), media, lists, prefs)
        backgroundScope.launch { vm.state.collect {} }
        vm.setGenre(Genre.COMEDY)
        assertEquals(listOf("pop-all-comedy-1", "pop-all-comedy-2"), ids(vm.state.value.suggestions.items))
        vm.setQuery("funny")
        advanceTimeBy(500)
        assertEquals(listOf("x1"), ids(vm.state.value.results.items))
    }
}
