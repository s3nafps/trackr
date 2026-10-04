package com.trackr.app.data.remote.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbPage<T>(val page: Int = 1, val results: List<T> = emptyList())

@Serializable
data class TmdbResult(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val overview: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
)

@Serializable
data class TmdbGenre(val id: Int, val name: String)

@Serializable
data class TmdbCastDto(
    val name: String,
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int = 0,
)

@Serializable
data class TmdbCredits(val cast: List<TmdbCastDto> = emptyList())

@Serializable
data class TmdbSeasonDto(
    @SerialName("season_number") val seasonNumber: Int,
    val name: String = "",
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
)

@Serializable
data class TmdbNetwork(val name: String)

@Serializable
data class TmdbReleaseDates(val results: List<TmdbReleaseCountry> = emptyList())

@Serializable
data class TmdbReleaseCountry(
    @SerialName("iso_3166_1") val country: String,
    @SerialName("release_dates") val releaseDates: List<TmdbRelease> = emptyList(),
)

@Serializable
data class TmdbRelease(val certification: String = "")

@Serializable
data class TmdbContentRatings(val results: List<TmdbContentRating> = emptyList())

@Serializable
data class TmdbContentRating(@SerialName("iso_3166_1") val country: String, val rating: String = "")

@Serializable
data class TmdbDetail(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val tagline: String? = null,
    val overview: String? = null,
    val status: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    val genres: List<TmdbGenre> = emptyList(),
    val seasons: List<TmdbSeasonDto> = emptyList(),
    val networks: List<TmdbNetwork> = emptyList(),
    val credits: TmdbCredits? = null,
    @SerialName("release_dates") val releaseDates: TmdbReleaseDates? = null,
    @SerialName("content_ratings") val contentRatings: TmdbContentRatings? = null,
    @SerialName("next_episode_to_air") val nextEpisodeToAir: TmdbEpisodeStub? = null,
    @SerialName("watch/providers") val watchProviders: TmdbWatchProviders? = null,
)

/** `watch/providers` (JustWatch data), keyed by ISO 3166-1 country. */
@Serializable
data class TmdbWatchProviders(val results: Map<String, TmdbRegionProviders> = emptyMap())

@Serializable
data class TmdbRegionProviders(
    /** TMDB's watch page for the title in this region; links out to each provider. */
    val link: String? = null,
    val flatrate: List<TmdbProvider> = emptyList(),
    val free: List<TmdbProvider> = emptyList(),
    val ads: List<TmdbProvider> = emptyList(),
    val rent: List<TmdbProvider> = emptyList(),
    val buy: List<TmdbProvider> = emptyList(),
)

@Serializable
data class TmdbProvider(
    @SerialName("provider_name") val name: String,
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("display_priority") val displayPriority: Int = Int.MAX_VALUE,
)

@Serializable
data class TmdbEpisodeStub(
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
)
