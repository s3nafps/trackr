package com.trackr.app.domain.model

/** Unified summary model for TMDB movies/TV and AniList anime. */
data class MediaItem(
    val source: MediaSource,
    val externalId: String,
    val type: MediaType,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String? = null,
    val year: Int? = null,
    /** Normalised 0–10 score, null when unknown. */
    val score: Double? = null,
    val overview: String? = null,
    val genres: List<String> = emptyList(),
    val totalEpisodes: Int? = null,
    val runtimeMinutes: Int? = null,
    /** Extra line: studio, network, "3 Seasons", ... */
    val subtitle: String? = null,
    val airingEpisode: Int? = null,
    val airingAtEpoch: Long? = null,
) {
    val key: String get() = "${source.key}:$externalId"
}

data class CastMember(val name: String, val role: String?, val imageUrl: String?)

data class SeasonInfo(val number: Int, val name: String, val episodeCount: Int, val year: Int?, val posterUrl: String?)

data class MediaDetail(
    val item: MediaItem,
    val tagline: String? = null,
    val status: String? = null,
    val voteCount: Int? = null,
    val cast: List<CastMember> = emptyList(),
    val seasons: List<SeasonInfo> = emptyList(),
    val seasonCount: Int? = null,
    val studios: List<String> = emptyList(),
    val certification: String? = null,
)

/** Result wrapper so UI can render loading/error/content without exceptions leaking. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Success<T>(val data: T, val stale: Boolean = false) : Load<T>
    data class Failure(val message: String) : Load<Nothing>
}
