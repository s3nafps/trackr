package com.trackr.app.data.repository

import com.trackr.app.data.mapper.TmdbMapper
import com.trackr.app.data.remote.anilist.AniListClient
import com.trackr.app.data.remote.tmdb.TmdbApi
import com.trackr.app.data.remote.tmdb.TmdbPage
import com.trackr.app.data.remote.tmdb.TmdbResult
import com.trackr.app.data.util.TtlCache
import com.trackr.app.domain.model.Genre
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.MediaDetail
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaPage
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** [type] is the one media type the filter keeps, or null for all of them. */
enum class SearchFilter(val label: String, val type: MediaType?) {
    ALL("All", null), MOVIES("Movies", MediaType.MOVIE), TV("TV Shows", MediaType.TV), ANIME("Anime", MediaType.ANIME)
}

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
    private val lists = TtlCache<String, MediaPage>(ttlMillis = 5 * 60_000)
    private val details = TtlCache<String, MediaDetail>(ttlMillis = 10 * 60_000)

    /** Cached fetch; on failure serves a stale copy when one exists, otherwise rethrows. */
    private suspend fun cached(key: String, force: Boolean, load: suspend () -> MediaPage): MediaPage {
        if (!force) lists.get(key)?.let { return it }
        return try {
            load().also { lists.put(key, it) }
        } catch (e: Exception) {
            lists.getStale(key) ?: throw e
        }
    }

    private fun TmdbPage<TmdbResult>.toPage(type: MediaType?) = MediaPage(results.mapNotNull { TmdbMapper.toItem(it, type) }, hasMore)

    suspend fun trendingMovies(force: Boolean = false) = trending(MediaType.MOVIE, 1, force).items
    suspend fun trendingTv(force: Boolean = false) = trending(MediaType.TV, 1, force).items
    suspend fun trendingAnime(force: Boolean = false) = trending(MediaType.ANIME, 1, force).items

    /** This week's trending titles of one type, a page at a time. */
    suspend fun trending(type: MediaType, page: Int, force: Boolean = false): MediaPage = cached("t:$type:$page", force) {
        when (type) {
            MediaType.MOVIE -> tmdb.trendingMovies(page).toPage(MediaType.MOVIE)
            MediaType.TV -> tmdb.trendingTv(page).toPage(MediaType.TV)
            MediaType.ANIME -> anilist.trending(page)
        }
    }

    /**
     * Popular titles of one type (all of them when [type] is null), for browsing. With a [genre], only the sources that
     * have that genre are asked.
     */
    suspend fun popular(type: MediaType?, page: Int, force: Boolean = false, genre: Genre? = null): MediaPage = when (type) {
        null -> merged(
            MediaType.entries.filter { genre?.appliesTo(it) != false }.map { t -> suspend { popular(t, page, force, genre) } },
            ::isTmdbAnime,
        )
        else -> cached("p:$type:${genre?.name}:$page", force) {
            when (type) {
                MediaType.MOVIE -> tmdb.discoverMovies(page, withGenres = genre?.tmdbMovie?.toString()).toPage(MediaType.MOVIE)
                MediaType.TV -> tmdb.discoverTv(page, withGenres = genre?.tmdbTv?.toString()).toPage(MediaType.TV)
                MediaType.ANIME -> anilist.popular(page, genre = genre?.anilist)
            }
        }
    }

    /** What TMDB or AniList recommends for a title in the list (from its cached detail). */
    suspend fun recommendationsFor(entry: ListEntry, force: Boolean = false): List<MediaItem> =
        detail(entry.source, entry.externalId, entry.mediaType, force).recommendations

    /** All types interleaved: page [page] of each, as one page. */
    suspend fun trendingAll(page: Int, force: Boolean = false): MediaPage =
        merged(MediaType.entries.map { t -> suspend { trending(t, page, force) } }, ::isTmdbAnime)

    suspend fun airingThisWeek(force: Boolean = false, nowEpoch: Long = System.currentTimeMillis() / 1000) =
        cached("aw", force) { MediaPage(anilist.airingThisWeek(nowEpoch, nowEpoch + 7 * 24 * 3600), hasMore = false) }.items

    suspend fun search(query: String, filter: SearchFilter): List<MediaItem> = search(query, filter, 1).items

    /** Partial results are returned when only one backend fails; throws only if all requested backends fail. */
    suspend fun search(query: String, filter: SearchFilter, page: Int): MediaPage {
        val q = query.trim()
        if (q.isEmpty()) return MediaPage(emptyList(), hasMore = false)
        val key = "s:${filter.name}:${q.lowercase()}:$page"
        lists.get(key)?.let { return it }

        val tmdbSearch: (suspend () -> MediaPage)? = when (filter) {
            SearchFilter.MOVIES -> suspend { tmdb.searchMovies(q, page = page).toPage(MediaType.MOVIE) }
            SearchFilter.TV -> suspend { tmdb.searchTv(q, page = page).toPage(MediaType.TV) }
            SearchFilter.ALL -> suspend { tmdb.searchMulti(q, page).toPage(null) }
            SearchFilter.ANIME -> null
        }
        val aniSearch: (suspend () -> MediaPage)? =
            if (filter == SearchFilter.ALL || filter == SearchFilter.ANIME) suspend { anilist.search(q, page) } else null
        return merged(listOfNotNull(tmdbSearch, aniSearch)) { filter == SearchFilter.ALL && isTmdbAnime(it) }
            .also { lists.put(key, it) }
    }

    /** Mixed lists take anime from AniList, so TMDB's copies of the same shows are dropped there. */
    private fun isTmdbAnime(item: MediaItem) = item.source == MediaSource.TMDB && item.subtitle == "Anime"

    /**
     * Runs [loads] in parallel and interleaves their items, minus [drop]. A failing load is skipped as long as one
     * succeeds; there are more pages while any successful load has more.
     */
    private suspend fun merged(loads: List<suspend () -> MediaPage>, drop: (MediaItem) -> Boolean): MediaPage = coroutineScope {
        val results = loads.map { async { runCatching { it() } } }.awaitAll()
        val ok = results.mapNotNull { it.getOrNull() }
        if (ok.isEmpty()) throw results.firstOrNull()?.exceptionOrNull() ?: IOException("Request failed")
        MediaPage(interleave(ok.map { p -> p.items.filterNot(drop) }), hasMore = ok.any { it.hasMore })
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
