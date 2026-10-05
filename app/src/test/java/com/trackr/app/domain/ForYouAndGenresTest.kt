package com.trackr.app.domain

import com.trackr.app.domain.model.Genre
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.util.ForYou
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForYouAndGenresTest {
    private fun entry(id: String, status: ListStatus, rating: Int?, updatedAt: Long) =
        ListEntry(MediaSource.TMDB, id, MediaType.MOVIE, "Title $id", null, null, status, rating, 0, null, updatedAt)
    private fun item(id: String, vararg genres: String) =
        MediaItem(MediaSource.TMDB, id, MediaType.MOVIE, "T$id", null, genres = genres.toList())

    @Test fun `seeds are liked titles first, then unrated completed or watching ones`() {
        val seeds = ForYou.seeds(
            listOf(
                entry("plan", ListStatus.PLAN_TO_WATCH, null, 900),
                entry("dropped9", ListStatus.DROPPED, 9, 800),
                entry("meh", ListStatus.COMPLETED, 5, 700),
                entry("good8", ListStatus.COMPLETED, 8, 100),
                entry("great10", ListStatus.WATCHING, 10, 50),
                entry("good8new", ListStatus.COMPLETED, 8, 300),
                entry("watching", ListStatus.WATCHING, null, 600),
                entry("finished", ListStatus.COMPLETED, null, 650),
            ),
        )
        assertEquals(listOf("great10", "good8new", "good8", "finished", "watching"), seeds.map { it.externalId })
    }

    @Test fun `ranking favours titles recommended for several seeds, takes turns, and skips listed ones`() {
        val ranked = ForYou.rank(
            listOf(
                listOf(item("a"), item("shared"), item("b")),
                listOf(item("c"), item("listed"), item("shared")),
            ),
            listed = setOf("tmdb:listed"),
        )
        assertEquals(listOf("shared", "a", "c", "b"), ranked.map { it.externalId })
    }

    @Test fun `genres know which sources have them`() {
        assertTrue(Genre.CRIME.appliesTo(MediaType.TV))
        assertFalse(Genre.CRIME.appliesTo(MediaType.ANIME))
        assertFalse(Genre.SLICE_OF_LIFE.appliesTo(MediaType.MOVIE))
        assertTrue(Genre.SLICE_OF_LIFE.appliesTo(null))
        assertFalse(Genre.HORROR in Genre.forType(MediaType.TV))
    }

    @Test fun `loaded titles match a genre by any of its names`() {
        assertTrue(Genre.SCI_FI.matches(item("1", "Sci-Fi & Fantasy")))
        assertTrue(Genre.ACTION.matches(item("2", "Drama", "Action")))
        assertFalse(Genre.COMEDY.matches(item("3", "Drama")))
    }
}
