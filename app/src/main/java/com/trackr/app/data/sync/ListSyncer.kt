package com.trackr.app.data.sync

import com.trackr.app.data.local.ListEntryDao
import com.trackr.app.data.local.ListEntryEntity
import com.trackr.app.data.mapper.ListEntryMapper.toDto
import com.trackr.app.data.mapper.ListEntryMapper.toEntity
import com.trackr.app.data.remote.supabase.ListEntryDto
import com.trackr.app.data.mapper.ListEntryMapper.parseInstant

/**
 * Every row of a paged read: asks for [pageSize] rows at a time until a page comes back empty. The server caps one
 * response (Supabase's default is 1000 rows), and the syncer drops clean local rows that a fetch doesn't return, so a
 * short read would remove titles from the device.
 */
suspend fun <T> fetchAllPages(pageSize: Int = 1000, fetch: suspend (from: Long, to: Long) -> List<T>): List<T> {
    val rows = mutableListOf<T>()
    while (true) {
        val page = fetch(rows.size.toLong(), rows.size + pageSize - 1L)
        if (page.isEmpty()) return rows
        rows += page
    }
}

/** Remote operations the syncer needs (kept tiny so the merge logic is unit-testable). */
interface ListRemote {
    suspend fun fetchAll(userId: String): List<ListEntryDto>
    /** Writes all of [dtos] in as few requests as possible. */
    suspend fun upsertAll(dtos: List<ListEntryDto>)
    /** Deletes the user's rows with these (source, externalId) keys. */
    suspend fun deleteAll(userId: String, keys: List<Pair<String, String>>)
}

/**
 * Two-way sync, last-write-wins on updatedAt.
 *  - local dirty row newer (or equal) than remote  -> push
 *  - remote newer                                    -> remote overwrites local
 *  - local tombstone newer than remote               -> delete remote, drop local
 *  - clean local row missing remotely                -> deleted elsewhere, drop locally
 * Pushes and deletes go out in one batch each. Local writes use the row's updatedAt as an expected version, so an edit
 * made mid-sync survives and stays dirty for the next run.
 */
class ListSyncer(private val dao: ListEntryDao, private val remote: ListRemote) {
    suspend fun sync(userId: String) {
        val remoteRows = remote.fetchAll(userId).associateBy { it.source to it.externalId }
        val local = dao.getAllRaw().associateBy { it.source to it.externalId }

        val pushes = mutableListOf<ListEntryEntity>()
        val remoteDeletes = mutableListOf<ListEntryEntity>()
        for ((key, l) in local) {
            val r = remoteRows[key]
            val remoteTime = r?.let { parseInstant(it.updatedAt) }
            when {
                l.deleted -> when {
                    r == null -> dao.hardDeleteIfUnchanged(l.source, l.externalId, l.updatedAt)
                    l.updatedAt >= remoteTime!! -> remoteDeletes += l
                    else -> dao.replaceIfUnchanged(r.toEntity(), l.updatedAt) // someone edited after our delete
                }
                l.dirty -> if (r == null || l.updatedAt >= remoteTime!!) pushes += l
                    else dao.replaceIfUnchanged(r.toEntity(), l.updatedAt)
                r == null -> dao.hardDeleteIfUnchanged(l.source, l.externalId, l.updatedAt)
                remoteTime!! > l.updatedAt -> dao.replaceIfUnchanged(r.toEntity(), l.updatedAt)
            }
        }
        if (remoteDeletes.isNotEmpty()) {
            remote.deleteAll(userId, remoteDeletes.map { it.source to it.externalId })
            remoteDeletes.forEach { dao.hardDeleteIfUnchanged(it.source, it.externalId, it.updatedAt) }
        }
        if (pushes.isNotEmpty()) {
            remote.upsertAll(pushes.map { it.toDto(userId) })
            pushes.forEach { dao.markCleanIfUnchanged(it.source, it.externalId, it.updatedAt) }
        }
        val fresh: List<ListEntryEntity> = remoteRows.filterKeys { it !in local }.values.map { it.toEntity() }
        if (fresh.isNotEmpty()) dao.insertIfAbsent(fresh)
    }
}
