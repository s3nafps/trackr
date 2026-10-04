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
    /** Epoch seconds. */
    val airingAtEpoch: Long? = null,
    /** True when only the date is known (TMDB); the time is a 09:00 local stand-in. */
    val airingDateOnly: Boolean = false,
) {
    val key: String get() = "${source.key}:$externalId"
}

data class CastMember(val name: String, val role: String?, val imageUrl: String?)

/** One place to watch a title. [url] opens it; [color] is the site's brand colour (hex) when the source gives one. */
data class WatchProvider(val name: String, val logoUrl: String?, val url: String?, val color: String? = null)

/**
 * Where a title can be watched. TMDB data is per [region] (ISO 3166-1 country) and comes from JustWatch;
 * AniList streaming links are not region-specific, so [region] is null for them.
 */
data class WatchOptions(
    val region: String? = null,
    val stream: List<WatchProvider> = emptyList(),
    val free: List<WatchProvider> = emptyList(),
    val rent: List<WatchProvider> = emptyList(),
    val buy: List<WatchProvider> = emptyList(),
) {
    val isEmpty: Boolean get() = stream.isEmpty() && free.isEmpty() && rent.isEmpty() && buy.isEmpty()
}

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
    val watch: WatchOptions = WatchOptions(),
)

/** Result wrapper so UI can render loading/error/content without exceptions leaking. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Success<T>(val data: T, val stale: Boolean = false) : Load<T>
    data class Failure(val message: String) : Load<Nothing>
}
