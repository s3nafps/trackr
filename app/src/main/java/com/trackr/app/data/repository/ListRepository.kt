package com.trackr.app.data.repository

import com.trackr.app.data.airing.AiringRefreshScheduler
import com.trackr.app.data.local.ListEntryDao
import com.trackr.app.data.mapper.ListEntryMapper.toDomain
import com.trackr.app.data.mapper.ListEntryMapper.toEntity
import com.trackr.app.data.remote.supabase.ListEntryDto
import com.trackr.app.data.sync.ListRemote
import com.trackr.app.data.sync.ListSyncer
import com.trackr.app.data.sync.SyncScheduler
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseListRemote @Inject constructor(private val supabase: SupabaseClient) : ListRemote {
    override suspend fun fetchAll(userId: String): List<ListEntryDto> =
        supabase.from("list_entries").select { filter { eq("user_id", userId) } }.decodeList()

    override suspend fun upsert(dto: ListEntryDto) {
        supabase.from("list_entries").upsert(dto) { onConflict = "user_id,source,external_id" }
    }

    override suspend fun delete(userId: String, source: String, externalId: String) {
        supabase.from("list_entries").delete {
            filter { eq("user_id", userId); eq("source", source); eq("external_id", externalId) }
        }
    }
}

/** Offline-first list: all writes go to Room and are pushed by [sync] (WorkManager + on demand). */
@Singleton
class ListRepository @Inject constructor(
    private val dao: ListEntryDao,
    private val remote: SupabaseListRemote,
    private val auth: AuthRepository,
    private val scheduler: SyncScheduler,
    private val airingRefresh: AiringRefreshScheduler,
) {
    /** The worker, pull-to-refresh, "Sync now" and sign-out can all call [sync]; runs never overlap, nor with [clearLocal]. */
    private val syncLock = Mutex()

    val entries: Flow<List<ListEntry>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun entry(item: MediaItem): Flow<ListEntry?> = dao.observe(item.source.key, item.externalId).map { it?.toDomain() }
    fun entry(source: String, id: String): Flow<ListEntry?> = dao.observe(source, id).map { it?.toDomain() }

    private suspend fun write(entry: ListEntry) {
        dao.upsert(entry.copy(updatedAt = System.currentTimeMillis()).toEntity(dirty = true))
        scheduler.syncNow()
        airingRefresh.refreshNow()
    }

    suspend fun save(item: MediaItem, status: ListStatus, rating: Int?, progress: Int) {
        val total = item.totalEpisodes
        val fixedProgress = when {
            status == ListStatus.COMPLETED && total != null -> total
            total != null -> progress.coerceIn(0, total)
            else -> progress.coerceAtLeast(0)
        }
        write(
            ListEntry(
                item.source, item.externalId, item.type, item.title, item.posterUrl, item.backdropUrl,
                status, rating?.coerceIn(1, 10), fixedProgress, total, 0L,
            ),
        )
    }

    suspend fun update(entry: ListEntry, status: ListStatus = entry.status, rating: Int? = entry.rating, progress: Int = entry.progress) {
        val p = entry.totalEpisodes?.let { progress.coerceIn(0, it) } ?: progress.coerceAtLeast(0)
        write(entry.copy(status = status, rating = rating?.coerceIn(1, 10), progress = p))
    }

    suspend fun setNotify(entry: ListEntry, on: Boolean) = write(entry.copy(notify = on))

    /**
     * Adds imported entries that aren't in the list yet (a removed entry counts as absent) and never touches existing
     * ones. Returns how many were added; sync and airing refresh are scheduled once for the batch.
     */
    suspend fun importEntries(entries: List<ListEntry>): Int {
        val now = System.currentTimeMillis()
        var added = 0
        entries.distinctBy { it.key }.forEach { e ->
            val existing = dao.get(e.source.key, e.externalId)
            if (existing != null && !existing.deleted) return@forEach
            val total = e.totalEpisodes
            val progress = when {
                e.status == ListStatus.COMPLETED && total != null -> total
                total != null -> e.progress.coerceIn(0, total)
                else -> e.progress.coerceAtLeast(0)
            }
            val rating = e.rating?.takeIf { it > 0 }?.coerceIn(1, 10)
            dao.upsert(e.copy(progress = progress, rating = rating, updatedAt = now).toEntity(dirty = true))
            added++
        }
        if (added > 0) {
            scheduler.syncNow()
            airingRefresh.refreshNow()
        }
        return added
    }

    /** "+1 episode": auto-completes at the last episode, and moves Plan/Dropped to Watching. */
    suspend fun incrementProgress(entry: ListEntry) {
        val total = entry.totalEpisodes
        if (total != null && entry.progress >= total) return
        val next = entry.progress + 1
        val status = when {
            total != null && next >= total -> ListStatus.COMPLETED
            entry.status == ListStatus.COMPLETED -> ListStatus.COMPLETED
            else -> ListStatus.WATCHING
        }
        update(entry, status = status, progress = next)
    }

    suspend fun remove(entry: ListEntry) {
        val existing = dao.get(entry.source.key, entry.externalId) ?: return
        dao.upsert(existing.copy(deleted = true, dirty = true, updatedAt = System.currentTimeMillis()))
        scheduler.syncNow()
        airingRefresh.refreshNow()
    }

    /** Returns true when a sync ran. The user is read inside the lock so a sync queued behind sign-out is a no-op. */
    suspend fun sync(): Boolean = syncLock.withLock {
        val uid = auth.currentUserId ?: return@withLock false
        ListSyncer(dao, remote).sync(uid)
        true
    }

    /** Waits for an in-flight sync, which could otherwise re-insert the pulled rows of the account being wiped. */
    suspend fun clearLocal() = syncLock.withLock { dao.clear() }
}
