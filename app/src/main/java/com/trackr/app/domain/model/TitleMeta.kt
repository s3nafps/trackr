package com.trackr.app.domain.model

/**
 * Details of a listed title that come from TMDB/AniList rather than from the user: its genres, and for TV shows the
 * episode count of each regular season (specials left out), in order. Kept per device, never synced.
 */
data class TitleMeta(
    val genres: List<String>,
    val seasonEpisodes: List<Int>,
    val fetchedAt: Long,
    /** Episodes out when it was fetched (see MediaDetail.airedEpisodes); null when unknown. */
    val airedEpisodes: Int? = null,
)
