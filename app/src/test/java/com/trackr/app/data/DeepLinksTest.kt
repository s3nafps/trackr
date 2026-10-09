package com.trackr.app.data

import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.ui.navigation.DeepLinks
import com.trackr.app.ui.navigation.DetailTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinksTest {
    @Test fun `app links round-trip`() {
        for ((source, type) in listOf(MediaSource.TMDB to MediaType.MOVIE, MediaSource.TMDB to MediaType.TV, MediaSource.ANILIST to MediaType.ANIME)) {
            assertEquals(DetailTarget(source.key, type.key, "42"), DeepLinks.parse(DeepLinks.appUri(source, type, "42")))
        }
    }

    @Test fun `shared web links round-trip`() {
        for ((source, type) in listOf(MediaSource.TMDB to MediaType.MOVIE, MediaSource.TMDB to MediaType.TV, MediaSource.ANILIST to MediaType.ANIME)) {
            assertEquals(DetailTarget(source.key, type.key, "42"), DeepLinks.parse(DeepLinks.webUrl(source, type, "42")))
        }
    }

    @Test fun `tmdb and anilist pages with slugs, queries and no www open the title`() {
        assertEquals(DetailTarget("tmdb", "movie", "438631"), DeepLinks.parse("https://www.themoviedb.org/movie/438631-dune?language=de-DE"))
        assertEquals(DetailTarget("tmdb", "tv", "1399"), DeepLinks.parse("https://themoviedb.org/tv/1399-game-of-thrones/season/1"))
        assertEquals(DetailTarget("anilist", "anime", "154587"), DeepLinks.parse("https://anilist.co/anime/154587/Sousou-no-Frieren/"))
        assertEquals(DetailTarget("anilist", "anime", "1"), DeepLinks.parse(" http://www.anilist.co/anime/1 "))
    }

    @Test fun `notification and widget extras open only titles we open`() {
        assertEquals(DetailTarget("tmdb", "tv", "1399"), DeepLinks.target("tmdb", "tv", "1399"))
        assertEquals(DetailTarget("anilist", "anime", "154587"), DeepLinks.target("anilist", "anime", "154587"))
        assertNull(DeepLinks.target(null, "tv", "1"))
        assertNull(DeepLinks.target("tmdb", "tv", null))
        assertNull(DeepLinks.target("tmdb", "anime", "1"))
        assertNull(DeepLinks.target("anilist", "tv", "1"))
        assertNull(DeepLinks.target("tmdb", "tv", "1/../../x"))
        assertNull(DeepLinks.target("tmdb", "tv", "12x"))
    }

    @Test fun `anything else is ignored`() {
        listOf(
            null, "", "not a url", "https://www.themoviedb.org/person/500-tom-cruise", "https://www.themoviedb.org/movie/",
            "https://www.themoviedb.org/movie/abc", "https://anilist.co/manga/30013", "https://anilist.co/anime/12x",
            "https://evil.example/movie/1", "trackr://title/tmdb/anime/1", "trackr://title/anilist/movie/1",
            "trackr://title/imdb/movie/1", "trackr://title/tmdb/movie", "trackr://profile/tmdb/movie/1",
            "trackr://title/tmdb/movie/1234567890123", "ftp://www.themoviedb.org/movie/1",
        ).forEach { assertNull(it, DeepLinks.parse(it)) }
    }
}
