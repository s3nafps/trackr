package com.trackr.app.data.mapper

import com.trackr.app.data.remote.tmdb.TmdbDetail
import com.trackr.app.data.remote.tmdb.TmdbResult
import com.trackr.app.domain.model.CastMember
import com.trackr.app.domain.model.MediaDetail
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.SeasonInfo

object TmdbMapper {
    const val IMG = "https://image.tmdb.org/t/p/"

    private val genreNames = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary",
        18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
        9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi", 10770 to "TV Movie", 53 to "Thriller",
        10752 to "War", 37 to "Western", 10759 to "Action & Adventure", 10762 to "Kids", 10763 to "News",
        10764 to "Reality", 10765 to "Sci-Fi & Fantasy", 10766 to "Soap", 10767 to "Talk", 10768 to "War & Politics",
    )

    fun poster(path: String?) = path?.let { "${IMG}w500$it" }
    fun backdrop(path: String?) = path?.let { "${IMG}w1280$it" }
    fun profile(path: String?) = path?.let { "${IMG}w185$it" }

    fun yearOf(date: String?): Int? = date?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()

    private fun scoreOf(avg: Double?, votes: Int?): Double? =
        avg?.takeIf { it > 0.0 && (votes ?: 1) > 0 }

    /** [forcedType] is used when the endpoint is type-specific (trending/movie etc.). */
    fun toItem(r: TmdbResult, forcedType: MediaType? = null): MediaItem? {
        val type = forcedType ?: when (r.mediaType) {
            "movie" -> MediaType.MOVIE
            "tv" -> MediaType.TV
            else -> return null // person etc.
        }
        val title = (if (type == MediaType.MOVIE) r.title else r.name) ?: r.title ?: r.name ?: return null
        val isAnime = type == MediaType.TV && r.genreIds.contains(16) && r.originalLanguage == "ja"
        return MediaItem(
            source = MediaSource.TMDB,
            externalId = r.id.toString(),
            // Japanese animation on TMDB is still tracked as TV there; keep it TV so ids stay in one source.
            type = type,
            title = title,
            posterUrl = poster(r.posterPath),
            backdropUrl = backdrop(r.backdropPath),
            year = yearOf(if (type == MediaType.MOVIE) r.releaseDate else r.firstAirDate),
            score = scoreOf(r.voteAverage, r.voteCount),
            overview = r.overview?.takeIf { it.isNotBlank() },
            genres = r.genreIds.mapNotNull { genreNames[it] },
            subtitle = if (isAnime) "Anime" else null,
        )
    }

    fun toDetail(d: TmdbDetail, type: MediaType): MediaDetail {
        val isMovie = type == MediaType.MOVIE
        val runtime = if (isMovie) d.runtime else d.episodeRunTime.firstOrNull()
        val seasons = d.seasons.filter { it.seasonNumber > 0 }.map {
            SeasonInfo(it.seasonNumber, it.name.ifBlank { "Season ${it.seasonNumber}" }, it.episodeCount, yearOf(it.airDate), poster(it.posterPath))
        }
        val cert = if (isMovie) {
            d.releaseDates?.results?.firstOrNull { it.country == "US" }?.releaseDates
                ?.firstOrNull { it.certification.isNotBlank() }?.certification
        } else d.contentRatings?.results?.firstOrNull { it.country == "US" }?.rating?.takeIf { it.isNotBlank() }
        val item = MediaItem(
            source = MediaSource.TMDB,
            externalId = d.id.toString(),
            type = type,
            title = (if (isMovie) d.title else d.name) ?: d.title ?: d.name ?: "Untitled",
            posterUrl = poster(d.posterPath),
            backdropUrl = backdrop(d.backdropPath),
            year = yearOf(if (isMovie) d.releaseDate else d.firstAirDate),
            score = scoreOf(d.voteAverage, d.voteCount),
            overview = d.overview?.takeIf { it.isNotBlank() },
            genres = d.genres.map { it.name },
            totalEpisodes = if (isMovie) 1 else d.numberOfEpisodes,
            runtimeMinutes = runtime?.takeIf { it > 0 },
            subtitle = d.networks.firstOrNull()?.name,
        )
        return MediaDetail(
            item = item,
            tagline = d.tagline?.takeIf { it.isNotBlank() },
            status = d.status,
            voteCount = d.voteCount,
            cast = d.credits?.cast?.sortedBy { it.order }?.take(15)
                ?.map { CastMember(it.name, it.character?.takeIf { c -> c.isNotBlank() }, profile(it.profilePath)) }.orEmpty(),
            seasons = seasons,
            seasonCount = d.numberOfSeasons,
            studios = d.networks.map { it.name },
            certification = cert,
        )
    }
}
