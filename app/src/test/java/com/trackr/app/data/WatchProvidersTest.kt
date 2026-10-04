package com.trackr.app.data

import com.trackr.app.data.mapper.AniListMapper
import com.trackr.app.data.mapper.TmdbMapper
import com.trackr.app.data.remote.tmdb.TmdbDetail
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.WatchProvider
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProvidersTest {
    // Same configuration as NetworkModule.json().
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private val movie = """
        {
          "id": 438631, "title": "Dune", "release_date": "2021-09-15",
          "watch/providers": {
            "results": {
              "DE": {
                "link": "https://www.themoviedb.org/movie/438631-dune/watch?locale=DE",
                "flatrate": [
                  {"logo_path": "/netflix.jpg", "provider_id": 8, "provider_name": "Netflix", "display_priority": 5},
                  {"logo_path": "/wow.jpg", "provider_id": 30, "provider_name": "WOW", "display_priority": 1}
                ],
                "free": [{"logo_path": "/arte.jpg", "provider_id": 234, "provider_name": "Arte", "display_priority": 3}],
                "ads": [
                  {"logo_path": "/pluto.jpg", "provider_id": 300, "provider_name": "Pluto TV", "display_priority": 2},
                  {"logo_path": "/arte.jpg", "provider_id": 234, "provider_name": "Arte", "display_priority": 3}
                ],
                "rent": [{"logo_path": null, "provider_id": 2, "provider_name": "Apple TV", "display_priority": 4}],
                "buy": [{"provider_id": 2, "provider_name": "Apple TV"}]
              }
            }
          }
        }
    """.trimIndent()

    private fun dune(region: String) = TmdbMapper.toDetail(json.decodeFromString<TmdbDetail>(movie), MediaType.MOVIE, region = region).watch

    @Test fun `tmdb providers for the region are parsed, ordered by priority and linked to the watch page`() {
        val w = dune("DE")
        val link = "https://www.themoviedb.org/movie/438631-dune/watch?locale=DE"
        assertEquals("DE", w.region)
        assertEquals(
            listOf(WatchProvider("WOW", "https://image.tmdb.org/t/p/w92/wow.jpg", link), WatchProvider("Netflix", "https://image.tmdb.org/t/p/w92/netflix.jpg", link)),
            w.stream,
        )
        assertEquals(listOf("Apple TV"), w.rent.map { it.name })
        assertEquals(null, w.rent.single().logoUrl)
        assertEquals(listOf("Apple TV"), w.buy.map { it.name })
    }

    @Test fun `free and ad-supported providers are merged without duplicates`() {
        assertEquals(listOf("Pluto TV", "Arte"), dune("DE").free.map { it.name })
    }

    @Test fun `another country's catalogue is never shown`() {
        val w = dune("FR")
        assertEquals("FR", w.region)
        assertTrue(w.isEmpty)
    }

    @Test fun `detail without watch providers maps to an empty region result`() {
        val w = TmdbMapper.toDetail(TmdbDetail(id = 1, name = "Show"), MediaType.TV, region = "US").watch
        assertEquals("US", w.region)
        assertTrue(w.isEmpty)
    }

    @Test fun `anilist keeps enabled streaming links, one per site`() {
        fun link(site: String, type: String? = "STREAMING", url: String? = "https://$site.example/show", disabled: Boolean = false) =
            AniListMapper.ExternalLink(site, url, type, "https://icons.example/$site.png", "#F88A00", disabled)
        val w = AniListMapper.watchOptions(
            listOf(
                link("Crunchyroll"),
                link("Crunchyroll", url = "https://Crunchyroll.example/other"),
                link("Twitter", type = "SOCIAL"),
                link("Official Site", type = "INFO"),
                link("Funimation", disabled = true),
                link("Netflix", url = null),
                link("HIDIVE"),
            ),
        )
        assertEquals(null, w.region)
        assertEquals(listOf("Crunchyroll", "HIDIVE"), w.stream.map { it.name })
        assertEquals("https://Crunchyroll.example/show", w.stream.first().url)
        assertEquals("#F88A00", w.stream.first().color)
        assertTrue(w.free.isEmpty() && w.rent.isEmpty() && w.buy.isEmpty())
    }
}
