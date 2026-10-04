package com.trackr.app.data.repository

import com.trackr.app.data.mapper.TmdbMapper
import com.trackr.app.data.remote.anilist.AniListClient
import com.trackr.app.data.remote.tmdb.TmdbApi
import com.trackr.app.data.util.TtlCache
import com.trackr.app.domain.model.MediaDetail
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class SearchFilter(val label: String) { ALL("All"), MOVIES("Movies"), TV("TV Shows"), ANIME("Anime") }

fun Throwable.userMessage(): String = when (this) {
    is HttpException -> when (code()) {
        401, 403 -> "The server rejected our credentials."
        429 -> "Too many requests – please try again in a moment."
        in 500..599 -> "The service is having trouble. Try again soon."
        else -> "Request failed (${code()})."
    }
    is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException ->
        "Can't reach the server. Check your connection."
    is IOException -> message?.takeIf { it.isNotBlank() } ?: "Network error. Try again."
    else -> message ?: "Something went wrong."
}

@Singleton
class MediaRepository @Inject constructor(
    private val tmdb: TmdbApi,
    private val anilist: AniListClient,
) {
    private val lists = TtlCache<String, List<MediaItem>>(ttlMillis = 5 * 60_000)
    private val details = TtlCache<String, MediaDetail>(ttlMillis = 10 * 60_000)

    /** Cached fetch; on failure serves a stale copy when one exists, otherwise rethrows. */
    private suspend fun cached(key: String, force: Boolean, load: suspend () -> List<MediaItem>): List<MediaItem> {
        if (!force) lists.get(key)?.let { return it }
        return try {
            load().also { lists.put(key, it) }
        } catch (e: Exception) {
            lists.getStale(key) ?: throw e
        }
    }

    suspend fun trendingMovies(force: Boolean = false) = cached("tm", force) {
        tmdb.trendingMovies().results.mapNotNull { TmdbMapper.toItem(it, MediaType.MOVIE) }
    }

    suspend fun trendingTv(force: Boolean = false) = cached("tt", force) {
        tmdb.trendingTv().results.mapNotNull { TmdbMapper.toItem(it, MediaType.TV) }
    }

    suspend fun trendingAnime(force: Boolean = false) = cached("ta", force) { anilist.trending() }

    suspend fun airingThisWeek(force: Boolean = false, nowEpoch: Long = System.currentTimeMillis() / 1000) =
        cached("aw", force) { anilist.airingThisWeek(nowEpoch, nowEpoch + 7 * 24 * 3600) }

    /** Partial results are returned when only one backend fails; throws only if all requested backends fail. */
    suspend fun search(query: String, filter: SearchFilter): List<MediaItem> = coroutineScope {
        val q = query.trim()
        if (q.isEmpty()) return@coroutineScope emptyList()
        val key = "s:${filter.name}:${q.lowercase()}"
        lists.get(key)?.let { return@coroutineScope it }

        val tmdbJob = if (filter != SearchFilter.ANIME) async {
            runCatching {
                when (filter) {
                    SearchFilter.MOVIES -> tmdb.searchMovies(q).results.mapNotNull { TmdbMapper.toItem(it, MediaType.MOVIE) }
                    SearchFilter.TV -> tmdb.searchTv(q).results.mapNotNull { TmdbMapper.toItem(it, MediaType.TV) }
                    else -> tmdb.searchMulti(q).results.mapNotNull { TmdbMapper.toItem(it) }
                        // anime is covered by AniList in "All"; avoid duplicates
                        .filterNot { it.subtitle == "Anime" }
                }
            }
        } else null
        val aniJob = if (filter == SearchFilter.ALL || filter == SearchFilter.ANIME) async { runCatching { anilist.search(q) } } else null

        val a = tmdbJob?.await()
        val b = aniJob?.await()
        val results = listOfNotNull(a, b)
        val ok = results.mapNotNull { it.getOrNull() }
        if (ok.isEmpty() && results.isNotEmpty()) throw results.first().exceptionOrNull() ?: IOException("Search failed")
        val merged = interleave(ok)
        lists.put(key, merged)
        merged
    }

    /** [region] picks the TMDB watch-provider country; it defaults to the device's country. */
    suspend fun detail(
        source: MediaSource, id: String, type: MediaType, force: Boolean = false, region: String = deviceRegion(),
    ): MediaDetail {
        val key = "${source.key}:$type:$id:$region"
        if (!force) details.get(key)?.let { return it }
        return try {
            val d = when (source) {
                MediaSource.TMDB -> {
                    val intId = id.toIntOrNull() ?: throw IOException("Invalid id")
                    TmdbMapper.toDetail(if (type == MediaType.MOVIE) tmdb.movieDetail(intId) else tmdb.tvDetail(intId), type, region = region)
                }
                MediaSource.ANILIST -> anilist.detail(id.toIntOrNull() ?: throw IOException("Invalid id"))
            }
            details.put(key, d); d
        } catch (e: Exception) {
            details.getStale(key) ?: throw e
        }
    }

    companion object {
        /** ISO 3166-1 country of the device locale, or US when the locale has none. */
        fun deviceRegion(): String = java.util.Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"

        fun interleave(lists: List<List<MediaItem>>): List<MediaItem> {
            val out = ArrayList<MediaItem>()
            val max = lists.maxOfOrNull { it.size } ?: 0
            for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
            return out
        }
    }
}
