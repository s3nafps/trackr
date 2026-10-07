package com.trackr.app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Unified summary model for TMDB movies/TV and AniList anime. Serializable so browse results can be saved offline. */
@Serializable
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
    /** TMDB numbers episodes within a season: the season of [airingEpisode]. Null when numbered across the show (AniList). */
    val airingSeason: Int? = null,
    val airingEpisode: Int? = null,
    /** Epoch seconds. */
    val airingAtEpoch: Long? = null,
    /** True when only the date is known (TMDB); the time is a 09:00 local stand-in. */
    val airingDateOnly: Boolean = false,
) {
    val key: String get() = "${source.key}:$externalId"
}

@Serializable
data class CastMember(val name: String, val role: String?, val imageUrl: String?)

/** One place to watch a title. [url] opens it; [color] is the site's brand colour (hex) when the source gives one. */
@Serializable
data class WatchProvider(val name: String, val logoUrl: String?, val url: String?, val color: String? = null)

/**
 * Where a title can be watched. TMDB data is per [region] (ISO 3166-1 country) and comes from JustWatch;
 * AniList streaming links are not region-specific, so [region] is null for them.
 */
@Serializable
data class WatchOptions(
    val region: String? = null,
    val stream: List<WatchProvider> = emptyList(),
    val free: List<WatchProvider> = emptyList(),
    val rent: List<WatchProvider> = emptyList(),
    val buy: List<WatchProvider> = emptyList(),
) {
    val isEmpty: Boolean get() = stream.isEmpty() && free.isEmpty() && rent.isEmpty() && buy.isEmpty()
}

/** A trailer video; [url] opens it in the YouTube app or the browser. */
@Serializable
data class Trailer(val url: String, val thumbnailUrl: String?) {
    companion object {
        fun youtube(key: String) = Trailer("https://www.youtube.com/watch?v=$key", "https://img.youtube.com/vi/$key/hqdefault.jpg")
    }
}

/** A title linked to another one; [relation] is a display label such as "Sequel". */
@Serializable
data class RelatedItem(val relation: String, val item: MediaItem)

@Serializable
data class SeasonInfo(val number: Int, val name: String, val episodeCount: Int, val year: Int?, val posterUrl: String?)

@Serializable
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
    val trailer: Trailer? = null,
    val related: List<RelatedItem> = emptyList(),
    val recommendations: List<MediaItem> = emptyList(),
    /**
     * Episodes already out, counted across the show like progress (regular seasons only), or null when the source
     * doesn't say. Lets the app tell "next episode" from "caught up, waiting for the next one".
     */
    val airedEpisodes: Int? = null,
)

/** Result wrapper so UI can render loading/error/content without exceptions leaking. */
/**
 * One page of a paged list; [hasMore] says whether asking for the next page is worthwhile. [fromCache] marks a page
 * served from the copy saved on the device because the source couldn't be reached.
 */
@Serializable
data class MediaPage(val items: List<MediaItem>, val hasMore: Boolean, @Transient val fromCache: Boolean = false)

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Success<T>(val data: T, val stale: Boolean = false) : Load<T>
    data class Failure(val message: String) : Load<Nothing>
}
