package com.trackr.app.data

import com.trackr.app.data.remote.anilist.AniListClient
import com.trackr.app.data.remote.tmdb.TmdbApi
import com.trackr.app.data.remote.tmdb.TmdbPage
import com.trackr.app.data.remote.tmdb.TmdbResult
import com.trackr.app.data.repository.MediaRepository
import com.trackr.app.data.repository.SearchFilter
import com.trackr.app.data.repository.userMessage
import com.trackr.app.domain.model.MediaItem
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
        coEvery { anilist.search(any(), any()) } returns listOf(anime("9"))
        val out = repo.search("query", SearchFilter.ALL)
        assertEquals(listOf("tmdb:1", "anilist:9"), out.map { it.key })
    }

    @Test fun `search returns partial results if one backend fails`() = runTest {
        coEvery { tmdb.searchMulti(any(), any(), any()) } throws IOException("boom")
        coEvery { anilist.search(any(), any()) } returns listOf(anime("9"))
        assertEquals(listOf("anilist:9"), repo.search("q2", SearchFilter.ALL).map { it.key })
    }

    @Test fun `search throws only when every backend fails`() = runTest {
        coEvery { tmdb.searchMulti(any(), any(), any()) } throws IOException("a")
        coEvery { anilist.search(any(), any()) } throws IOException("b")
        try { repo.search("q3", SearchFilter.ALL); fail() } catch (e: IOException) { /* expected */ }
    }

    @Test fun `blank query makes no calls`() = runTest {
        assertTrue(repo.search("   ", SearchFilter.ALL).isEmpty())
    }
}
