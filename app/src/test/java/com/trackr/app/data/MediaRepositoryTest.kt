package com.trackr.app.data

import com.trackr.app.data.cache.PageStore
import com.trackr.app.data.remote.anilist.AniListClient
import com.trackr.app.data.remote.tmdb.TmdbApi
import com.trackr.app.data.remote.tmdb.TmdbDetail
import com.trackr.app.data.remote.tmdb.TmdbPage
import com.trackr.app.data.remote.tmdb.TmdbResult
import com.trackr.app.data.remote.tmdb.TmdbSeasonDto
import com.trackr.app.data.repository.MediaRepository
import com.trackr.app.data.repository.SearchFilter
import com.trackr.app.data.repository.userMessage
import com.trackr.app.domain.model.Genre
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaPage
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class MediaRepositoryTest {
    private val tmdb = mockk<TmdbApi>()
    private val anilist = mockk<AniListClient>()
    private val repo = MediaRepository(tmdb, anilist)

    private fun result(id: Int, title: String) = TmdbResult(id = id, mediaType = "movie", title = title, releaseDate = "2020-01-01")
    private fun anime(id: String) = MediaItem(MediaSource.ANILIST, id, MediaType.ANIME, "Anime $id", null)

    @Test fun `trending is cached between calls`() = runTest {
        coEvery { tmdb.trendingMovies(any()) } returns TmdbPage(results = listOf(result(1, "A")))
        repo.trendingMovies(); repo.trendingMovies()
        coVerify(exactly = 1) { tmdb.trendingMovies(any()) }
        repo.trendingMovies(force = true)
        coVerify(exactly = 2) { tmdb.trendingMovies(any()) }
    }

    @Test fun `serves stale data when refresh fails offline`() = runTest {
        coEvery { tmdb.trendingMovies(any()) } returns TmdbPage(results = listOf(result(1, "A")))
        repo.trendingMovies()
        coEvery { tmdb.trendingMovies(any()) } throws UnknownHostException()
        assertEquals(1, repo.trendingMovies(force = true).size)
    }

    @Test fun `fails when offline and nothing cached`() = runTest {
        coEvery { tmdb.trendingTv(any()) } throws UnknownHostException()
        try { repo.trendingTv(); fail("expected exception") } catch (e: UnknownHostException) {
            assertTrue(e.userMessage().contains("connection", ignoreCase = true))
        }
    }

    @Test fun `all search merges both sources and drops tmdb anime duplicates`() = runTest {
        coEvery { tmdb.searchMulti(any(), any(), any()) } returns TmdbPage(
            results = listOf(
                result(1, "Movie"),
                TmdbResult(id = 2, mediaType = "tv", name = "JP Anime", genreIds = listOf(16), originalLanguage = "ja"),
                TmdbResult(id = 3, mediaType = "person", name = "Someone"),
            ),
        )
        coEvery { anilist.search(any(), any(), any()) } returns MediaPage(listOf(anime("9")), hasMore = false)
        val out = repo.search("query", SearchFilter.ALL)
        assertEquals(listOf("tmdb:1", "anilist:9"), out.map { it.key })
    }

    @Test fun `search returns partial results if one backend fails`() = runTest {
        coEvery { tmdb.searchMulti(any(), any(), any()) } throws IOException("boom")
        coEvery { anilist.search(any(), any(), any()) } returns MediaPage(listOf(anime("9")), hasMore = false)
        assertEquals(listOf("anilist:9"), repo.search("q2", SearchFilter.ALL).map { it.key })
    }

    @Test fun `search throws only when every backend fails`() = runTest {
        coEvery { tmdb.searchMulti(any(), any(), any()) } throws IOException("a")
        coEvery { anilist.search(any(), any(), any()) } throws IOException("b")
        try { repo.search("q3", SearchFilter.ALL); fail() } catch (e: IOException) { /* expected */ }
    }

    @Test fun `blank query makes no calls`() = runTest {
        assertTrue(repo.search("   ", SearchFilter.ALL).isEmpty())
    }

    @Test fun `pages are fetched and cached by page number`() = runTest {
        coEvery { tmdb.trendingMovies(1) } returns TmdbPage(page = 1, results = listOf(result(1, "A")), totalPages = 3)
        coEvery { tmdb.trendingMovies(3) } returns TmdbPage(page = 3, results = listOf(result(3, "C")), totalPages = 3)
        val first = repo.trending(MediaType.MOVIE, 1)
        val last = repo.trending(MediaType.MOVIE, 3)
        repo.trending(MediaType.MOVIE, 3)
        assertEquals(listOf("tmdb:1"), first.items.map { it.key })
        assertTrue(first.hasMore)
        assertEquals(listOf("tmdb:3"), last.items.map { it.key })
        assertTrue(!last.hasMore)
        coVerify(exactly = 1) { tmdb.trendingMovies(3) }
    }

    @Test fun `tmdb stops at page 500 whatever total_pages says`() {
        assertTrue(!TmdbPage<TmdbResult>(page = 500, totalPages = 9000).hasMore)
        assertTrue(TmdbPage<TmdbResult>(page = 499, totalPages = 9000).hasMore)
    }

    @Test fun `search asks every backend for the same page`() = runTest {
        coEvery { tmdb.searchMulti("dune", 2, any()) } returns TmdbPage(page = 2, results = listOf(result(5, "Dune")), totalPages = 2)
        coEvery { anilist.search("dune", 2, any()) } returns MediaPage(listOf(anime("8")), hasMore = true)
        val out = repo.search("dune", SearchFilter.ALL, 2)
        assertEquals(listOf("tmdb:5", "anilist:8"), out.items.map { it.key })
        assertTrue("AniList still has pages", out.hasMore)
    }

    @Test fun `popular across all types interleaves and drops tmdb anime`() = runTest {
        coEvery { tmdb.discoverMovies(1, any(), any(), any()) } returns TmdbPage(results = listOf(result(1, "Movie")), totalPages = 1)
        coEvery { tmdb.discoverTv(1, any(), any(), any(), any()) } returns TmdbPage(
            results = listOf(
                TmdbResult(id = 2, mediaType = "tv", name = "Show"),
                TmdbResult(id = 3, mediaType = "tv", name = "JP Anime", genreIds = listOf(16), originalLanguage = "ja"),
            ),
            totalPages = 1,
        )
        coEvery { anilist.popular(1, any()) } returns MediaPage(listOf(anime("9")), hasMore = false)
        val out = repo.popular(null, 1)
        assertEquals(listOf("tmdb:1", "tmdb:2", "anilist:9"), out.items.map { it.key })
        assertTrue(!out.hasMore)
    }

    @Test fun `a mixed page survives one source failing`() = runTest {
        coEvery { tmdb.trendingMovies(2) } throws IOException("down")
        coEvery { tmdb.trendingTv(2) } returns TmdbPage(page = 2, results = listOf(TmdbResult(id = 4, mediaType = "tv", name = "Show")), totalPages = 5)
        coEvery { anilist.trending(2, any()) } returns MediaPage(emptyList(), hasMore = false)
        val out = repo.trendingAll(2)
        assertEquals(listOf("tmdb:4"), out.items.map { it.key })
        assertTrue(out.hasMore)
    }

    @Test fun `a genre is passed to each source in its own terms, and sources without it are skipped`() = runTest {
        coEvery { tmdb.discoverMovies(1, any(), any(), any(), "80") } returns TmdbPage(results = listOf(result(1, "Heist")), totalPages = 1)
        coEvery { tmdb.discoverTv(1, any(), any(), any(), any(), "80") } returns TmdbPage(
            results = listOf(TmdbResult(id = 2, mediaType = "tv", name = "Cop Show")), totalPages = 1,
        )
        val out = repo.popular(null, 1, genre = Genre.CRIME)
        assertEquals(listOf("tmdb:1", "tmdb:2"), out.items.map { it.key })
        coVerify(exactly = 0) { anilist.popular(any(), any(), any()) }

        coEvery { anilist.popular(1, any(), "Sci-Fi") } returns MediaPage(listOf(anime("5")), hasMore = true)
        assertEquals(listOf("anilist:5"), repo.popular(MediaType.ANIME, 1, genre = Genre.SCI_FI).items.map { it.key })
    }

    private class MemoryStore : PageStore {
        val pages = mutableMapOf<String, MediaPage>()
        override suspend fun get(key: String) = pages[key]
        override suspend fun put(key: String, page: MediaPage) { pages[key] = page }
    }

    @Test fun `pages are saved, and served from the saved copy when offline after a restart`() = runTest {
        val store = MemoryStore()
        coEvery { tmdb.trendingMovies(1) } returns TmdbPage(page = 1, results = listOf(result(1, "A")), totalPages = 2)
        val online = MediaRepository(tmdb, anilist, store).trending(MediaType.MOVIE, 1)
        assertTrue(!online.fromCache)

        coEvery { tmdb.trendingMovies(1) } throws UnknownHostException()
        val restarted = MediaRepository(tmdb, anilist, store) // nothing in memory any more
        val offline = restarted.trending(MediaType.MOVIE, 1)
        assertEquals(listOf("tmdb:1"), offline.items.map { it.key })
        assertTrue(offline.fromCache)
        assertTrue(offline.hasMore)
    }

    @Test fun `searches fall back to the saved copy too, and fail when there is none`() = runTest {
        val store = MemoryStore()
        coEvery { tmdb.searchMulti("dune", 1, any()) } returns TmdbPage(results = listOf(result(5, "Dune")))
        coEvery { anilist.search("dune", 1, any()) } returns MediaPage(emptyList(), hasMore = false)
        MediaRepository(tmdb, anilist, store).search("dune", SearchFilter.ALL, 1)

        coEvery { tmdb.searchMulti(any(), any(), any()) } throws UnknownHostException()
        coEvery { anilist.search(any(), any(), any()) } throws UnknownHostException()
        val restarted = MediaRepository(tmdb, anilist, store)
        assertEquals(listOf("tmdb:5"), restarted.search("dune", SearchFilter.ALL, 1).items.map { it.key })
        try { restarted.search("never searched", SearchFilter.ALL, 1); fail() } catch (e: UnknownHostException) { /* expected */ }
    }

    private fun tv(id: String) = MediaItem(MediaSource.TMDB, id, MediaType.TV, "Show $id", null)

    @Test fun `seasons of a tv show come from its details, without specials`() = runTest {
        coEvery { tmdb.tvDetail(7) } returns TmdbDetail(
            id = 7, name = "Show",
            seasons = listOf(
                TmdbSeasonDto(0, "Specials", 3), TmdbSeasonDto(1, "Season 1", 10), TmdbSeasonDto(2, "Season 2", 8),
            ),
        )
        assertEquals(listOf(10, 8), repo.seasonsOf(tv("7")))
    }

    @Test fun `movies and anime have no seasons to pick from, and nothing is fetched for them`() = runTest {
        assertEquals(emptyList<Int>(), repo.seasonsOf(MediaItem(MediaSource.TMDB, "1", MediaType.MOVIE, "Film", null)))
        assertEquals(emptyList<Int>(), repo.seasonsOf(anime("5")))
        coVerify(exactly = 0) { tmdb.tvDetail(any()) }
        coVerify(exactly = 0) { tmdb.movieDetail(any()) }
    }

    @Test fun `seasons are empty rather than an error when the details can't be loaded`() = runTest {
        coEvery { tmdb.tvDetail(9) } throws UnknownHostException()
        assertEquals(emptyList<Int>(), repo.seasonsOf(tv("9")))
    }
}
