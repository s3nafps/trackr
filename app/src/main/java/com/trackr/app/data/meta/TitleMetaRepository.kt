package com.trackr.app.data.meta

import com.trackr.app.data.local.TitleMetaDao
import com.trackr.app.data.local.TitleMetaEntity
import com.trackr.app.data.repository.MediaRepository
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.SeasonInfo
import com.trackr.app.domain.model.TitleMeta
import com.trackr.app.domain.util.SeasonProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Genres and season sizes of listed titles (see [TitleMeta]), stored per device. They come from each title's detail,
 * which [backfill] fetches for titles that don't have them yet; opening a title page records them right away.
 */
@Singleton
class TitleMetaRepository @Inject constructor(private val dao: TitleMetaDao, private val media: MediaRepository) {
    /** Keyed like [ListEntry.key] ("source:externalId"). */
    val all: Flow<Map<String, TitleMeta>> = dao.observeAll().map { rows -> rows.associate { "${it.source}:${it.externalId}" to it.toDomain() } }

    /** Stores what a title's detail says: its genres and, for TV, its regular seasons' episode counts. */
    suspend fun record(item: MediaItem, seasons: List<SeasonInfo>, now: Long = System.currentTimeMillis()) {
        dao.upsert(
            TitleMetaEntity(
                item.source.key, item.externalId,
                genres = item.genres.joinToString(SEP),
                seasonEpisodes = SeasonProgress.regular(seasons).joinToString(","),
                fetchedAt = now,
            ),
        )
    }

    /**
     * Fetches details for listed titles with nothing stored yet, and refreshes the seasons of TV shows being watched
     * (new seasons appear) once a week. Titles that fail are skipped until the next run. Returns how many are still due.
     */
    suspend fun backfill(entries: List<ListEntry>, now: Long = System.currentTimeMillis(), max: Int = MAX_PER_RUN): Int {
        val stored = dao.getAll().associateBy { it.source to it.externalId }
        val due = entries.filter { e ->
            val m = stored[e.source.key to e.externalId]
            m == null || (e.mediaType == MediaType.TV && e.status == ListStatus.WATCHING && now - m.fetchedAt > SEASON_REFRESH_MS)
        }
        for (e in due.take(max)) {
            try {
                val d = media.detail(e.source, e.externalId, e.mediaType)
                record(d.item, d.seasons, now)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
                // Offline, rate-limited or gone from the source: tried again on a later run.
            }
        }
        return (due.size - max).coerceAtLeast(0)
    }

    companion object {
        const val MAX_PER_RUN = 120
        const val SEASON_REFRESH_MS = 7L * 24 * 60 * 60 * 1000
        private const val SEP = "\u001F"

        fun TitleMetaEntity.toDomain() = TitleMeta(
            genres = genres.split(SEP).filter { it.isNotBlank() },
            seasonEpisodes = seasonEpisodes.split(',').mapNotNull { it.trim().toIntOrNull() },
            fetchedAt = fetchedAt,
        )
    }
}
