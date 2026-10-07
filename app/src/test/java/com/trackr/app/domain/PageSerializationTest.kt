package com.trackr.app.domain

import com.trackr.app.domain.model.MediaDetail
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaPage
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** What DiskPageStore writes and reads, with the same Json settings the app provides (NetworkModule.json). */
class PageSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private val full = MediaItem(
        MediaSource.ANILIST, "154587", MediaType.ANIME, "Frieren", "https://img/p.jpg", "https://img/b.jpg", 2023, 9.1, "A mage...",
        listOf("Fantasy", "Drama"), 28, 24, "Madhouse", airingSeason = 2, airingEpisode = 12, airingAtEpoch = 1_800_000_000L, airingDateOnly = true,
    )
    // No poster (it has no default value) and nothing optional set.
    private val bare = MediaItem(MediaSource.TMDB, "7", MediaType.MOVIE, "Untitled", null)

    private fun roundTrip(page: MediaPage) = json.decodeFromString(MediaPage.serializer(), json.encodeToString(MediaPage.serializer(), page))

    @Test fun `a page survives being saved and read back`() {
        val page = MediaPage(listOf(full, bare), hasMore = true)
        assertEquals(page, roundTrip(page))
    }

    @Test fun `a title without a poster is read back with a null poster`() {
        assertEquals(null, roundTrip(MediaPage(listOf(bare), hasMore = false)).items.single().posterUrl)
    }

    @Test fun `the served-from-cache flag is not saved`() {
        val saved = json.encodeToString(MediaPage.serializer(), MediaPage(listOf(bare), hasMore = false, fromCache = true))
        assertFalse(saved.contains("fromCache"))
        assertFalse(json.decodeFromString(MediaPage.serializer(), saved).fromCache)
    }

    @Test fun `a title page survives being saved and read back`() {
        val detail = MediaDetail(
            item = full,
            tagline = "Tagline",
            seasons = listOf(com.trackr.app.domain.model.SeasonInfo(1, "Season 1", 10, 2020, null)),
            related = listOf(com.trackr.app.domain.model.RelatedItem("Sequel", bare)),
            trailer = com.trackr.app.domain.model.Trailer("https://www.youtube.com/watch?v=x", null),
            airedEpisodes = 10,
        )
        val saved = json.encodeToString(MediaDetail.serializer(), detail)
        assertEquals(detail, json.decodeFromString(MediaDetail.serializer(), saved))
    }
}
