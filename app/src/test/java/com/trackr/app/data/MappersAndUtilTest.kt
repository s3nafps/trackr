package com.trackr.app.data

import com.trackr.app.data.local.ListEntryEntity
import com.trackr.app.data.mapper.AniListMapper
import com.trackr.app.data.mapper.ListEntryMapper.toDomain
import com.trackr.app.data.mapper.ListEntryMapper.toDto
import com.trackr.app.data.mapper.ListEntryMapper.toEntity
import com.trackr.app.data.remote.supabase.ListEntryDto
import com.trackr.app.data.mapper.TmdbMapper
import com.trackr.app.data.remote.anilist.AniListRateLimiter
import com.trackr.app.data.remote.tmdb.TmdbDetail
import com.trackr.app.data.remote.tmdb.TmdbEpisodeStub
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

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

    @Test fun `aired episodes count the regular seasons before the last aired episode`() {
        val seasons = TmdbMapper.toDetail(
            TmdbDetail(
                id = 1, name = "Show",
                seasons = listOf(
                    TmdbSeasonDto(0, "Specials", 3), TmdbSeasonDto(1, "S1", 10), TmdbSeasonDto(2, "S2", 10),
                    TmdbSeasonDto(3, "S3", 10), TmdbSeasonDto(4, "S4", 10), TmdbSeasonDto(5, "S5", 8), // S5 announced
                ),
                lastEpisodeToAir = TmdbEpisodeStub(airDate = "2025-06-01", episodeNumber = 10, seasonNumber = 4),
            ),
            MediaType.TV,
        )
        assertEquals(40, seasons.airedEpisodes)
        assertEquals(13, TmdbMapper.airedEpisodes(TmdbEpisodeStub(episodeNumber = 3, seasonNumber = 2), seasons.seasons))
        assertNull(TmdbMapper.airedEpisodes(TmdbEpisodeStub(episodeNumber = 1, seasonNumber = 0), seasons.seasons)) // a special
        assertNull(TmdbMapper.airedEpisodes(null, seasons.seasons))
        assertNull(TmdbMapper.toDetail(TmdbDetail(id = 2, title = "Film"), MediaType.MOVIE).airedEpisodes)
    }

    @Test fun `anime aired episodes come from the next airing episode or the status`() {
        assertEquals(7, AniListMapper.airedEpisodes("RELEASING", nextEpisode = 8, episodes = 12))
        assertEquals(0, AniListMapper.airedEpisodes("NOT_YET_RELEASED", nextEpisode = null, episodes = 12))
        assertEquals(0, AniListMapper.airedEpisodes("NOT_YET_RELEASED", nextEpisode = 1, episodes = 12))
        assertEquals(12, AniListMapper.airedEpisodes("FINISHED", nextEpisode = null, episodes = 12))
        assertNull(AniListMapper.airedEpisodes("RELEASING", nextEpisode = null, episodes = null))
        assertNull(AniListMapper.airedEpisodes("HIATUS", nextEpisode = null, episodes = 24))
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

class ListEntryMapperNotifyTest {
    @Test fun `mapper roundtrips notify`() {
        val e = ListEntryEntity("tmdb", "1", "tv", "T", null, null, "plan", null, 0, null, 5L, notify = true)
        val domain = e.toDomain()
        assertTrue(domain.notify)
        assertTrue(domain.toEntity(dirty = true).notify)
        val dto = e.toDto("u")
        assertTrue(dto.notify)
        assertTrue(dto.toEntity().notify)
        assertTrue(ListEntryDto("u", "tmdb", "1", "tv", "T", status = "plan", updatedAt = "1970-01-01T00:00:00Z", notify = false).toEntity().notify.not())
    }
}

class TmdbAiringMappingTest {
    private val today = LocalDate.of(2026, 10, 3)

    @Test fun `nineAm is 0900 in given zone`() {
        val zone = ZoneId.of("Europe/Berlin")
        val secs = TmdbMapper.localNineAm("2026-10-24", zone)!!
        assertEquals(ZonedDateTime.parse("2026-10-24T09:00:00+02:00[Europe/Berlin]").toEpochSecond(), secs)
    }

    @Test fun `unparsable date returns null`() {
        assertNull(TmdbMapper.localNineAm("soon"))
        assertNull(TmdbMapper.localNineAm(""))
    }

    @Test fun `tv detail maps next episode`() {
        val item = TmdbMapper.toDetail(
            TmdbDetail(id = 1, name = "Show", nextEpisodeToAir = TmdbEpisodeStub("2026-10-24", 5)), MediaType.TV, today,
        ).item
        assertEquals(5, item.airingEpisode)
        assertEquals(TmdbMapper.localNineAm("2026-10-24"), item.airingAtEpoch)
        assertTrue(item.airingDateOnly)
    }

    @Test fun `movie in future maps release date`() {
        val item = TmdbMapper.toDetail(TmdbDetail(id = 2, title = "Dune 3", releaseDate = "2026-12-18"), MediaType.MOVIE, today).item
        assertNull(item.airingEpisode)
        assertEquals(TmdbMapper.localNineAm("2026-12-18"), item.airingAtEpoch)
        assertTrue(item.airingDateOnly)
    }

    @Test fun `movie releasing today still maps`() {
        val item = TmdbMapper.toDetail(TmdbDetail(id = 2, title = "M", releaseDate = "2026-10-03"), MediaType.MOVIE, today).item
        assertEquals(TmdbMapper.localNineAm("2026-10-03"), item.airingAtEpoch)
    }

    @Test fun `movie in past has no airing`() {
        val item = TmdbMapper.toDetail(TmdbDetail(id = 3, title = "Old", releaseDate = "2021-10-22"), MediaType.MOVIE, today).item
        assertNull(item.airingAtEpoch)
        assertEquals(false, item.airingDateOnly)
    }

    @Test fun `tv without next episode has no airing`() {
        val item = TmdbMapper.toDetail(TmdbDetail(id = 4, name = "Ended"), MediaType.TV, today).item
        assertNull(item.airingAtEpoch); assertNull(item.airingEpisode)
    }

    @Test fun `tv next episode with unparsable date has no airing`() {
        val item = TmdbMapper.toDetail(TmdbDetail(id = 5, name = "X", nextEpisodeToAir = TmdbEpisodeStub(null, 2)), MediaType.TV, today).item
        assertNull(item.airingAtEpoch)
    }
}
