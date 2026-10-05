package com.trackr.app.data.mapper

import com.trackr.app.data.remote.tmdb.TmdbDetail
import com.trackr.app.data.remote.tmdb.TmdbProvider
import com.trackr.app.data.remote.tmdb.TmdbRegionProviders
import com.trackr.app.data.remote.tmdb.TmdbResult
import com.trackr.app.data.remote.tmdb.TmdbVideo
import com.trackr.app.domain.model.CastMember
import com.trackr.app.domain.model.MediaDetail
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.SeasonInfo
import com.trackr.app.domain.model.Trailer
import com.trackr.app.domain.model.WatchOptions
import com.trackr.app.domain.model.WatchProvider
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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
    fun logo(path: String?) = path?.let { "${IMG}w92$it" }

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

    /** Epoch seconds of 09:00 on the ISO [date] in [zone]; null if unparsable. */
    fun localNineAm(date: String, zone: ZoneId = ZoneId.systemDefault()): Long? =
        runCatching { LocalDate.parse(date).atTime(LocalTime.of(9, 0)).atZone(zone).toEpochSecond() }.getOrNull()

    /** Providers for [region] only: another country's catalogue would be misleading. */
    fun watchOptions(p: TmdbRegionProviders?, region: String): WatchOptions {
        fun List<TmdbProvider>.toProviders() =
            sortedBy { it.displayPriority }.distinctBy { it.name }.map { WatchProvider(it.name, logo(it.logoPath), p?.link) }
        if (p == null) return WatchOptions(region)
        return WatchOptions(
            region = region,
            stream = p.flatrate.toProviders(),
            free = (p.free + p.ads).toProviders(),
            rent = p.rent.toProviders(),
            buy = p.buy.toProviders(),
        )
    }

    /** Best YouTube video: an official trailer, then any trailer, then a teaser. */
    fun trailer(videos: List<TmdbVideo>): Trailer? {
        val yt = videos.filter { it.site == "YouTube" && it.key.isNotBlank() }
        val best = yt.firstOrNull { it.type == "Trailer" && it.official } ?: yt.firstOrNull { it.type == "Trailer" }
            ?: yt.firstOrNull { it.type == "Teaser" } ?: return null
        return Trailer.youtube(best.key)
    }

    /** TMDB recommendations, falling back to "similar" (genre/keyword based) when there are none yet. */
    fun recommendations(d: TmdbDetail, type: MediaType): List<MediaItem> =
        d.recommendations?.results.orEmpty().ifEmpty { d.similar?.results.orEmpty() }
            .filter { it.id != d.id }
            .mapNotNull { toItem(it, type) }
            .distinctBy { it.key }
            .take(MAX_RECOMMENDATIONS)

    const val MAX_RECOMMENDATIONS = 20

    fun toDetail(d: TmdbDetail, type: MediaType, today: LocalDate = LocalDate.now(), region: String = "US"): MediaDetail {
        val isMovie = type == MediaType.MOVIE
        val runtime = if (isMovie) d.runtime else d.episodeRunTime.firstOrNull()
        val seasons = d.seasons.filter { it.seasonNumber > 0 }.map {
            SeasonInfo(it.seasonNumber, it.name.ifBlank { "Season ${it.seasonNumber}" }, it.episodeCount, yearOf(it.airDate), poster(it.posterPath))
        }
        val cert = if (isMovie) {
            d.releaseDates?.results?.firstOrNull { it.country == "US" }?.releaseDates
                ?.firstOrNull { it.certification.isNotBlank() }?.certification
        } else d.contentRatings?.results?.firstOrNull { it.country == "US" }?.rating?.takeIf { it.isNotBlank() }
        val (airingEpisode, airingAt) = if (isMovie) {
            val date = d.releaseDate?.takeIf { r -> runCatching { !LocalDate.parse(r).isBefore(today) }.getOrDefault(false) }
            null to date?.let { localNineAm(it) }
        } else {
            d.nextEpisodeToAir?.let { n -> n.episodeNumber to n.airDate?.let { localNineAm(it) } } ?: (null to null)
        }
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
            airingEpisode = airingEpisode.takeIf { airingAt != null },
            airingSeason = d.nextEpisodeToAir?.seasonNumber.takeIf { !isMovie && airingAt != null && airingEpisode != null },
            airingAtEpoch = airingAt,
            airingDateOnly = airingAt != null,
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
            watch = watchOptions(d.watchProviders?.results?.get(region), region),
            trailer = trailer(d.videos?.results.orEmpty()),
            recommendations = recommendations(d, type),
        )
    }
}
