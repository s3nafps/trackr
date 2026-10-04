package com.trackr.app.data.sync

import com.trackr.app.data.local.ListEntryDao
import com.trackr.app.data.local.ListEntryEntity
import com.trackr.app.data.mapper.ListEntryMapper.toDto
import com.trackr.app.data.mapper.ListEntryMapper.toEntity
import com.trackr.app.data.remote.supabase.ListEntryDto
import com.trackr.app.data.mapper.ListEntryMapper.parseInstant

/** Remote operations the syncer needs (kept tiny so the merge logic is unit-testable). */
interface ListRemote {
    suspend fun fetchAll(userId: String): List<ListEntryDto>
    suspend fun upsert(dto: ListEntryDto)
    suspend fun delete(userId: String, source: String, externalId: String)
}

/**
 * Two-way sync, last-write-wins on updatedAt.
 *  - local dirty row newer (or equal) than remote  -> push
 *  - remote newer                                    -> remote overwrites local
 *  - local tombstone newer than remote               -> delete remote, drop local
 *  - clean local row missing remotely                -> deleted elsewhere, drop locally
 * Local writes use the row's updatedAt as an expected version, so an edit made mid-sync survives and stays dirty
 * for the next run.
 */
class ListSyncer(private val dao: ListEntryDao, private val remote: ListRemote) {
    suspend fun sync(userId: String) {
        val remoteRows = remote.fetchAll(userId).associateBy { it.source to it.externalId }
        val local = dao.getAllRaw().associateBy { it.source to it.externalId }

        for ((key, l) in local) {
            val r = remoteRows[key]
            val remoteTime = r?.let { parseInstant(it.updatedAt) }
            when {
                l.deleted -> when {
                    r == null || l.updatedAt >= remoteTime!! -> {
                        if (r != null) remote.delete(userId, l.source, l.externalId)
                        dao.hardDeleteIfUnchanged(l.source, l.externalId, l.updatedAt)
                    }
                    else -> dao.replaceIfUnchanged(r.toEntity(), l.updatedAt) // someone edited after our delete
                }
                l.dirty -> if (r == null || l.updatedAt >= remoteTime!!) {
                    remote.upsert(l.toDto(userId))
                    dao.markCleanIfUnchanged(l.source, l.externalId, l.updatedAt)
                } else dao.replaceIfUnchanged(r.toEntity(), l.updatedAt)
                r == null -> dao.hardDeleteIfUnchanged(l.source, l.externalId, l.updatedAt)
                remoteTime!! > l.updatedAt -> dao.replaceIfUnchanged(r.toEntity(), l.updatedAt)
            }
        }
        val fresh: List<ListEntryEntity> = remoteRows.filterKeys { it !in local }.values.map { it.toEntity() }
        if (fresh.isNotEmpty()) dao.insertIfAbsent(fresh)
    }
}
