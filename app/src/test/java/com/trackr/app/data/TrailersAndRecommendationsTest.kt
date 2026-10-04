package com.trackr.app.data

import com.trackr.app.data.mapper.AniListMapper
import com.trackr.app.data.mapper.TmdbMapper
import com.trackr.app.data.remote.tmdb.TmdbDetail
import com.trackr.app.data.remote.tmdb.TmdbPage
import com.trackr.app.data.remote.tmdb.TmdbResult
import com.trackr.app.data.remote.tmdb.TmdbVideo
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.Trailer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailersAndRecommendationsTest {
    // Same configuration as NetworkModule.json().
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    @Test fun `tmdb prefers an official trailer, then any trailer, then a teaser, youtube only`() {
        val teaser = TmdbVideo("tease", "YouTube", "Teaser", official = true)
        val fanTrailer = TmdbVideo("fan", "YouTube", "Trailer", official = false)
        val official = TmdbVideo("off", "YouTube", "Trailer", official = true)
        val vimeo = TmdbVideo("vim", "Vimeo", "Trailer", official = true)
        assertEquals(Trailer.youtube("off"), TmdbMapper.trailer(listOf(teaser, fanTrailer, vimeo, official)))
        assertEquals(Trailer.youtube("fan"), TmdbMapper.trailer(listOf(teaser, fanTrailer, vimeo)))
        assertEquals(Trailer.youtube("tease"), TmdbMapper.trailer(listOf(teaser, vimeo)))
        assertNull(TmdbMapper.trailer(listOf(vimeo, TmdbVideo("clip", "YouTube", "Featurette"))))
        assertEquals("https://img.youtube.com/vi/off/hqdefault.jpg", Trailer.youtube("off").thumbnailUrl)
    }

    @Test fun `tmdb detail json maps trailer and recommendations`() {
        val d = json.decodeFromString<TmdbDetail>(
            """
            {
              "id": 1, "title": "Dune",
              "videos": {"results": [{"key": "abc", "site": "YouTube", "type": "Trailer", "official": true, "name": "Official Trailer"}]},
              "recommendations": {"page": 1, "results": [
                {"id": 2, "media_type": "movie", "title": "Dune: Part Two", "poster_path": "/p2.jpg"},
                {"id": 1, "media_type": "movie", "title": "Dune"},
                {"id": 3, "media_type": "movie", "title": "Arrival"}
              ]},
              "similar": {"page": 1, "results": [{"id": 9, "title": "Ignored"}]}
            }
            """.trimIndent(),
        )
        val detail = TmdbMapper.toDetail(d, MediaType.MOVIE, region = "US")
        assertEquals("https://www.youtube.com/watch?v=abc", detail.trailer?.url)
        assertEquals(listOf("2", "3"), detail.recommendations.map { it.externalId })
        assertEquals("https://image.tmdb.org/t/p/w500/p2.jpg", detail.recommendations.first().posterUrl)
    }

    @Test fun `tmdb falls back to similar titles, deduped and capped`() {
        val similar = (1..30).map { TmdbResult(id = 100 + it, name = "S$it") } + TmdbResult(id = 101, name = "dup")
        val d = TmdbDetail(id = 5, name = "Show", recommendations = TmdbPage(results = emptyList()), similar = TmdbPage(results = similar))
        val recs = TmdbMapper.recommendations(d, MediaType.TV)
        assertEquals(TmdbMapper.MAX_RECOMMENDATIONS, recs.size)
        assertEquals(recs.size, recs.map { it.key }.toSet().size)
        assertEquals(MediaType.TV, recs.first().type)
    }

    @Test fun `tmdb detail without videos or recommendations has none`() {
        val detail = TmdbMapper.toDetail(TmdbDetail(id = 1, title = "X"), MediaType.MOVIE)
        assertNull(detail.trailer)
        assertEquals(emptyList<MediaItem>(), detail.recommendations)
    }

    @Test fun `anilist trailer supports youtube and dailymotion`() {
        assertEquals(Trailer.youtube("yt1"), AniListMapper.trailer("yt1", "youtube", "https://thumb/ignored.jpg"))
        assertEquals(
            Trailer("https://www.dailymotion.com/video/dm1", "https://thumb/dm.jpg"),
            AniListMapper.trailer("dm1", "dailymotion", "https://thumb/dm.jpg"),
        )
        assertNull(AniListMapper.trailer("x", "vimeo", null))
        assertNull(AniListMapper.trailer(null, "youtube", null))
        assertNull(AniListMapper.trailer(" ", "youtube", null))
    }

    @Test fun `anilist relations put prequels and sequels first with readable labels`() {
        fun anime(id: String) = MediaItem(MediaSource.ANILIST, id, MediaType.ANIME, "A$id", null)
        val related = AniListMapper.relatedItems(
            listOf(
                "SIDE_STORY" to anime("1"),
                "SOMETHING_NEW" to anime("2"),
                "SEQUEL" to anime("3"),
                "SPIN_OFF" to anime("4"),
                "PREQUEL" to anime("5"),
                "SEQUEL" to anime("3"),
                null to anime("6"),
            ),
        )
        assertEquals(listOf("5", "3", "1", "4", "2", "6"), related.map { it.item.externalId })
        assertEquals(listOf("Prequel", "Sequel", "Side story", "Spin-off", "Related", "Related"), related.map { it.relation })
    }
}
