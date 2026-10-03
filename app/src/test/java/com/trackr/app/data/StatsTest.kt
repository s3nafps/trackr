package com.trackr.app.data

import com.trackr.app.domain.model.ActivityEntry
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.StatsCalculator
import com.trackr.app.ui.components.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsTest {
    private fun e(id: String, type: MediaType, status: ListStatus, rating: Int? = null, progress: Int = 0, total: Int? = null) =
        ListEntry(MediaSource.TMDB, id, type, "T$id", null, null, status, rating, progress, total, 0)

    @Test fun `computes counts hours and rating stats`() {
        val s = StatsCalculator.compute(
            listOf(
                e("1", MediaType.MOVIE, ListStatus.COMPLETED, 8, 1, 1),
                e("2", MediaType.MOVIE, ListStatus.PLAN_TO_WATCH),
                e("3", MediaType.TV, ListStatus.WATCHING, 10, 8, 10),   // 8 * 45 = 360 min
                e("4", MediaType.ANIME, ListStatus.COMPLETED, 8, 12, 12), // 12 * 24 = 288 min
                e("5", MediaType.TV, ListStatus.PLAN_TO_WATCH),
            ),
        )
        assertEquals(1, s.moviesWatched)
        assertEquals(1, s.tvShows)            // plan-to-watch is not "watched"
        assertEquals(8, s.tvEpisodes)
        assertEquals(1, s.animeTitles)
        assertEquals((120 + 360 + 288) / 60, s.hours)
        assertEquals(26.0 / 3, s.averageRating!!, 1e-9)
        assertEquals(2, s.ratingDistribution[7]) // two 8s
        assertEquals(1, s.ratingDistribution[9])
        assertEquals(3, s.ratedCount)
        assertEquals(2, s.statusCounts[ListStatus.PLAN_TO_WATCH])
    }

    @Test fun `empty list has no average`() {
        val s = StatsCalculator.compute(emptyList())
        assertNull(s.averageRating); assertEquals(0, s.hours); assertEquals(10, s.ratingDistribution.size)
    }

    @Test fun `watch together is the plan-to-watch intersection`() {
        val mine = listOf(e("1", MediaType.MOVIE, ListStatus.PLAN_TO_WATCH), e("2", MediaType.MOVIE, ListStatus.PLAN_TO_WATCH), e("3", MediaType.MOVIE, ListStatus.COMPLETED))
        val theirs = listOf(e("2", MediaType.MOVIE, ListStatus.PLAN_TO_WATCH), e("3", MediaType.MOVIE, ListStatus.PLAN_TO_WATCH), e("1", MediaType.MOVIE, ListStatus.DROPPED))
        assertEquals(listOf("2"), StatsCalculator.overlap(mine, theirs).map { it.externalId })
    }

    @Test fun `activity verbs read naturally`() {
        fun a(status: ListStatus, rating: Int?) = ActivityEntry("i", "u", "sara", null, MediaSource.TMDB, "1", MediaType.TV, "X", null, status, rating, 0, null, 0)
        assertEquals("rated", a(ListStatus.COMPLETED, 9).verb)
        assertEquals("completed", a(ListStatus.COMPLETED, null).verb)
        assertEquals("is watching", a(ListStatus.WATCHING, null).verb)
        assertEquals("plans to watch", a(ListStatus.PLAN_TO_WATCH, null).verb)
        assertEquals("dropped", a(ListStatus.DROPPED, null).verb)
    }

    @Test fun `relative time buckets`() {
        val now = 1_000_000_000L
        assertEquals("just now", relativeTime(now - 10_000, now))
        assertEquals("15m ago", relativeTime(now - 15 * 60_000, now))
        assertEquals("2h ago", relativeTime(now - 2 * 3_600_000L, now))
        assertEquals("3d ago", relativeTime(now - 3 * 86_400_000L, now))
    }
}
