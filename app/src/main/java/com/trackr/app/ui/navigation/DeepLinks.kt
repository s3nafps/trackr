package com.trackr.app.ui.navigation

import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import java.net.URI

/** A title to open straight on its Detail screen (notification, widget or link tap). */
data class DetailTarget(val source: String, val type: String, val id: String)

/**
 * Links that open a title in Trackr: our own `trackr://title/{source}/{type}/{id}` and the TMDB/AniList pages we
 * share, so a shared link opens the app when it's installed and the website when it isn't.
 */
object DeepLinks {
    fun appUri(source: MediaSource, type: MediaType, id: String) = "trackr://title/${source.key}/${type.key}/$id"

    /** The public page shared for a title. */
    fun webUrl(source: MediaSource, type: MediaType, id: String) = when (source) {
        MediaSource.TMDB -> "https://www.themoviedb.org/${if (type == MediaType.MOVIE) "movie" else "tv"}/$id"
        MediaSource.ANILIST -> "https://anilist.co/anime/$id"
    }

    private val tmdbHosts = setOf("themoviedb.org", "www.themoviedb.org")
    private val anilistHosts = setOf("anilist.co", "www.anilist.co")

    fun parse(url: String?): DetailTarget? {
        val uri = url?.let { runCatching { URI(it.trim()) }.getOrNull() } ?: return null
        val host = uri.host?.lowercase()
        val path = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        return when {
            uri.scheme == "trackr" && host == "title" && path.size == 3 -> {
                val (source, type, id) = path
                val valid = when (source) {
                    MediaSource.TMDB.key -> type == MediaType.MOVIE.key || type == MediaType.TV.key
                    MediaSource.ANILIST.key -> type == MediaType.ANIME.key
                    else -> false
                }
                if (valid && id.isNumericId()) DetailTarget(source, type, id) else null
            }
            uri.isWeb() && host in tmdbHosts && path.size >= 2 && path[0] in setOf("movie", "tv") ->
                // TMDB slugs the id: /movie/438631-dune
                path[1].takeWhile { it.isDigit() }.takeIf { it.isNumericId() }
                    ?.let { DetailTarget(MediaSource.TMDB.key, if (path[0] == "movie") MediaType.MOVIE.key else MediaType.TV.key, it) }
            uri.isWeb() && host in anilistHosts && path.size >= 2 && path[0] == "anime" ->
                path[1].takeIf { it.isNumericId() }?.let { DetailTarget(MediaSource.ANILIST.key, MediaType.ANIME.key, it) }
            else -> null
        }
    }

    private fun URI.isWeb() = scheme == "https" || scheme == "http"

    private fun String.isNumericId() = isNotEmpty() && length <= 12 && all { it.isDigit() }
}
