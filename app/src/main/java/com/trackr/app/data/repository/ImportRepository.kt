package com.trackr.app.data.repository

import com.trackr.app.data.importer.Backup
import com.trackr.app.data.importer.ImportException
import com.trackr.app.data.importer.ImportSummary
import com.trackr.app.data.importer.Letterboxd
import com.trackr.app.data.importer.LetterboxdFilm
import com.trackr.app.data.importer.MalExport
import com.trackr.app.data.importer.aniListStatus
import com.trackr.app.data.importer.tenPointRating
import com.trackr.app.data.mapper.TmdbMapper
import com.trackr.app.data.remote.anilist.AniListClient
import com.trackr.app.data.remote.tmdb.TmdbApi
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Imports lists from AniList, MyAnimeList and Letterboxd (and Trackr backups) into the local list; exports it. */
@Singleton
class ImportRepository @Inject constructor(
    private val anilist: AniListClient,
    private val tmdb: TmdbApi,
    private val lists: ListRepository,
) {
    /** [userName] may also be a profile URL or "@name". */
    suspend fun fromAniList(userName: String): ImportSummary {
        val name = userName.trim().substringAfter("anilist.co/user/").trim('/', ' ').removePrefix("@")
        if (name.isEmpty()) throw ImportException("Enter your AniList username.")
        val rows = try {
            anilist.userAnimeList(name)
        } catch (e: IOException) {
            throw ImportException("Couldn't load $name's AniList list. Check the username and that the list is public.")
        }
        return add(rows.mapNotNull { r -> aniListStatus(r.status)?.let { entry(r.item, it, tenPointRating(r.score), r.progress) } })
    }

    /** [files] is the opened MyAnimeList export (an .xml, usually delivered as .xml.gz). */
    suspend fun fromMal(files: Map<String, String>): ImportSummary {
        val xml = files.values.firstOrNull { it.contains("<myanimelist") } ?: throw ImportException("That isn't a MyAnimeList export file.")
        val mal = withContext(Dispatchers.Default) { MalExport.parse(xml) }
        val byId = try {
            anilist.byMalIds(mal.map { it.malId })
        } catch (e: IOException) {
            throw ImportException("Couldn't reach AniList to match your titles. Try again when you're online.")
        }
        return add(
            mal.mapNotNull { m -> byId[m.malId]?.let { entry(it, m.status, m.score, m.watched) } },
            notFound = mal.filter { it.malId !in byId }.map { it.title },
        )
    }

    /** [files] is the opened Letterboxd export zip (or one CSV from it); films are matched on TMDB by title and year. */
    suspend fun fromLetterboxd(files: Map<String, String>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ImportSummary {
        val films = withContext(Dispatchers.Default) { Letterboxd.parse(files) }
        val done = AtomicInteger()
        val gate = Semaphore(SEARCH_CONCURRENCY)
        val matched = try {
            coroutineScope {
                films.map { f ->
                    async { gate.withPermit { f to match(f) }.also { onProgress(done.incrementAndGet(), films.size) } }
                }.awaitAll()
            }
        } catch (e: IOException) {
            throw ImportException("Couldn't reach TMDB to match your films. Try again when you're online.")
        }
        return add(
            matched.mapNotNull { (f, item) ->
                item?.let { entry(it.copy(totalEpisodes = 1), if (f.watched) ListStatus.COMPLETED else ListStatus.PLAN_TO_WATCH, f.rating, if (f.watched) 1 else 0) }
            },
            notFound = matched.filter { it.second == null }.map { (f, _) -> f.year?.let { "${f.name} ($it)" } ?: f.name },
        )
    }

    suspend fun fromBackup(files: Map<String, String>): ImportSummary {
        val json = files.values.firstOrNull() ?: throw ImportException("That file is empty.")
        return add(withContext(Dispatchers.Default) { Backup.fromJson(json) })
    }

    // Parsing and encoding can take a while for big lists, so they stay off the main thread.
    suspend fun exportJson(): String = lists.entries.first().let { withContext(Dispatchers.Default) { Backup.toJson(it) } }

    suspend fun exportCsv(): String = lists.entries.first().let { withContext(Dispatchers.Default) { Backup.toCsv(it) } }

    /** Same-year result first, then TMDB's best match; a year with no results is retried without it. */
    private suspend fun match(f: LetterboxdFilm): MediaItem? {
        val withYear = f.year?.let { tmdb.searchMovies(f.name, year = it).results }.orEmpty()
        val results = withYear.ifEmpty { tmdb.searchMovies(f.name).results }
        val pick = results.firstOrNull { TmdbMapper.yearOf(it.releaseDate) == f.year } ?: results.firstOrNull()
        return pick?.let { TmdbMapper.toItem(it, MediaType.MOVIE) }
    }

    private fun entry(item: MediaItem, status: ListStatus, rating: Int?, progress: Int) = ListEntry(
        item.source, item.externalId, item.type, item.title, item.posterUrl, item.backdropUrl, status, rating, progress,
        item.totalEpisodes, updatedAt = 0L,
    )

    private suspend fun add(entries: List<ListEntry>, notFound: List<String> = emptyList()): ImportSummary {
        val unique = entries.distinctBy { it.key }
        val added = lists.importEntries(unique)
        return ImportSummary(added = added, skipped = unique.size - added, notFound = notFound)
    }

    private companion object {
        const val SEARCH_CONCURRENCY = 4
    }
}
