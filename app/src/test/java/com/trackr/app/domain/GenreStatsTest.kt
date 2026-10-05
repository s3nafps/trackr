package com.trackr.app.domain

import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.TitleMeta
import com.trackr.app.domain.util.GenreShare
import com.trackr.app.domain.util.GenreStats
import org.junit.Assert.assertEquals
import org.junit.Test

class GenreStatsTest {
    private fun entry(id: String, status: ListStatus, source: MediaSource = MediaSource.TMDB) =
        ListEntry(source, id, MediaType.TV, "T$id", null, null, status, null, 0, null, 0)
    private fun meta(vararg genres: String) = TitleMeta(genres.toList(), emptyList(), 1)

    @Test fun `counts watched titles by genre, splitting TMDB's combined genres`() {
        val entries = listOf(
            entry("1", ListStatus.COMPLETED), entry("2", ListStatus.WATCHING), entry("3", ListStatus.COMPLETED),
            entry("4", ListStatus.PLAN_TO_WATCH), entry("5", ListStatus.DROPPED), entry("6", ListStatus.COMPLETED),
        )
        val meta = mapOf(
            "tmdb:1" to meta("Drama", "Sci-Fi & Fantasy"),
            "tmdb:2" to meta("Sci-Fi", "Action"),
            "tmdb:3" to meta("Drama", "History"),
            "tmdb:4" to meta("Comedy"), // planned: not watched yet
            "tmdb:5" to meta("Comedy"), // dropped
        )
        val b = GenreStats.breakdown(entries, meta)
        assertEquals(
            listOf(GenreShare("Drama", 2), GenreShare("Sci-Fi", 2), GenreShare("Action", 1), GenreShare("Fantasy", 1), GenreShare("History", 1)),
            b.top,
        )
        assertEquals(3, b.counted) // tmdb:6 has no genres known yet
        assertEquals(4, b.watched)
    }

    @Test fun `a genre named twice for one title counts once`() {
        val b = GenreStats.breakdown(listOf(entry("1", ListStatus.COMPLETED)), mapOf("tmdb:1" to meta("Action & Adventure", "Action")))
        assertEquals(listOf(GenreShare("Action", 1), GenreShare("Adventure", 1)), b.top)
    }
}
