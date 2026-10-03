package com.trackr.app.data

import com.trackr.app.data.mapper.AniListMapper
import com.trackr.app.data.mapper.TmdbMapper
import com.trackr.app.data.remote.anilist.AniListRateLimiter
import com.trackr.app.data.remote.tmdb.TmdbDetail
import com.trackr.app.data.remote.tmdb.TmdbGenre
import com.trackr.app.data.remote.tmdb.TmdbResult
import com.trackr.app.data.remote.tmdb.TmdbSeasonDto
import com.trackr.app.data.repository.MediaRepository
import com.trackr.app.data.util.TtlCache
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbMapperTest {
    @Test fun `movie result maps to unified item`() {
        val item = TmdbMapper.toItem(
            TmdbResult(id = 7, title = "Dune", posterPath = "/p.jpg", releaseDate = "2021-10-22", voteAverage = 8.04, genreIds = listOf(878, 12)),
            MediaType.MOVIE,
        )!!
        assertEquals("tmdb:7", item.key)
        assertEquals(2021, item.year)
        assertEquals("https://image.tmdb.org/t/p/w500/p.jpg", item.posterUrl)
        assertEquals(listOf("Sci-Fi", "Adventure"), item.genres)
        assertEquals(8.04, item.score!!, 0.0)
    }

    @Test fun `person results are dropped in multi search`() {
        assertNull(TmdbMapper.toItem(TmdbResult(id = 1, mediaType = "person", name = "Someone")))
    }

    @Test fun `zero vote average becomes null score`() {
        assertNull(TmdbMapper.toItem(TmdbResult(id = 1, title = "X", voteAverage = 0.0), MediaType.MOVIE)!!.score)
    }

    @Test fun `japanese animation tv is flagged as anime for dedupe`() {
        val i = TmdbMapper.toItem(TmdbResult(id = 2, mediaType = "tv", name = "A", genreIds = listOf(16), originalLanguage = "ja"))!!
        assertEquals("Anime", i.subtitle)
    }

    @Test fun `tv detail maps seasons without specials`() {
        val d = TmdbMapper.toDetail(
            TmdbDetail(
                id = 1, name = "Show", firstAirDate = "2019-01-01", numberOfEpisodes = 20, episodeRunTime = listOf(45),
                genres = listOf(TmdbGenre(18, "Drama")),
                seasons = listOf(TmdbSeasonDto(0, "Specials", 3), TmdbSeasonDto(1, "Season 1", 10, "2019-01-01")),
            ),
            MediaType.TV,
        )
        assertEquals(1, d.seasons.size)
        assertEquals(20, d.item.totalEpisodes)
        assertEquals(45, d.item.runtimeMinutes)
        assertEquals(listOf("Drama"), d.item.genres)
    }
}

class UtilTest {
    @Test fun `ttl cache expires but keeps stale copy`() {
        var now = 0L
        val c = TtlCache<String, Int>(1000) { now }
        c.put("a", 1)
        assertEquals(1, c.get("a"))
        now = 1500
        assertNull(c.get("a"))
        assertEquals(1, c.getStale("a"))
    }

    @Test fun `rate limiter waits once the window is full`() {
        var now = 0L
        var slept = 0L
        val rl = AniListRateLimiter(maxPerMinute = 3, clock = { now }, sleeper = { slept += it; now += it })
        repeat(3) { assertEquals(0L, rl.acquire()) }
        val waited = rl.acquire()
        assertTrue("should have waited ~60s, waited $waited", waited in 59_000..61_000)
    }

    @Test fun `interleave alternates sources`() {
        fun m(id: String) = MediaItem(MediaSource.TMDB, id, MediaType.MOVIE, id, null)
        val out = MediaRepository.interleave(listOf(listOf(m("a1"), m("a2"), m("a3")), listOf(m("b1"))))
        assertEquals(listOf("a1", "b1", "a2", "a3"), out.map { it.externalId })
    }

    @Test fun `anilist description html is stripped`() {
        assertEquals("Line1\nLine2 \"q\"", AniListMapper.cleanDescription("<i>Line1</i><br>Line2 &quot;q&quot;"))
    }
}
