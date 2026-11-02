package com.trackr.app.domain

import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.TitleMeta
import com.trackr.app.domain.model.WatchEvent
import com.trackr.app.domain.util.WatchHistory
import com.trackr.app.domain.util.WatchTime
import com.trackr.app.domain.util.YearInReviewCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class WatchHistoryTest {
    private val utc = ZoneOffset.UTC
    private fun at(day: Int, hour: Int = 20) = LocalDate.of(2026, 11, day).atTime(hour, 0).toInstant(utc).toEpochMilli()
    private fun show(progress: Int, status: ListStatus = ListStatus.WATCHING, total: Int? = 10, rewatches: Int = 0) =
        ListEntry(MediaSource.TMDB, "1", MediaType.TV, "Severance", null, null, status, null, progress, total, 0L, rewatchCount = rewatches)
    private fun movie(status: ListStatus, rewatches: Int = 0) =
        ListEntry(MediaSource.TMDB, "2", MediaType.MOVIE, "Heat", null, null, status, null, 0, null, 0L, rewatchCount = rewatches)
    private var n = 0
    private fun id() = "e${n++}"
    private fun watched(from: Int?, to: Int?, at: Long, type: MediaType = MediaType.TV) =
        WatchEvent(id(), MediaSource.TMDB, if (type == MediaType.MOVIE) "2" else "1", type, "T", WatchEvent.Kind.WATCHED, from, to, at)
    private fun rewatch(at: Long) = WatchEvent(id(), MediaSource.TMDB, "1", MediaType.TV, "T", WatchEvent.Kind.REWATCH, null, null, at)

    @Test fun `marking episodes logs the new ones as one range`() {
        val e = WatchHistory.onWrite(show(3), show(7), at(1), ::id).single()
        assertEquals(4 to 7, e.fromEpisode to e.toEpisode)
        assertEquals(4, e.episodes)
        assertTrue(WatchHistory.onWrite(show(7), show(7, ListStatus.DROPPED), at(1), ::id).isEmpty())
        assertEquals(1 to 2, WatchHistory.onWrite(null, show(2), at(1), ::id).single().let { it.fromEpisode to it.toEpisode })
    }

    @Test fun `a movie is logged when it becomes completed`() {
        val e = WatchHistory.onWrite(movie(ListStatus.PLAN_TO_WATCH), movie(ListStatus.COMPLETED), at(1), ::id).single()
        assertEquals(null, e.fromEpisode)
        assertEquals(1, e.episodes)
        assertTrue(WatchHistory.onWrite(movie(ListStatus.COMPLETED), movie(ListStatus.COMPLETED), at(1), ::id).isEmpty())
    }

    @Test fun `lowering progress takes back episodes of the current watch-through only`() {
        val old = watched(1, 10, at(1))
        val marker = rewatch(at(2))
        val a = watched(1, 3, at(3))
        val b = watched(4, 6, at(4))
        val trim = WatchHistory.onCorrection(show(6), show(2), listOf(old, marker, a, b))
        assertEquals(listOf(b.id), trim.remove.map { it.id })
        assertEquals(listOf(a.id to 2), trim.shorten.map { it.id to it.toEpisode })
        assertTrue(WatchHistory.onCorrection(show(2), show(5), listOf(a)).isEmpty)
    }

    @Test fun `un-completing a movie takes back its last viewing`() {
        val first = watched(null, null, at(1), MediaType.MOVIE)
        val trim = WatchHistory.onCorrection(movie(ListStatus.COMPLETED), movie(ListStatus.WATCHING), listOf(first))
        assertEquals(listOf(first.id), trim.remove.map { it.id })
    }

    @Test fun `episodes logged one by one on the same day read as one line`() {
        val lines = WatchHistory.lines(
            listOf(watched(5, 5, at(1, 20)), watched(6, 6, at(1, 21)), watched(7, 7, at(1, 22)), watched(8, 8, at(2)), rewatch(at(3))),
            utc,
        )
        assertEquals(listOf("Started watching again", "Episode 8", "Episodes 5–7"), lines.map { it.label })
        assertTrue(lines.first().rewatch)
    }

    @Test fun `watch time counts rewatches and uses real runtimes`() {
        val meta = TitleMeta(emptyList(), emptyList(), 0L, runtimeMinutes = 50)
        assertEquals(10, WatchTime.viewings(show(10, ListStatus.COMPLETED)))
        assertEquals(23, WatchTime.viewings(show(3, rewatches = 2)))
        assertEquals(1, WatchTime.viewings(movie(ListStatus.WATCHING, rewatches = 1)))
        assertEquals(3, WatchTime.viewings(movie(ListStatus.COMPLETED, rewatches = 2)))
        assertEquals(500L, WatchTime.minutes(show(10, ListStatus.COMPLETED), meta))

        val total = WatchTime.total(listOf(show(10, ListStatus.COMPLETED), movie(ListStatus.COMPLETED), movie(ListStatus.PLAN_TO_WATCH)), mapOf("tmdb:1" to meta))
        assertEquals(500L + 120, total.minutes) // the movie has no runtime yet: a typical one
        assertEquals(1, total.estimated)
        assertEquals("10 h", WatchTime.format(620))
        assertEquals("1,234 h", WatchTime.format(1234 * 60L))
        assertEquals("45 min", WatchTime.format(45))
    }

    @Test fun `year in review hours come from the history, plus finished titles without one`() {
        val meta = mapOf("tmdb:1" to TitleMeta(emptyList(), emptyList(), 0L, runtimeMinutes = 60))
        val finished = show(10, ListStatus.COMPLETED).copy(completedAt = at(5))
        val heat = movie(ListStatus.COMPLETED).copy(completedAt = at(6))
        // Three episodes logged this year: 3 hours, plus Heat (no history, typical 2 hours).
        val review = YearInReviewCalculator.compute(listOf(finished, heat), 2026, utc, history = listOf(watched(8, 10, at(5))), meta = meta)
        assertEquals(5, review.hours)
    }
}
