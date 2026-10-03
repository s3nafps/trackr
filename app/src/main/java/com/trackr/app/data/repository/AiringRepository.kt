package com.trackr.app.data.repository

import com.trackr.app.data.airing.AiringScheduler
import com.trackr.app.data.local.AiringDao
import com.trackr.app.data.local.AiringEntity
import com.trackr.app.data.local.ListEntryDao
import com.trackr.app.data.local.UserPrefs
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.util.AiringPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiringRepository @Inject constructor(
    private val listDao: ListEntryDao,
    private val airingDao: AiringDao,
    private val media: MediaRepository,
    private val prefs: UserPrefs,
    private val scheduler: AiringScheduler,
) {
    val upcoming: Flow<List<AiringEntity>> = airingDao.observeAll()

    fun forTitle(source: String, id: String): Flow<AiringEntity?> =
        airingDao.observeAll().map { rows -> rows.firstOrNull { it.source == source && it.externalId == id } }

    /** Re-derives the "next drop" row of every eligible title, drops stale rows and re-applies alarms. */
    suspend fun refresh(now: Long = System.currentTimeMillis()) {
        val enabled = prefs.airingEnabled.first()
        val eligible = if (!enabled) emptyList() else listDao.getAllRaw().filter {
            !it.deleted && AiringPolicy.isEligible(ListStatus.fromKey(it.status), it.notify)
        }
        val existing = airingDao.getAll().associateBy { it.source to it.externalId }
        val upserted = mutableListOf<AiringEntity>()
        val removed = mutableListOf<AiringEntity>()

        for (entry in eligible) {
            val key = entry.source to entry.externalId
            val item = try {
                media.detail(MediaSource.fromKey(entry.source), entry.externalId, MediaType.fromKey(entry.mediaType), force = true).item
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue // keep whatever row we had
            }
            val at = item.airingAtEpoch
            val old = existing[key]
            if (at == null) {
                if (old != null) { airingDao.delete(entry.source, entry.externalId); removed += old }
                continue
            }
            val row = AiringEntity(
                entry.source, entry.externalId, entry.mediaType, entry.title, item.airingEpisode, at * 1000,
                if (item.airingDateOnly) "DATE" else "TIME",
                notified = old != null && old.episode == item.airingEpisode && old.notified,
            )
            airingDao.upsert(row)
            upserted += row
        }

        val eligibleKeys = eligible.map { it.source to it.externalId }.toSet()
        existing.filterKeys { it !in eligibleKeys }.forEach { (key, old) ->
            airingDao.delete(key.first, key.second); removed += old
        }
        scheduler.apply(upserted, removed, now)
    }

    suspend fun clearAll() {
        scheduler.cancelAll(airingDao.getAll())
        airingDao.clear()
    }
}
