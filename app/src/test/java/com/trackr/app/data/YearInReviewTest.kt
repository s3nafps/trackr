package com.trackr.app.data

import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.StatsCalculator
import com.trackr.app.domain.util.YearInReviewCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class YearInReviewTest {
    private val zone = ZoneId.of("America/New_York")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun e(
        id: String, type: MediaType = MediaType.MOVIE, status: ListStatus = ListStatus.COMPLETED, rating: Int? = null,
        progress: Int = 1, updatedAt: Long = 0, completedAt: Long? = null,
    ) = ListEntry(MediaSource.TMDB, id, type, "T$id", null, null, status, rating, progress, null, updatedAt, completedAt = completedAt)

    @Test fun `completion dates come from the record, then the last edit, never from unknown or unfinished rows`() {
        assertEquals(5L, YearInReviewCalculator.completedAt(e("1", completedAt = 5, updatedAt = 9)))
        assertEquals(9L, YearInReviewCalculator.completedAt(e("2", completedAt = null, updatedAt = 9)))
        assertNull(YearInReviewCalculator.completedAt(e("3", completedAt = ListEntry.COMPLETED_DATE_UNKNOWN, updatedAt = 9)))
        assertNull(YearInReviewCalculator.completedAt(e("4", status = ListStatus.WATCHING, completedAt = 5)))
    }

    @Test fun `a year counts only titles completed in it, in the user's time zone`() {
        val entries = listOf(
            e("film", rating = 8, completedAt = at(2026, 3, 10)),
            e("show", MediaType.TV, rating = 10, progress = 10, completedAt = at(2026, 3, 20)),
            e("anime", MediaType.ANIME, rating = 9, progress = 24, completedAt = at(2026, 11, 2)),
            // 23:30 on New Year's Eve in New York is already 2027 in UTC, but it's still 2026 here.
            e("nye", rating = 6, completedAt = at(2026, 12, 31, 23).plus(30 * 60_000)),
            e("old", rating = 10, completedAt = at(2025, 6, 1)),
            e("unknown", rating = 10, completedAt = ListEntry.COMPLETED_DATE_UNKNOWN),
            e("watching", status = ListStatus.WATCHING, updatedAt = at(2026, 5, 5)),
        )
        val y = YearInReviewCalculator.compute(entries, 2026, zone)
        assertEquals(4, y.completed)
        assertEquals(2, y.movies); assertEquals(1, y.shows); assertEquals(1, y.anime)
        assertEquals(34, y.episodes)
        val minutes = 2 * StatsCalculator.MOVIE_MINUTES + 10 * StatsCalculator.TV_EPISODE_MINUTES + 24 * StatsCalculator.ANIME_EPISODE_MINUTES
        assertEquals(minutes / 60, y.hours)
        assertEquals(8.25, y.averageRating!!, 1e-9)
        assertEquals(listOf("show", "anime", "film", "nye"), y.topRated.map { it.externalId })
        assertEquals(listOf(0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 1, 1), y.perMonth)
        assertEquals(3, y.busiestMonth)
        assertFalse(y.usesEstimatedDates)
        assertEquals(listOf(2026, 2025), YearInReviewCalculator.years(entries, zone))
    }

    @Test fun `rows from before dates were recorded are counted by their last edit and flagged`() {
        val y = YearInReviewCalculator.compute(listOf(e("legacy", updatedAt = at(2026, 2, 1))), 2026, zone)
        assertEquals(1, y.completed)
        assertTrue(y.usesEstimatedDates)
    }

    @Test fun `an empty year has no busiest month or average`() {
        val y = YearInReviewCalculator.compute(listOf(e("old", completedAt = at(2020, 1, 1))), 2026, zone)
        assertTrue(y.isEmpty); assertNull(y.busiestMonth); assertNull(y.averageRating)
        assertEquals(List(12) { 0 }, y.perMonth)
    }

    @Test fun `top rated is capped and ties go to whatever was finished first`() {
        val entries = (1..8).map { e("$it", rating = 9, completedAt = at(2026, 1, it)) }
        assertEquals(listOf("1", "2", "3", "4", "5"), YearInReviewCalculator.compute(entries, 2026, zone).topRated.map { it.externalId })
    }
}
