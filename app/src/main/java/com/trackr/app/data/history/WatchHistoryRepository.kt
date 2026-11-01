package com.trackr.app.data.history

import com.trackr.app.data.local.WatchEventDao
import com.trackr.app.data.local.WatchEventEntity
import com.trackr.app.data.mapper.ListEntryMapper.parseInstant
import com.trackr.app.data.sync.fetchAllPages
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.WatchEvent
import com.trackr.app.domain.util.WatchHistory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WatchEventDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val source: String,
    @SerialName("external_id") val externalId: String,
    @SerialName("media_type") val mediaType: String,
    val title: String,
    val kind: String,
    @SerialName("from_episode") val fromEpisode: Int? = null,
    @SerialName("to_episode") val toEpisode: Int? = null,
    @SerialName("watched_at") val watchedAt: String,
    /** Set by the server; left out of inserts. */
    @SerialName("created_at") val createdAt: String? = null,
)

/** Server side of the watch history (kept small so [HistorySyncer] is unit-testable). */
interface HistoryRemote {
    /** The user's events created after [since] (epoch millis; null = all), oldest first. */
    suspend fun fetchSince(userId: String, since: Long?): List<WatchEventDto>
    /** Inserts events; ids already there are left alone. */
    suspend fun insertAll(events: List<WatchEventDto>)
    suspend fun deleteAll(ids: List<String>)
}

@Singleton
class SupabaseHistoryRemote @Inject constructor(private val supabase: SupabaseClient) : HistoryRemote {
    override suspend fun fetchSince(userId: String, since: Long?): List<WatchEventDto> = fetchAllPages { from, to ->
        supabase.from("watch_events").select {
            filter {
                eq("user_id", userId)
                if (since != null) gt("created_at", Instant.ofEpochMilli(since).toString())
            }
            order("created_at", Order.ASCENDING)
            order("id", Order.ASCENDING)
            range(from, to)
        }.decodeList()
    }

    override suspend fun insertAll(events: List<WatchEventDto>) {
        events.chunked(BATCH).forEach { chunk ->
            supabase.from("watch_events").upsert(chunk) { onConflict = "id"; ignoreDuplicates = true }
        }
    }

    override suspend fun deleteAll(ids: List<String>) {
        ids.chunked(BATCH).forEach { chunk -> supabase.from("watch_events").delete { filter { isIn("id", chunk) } } }
    }

    private companion object {
        const val BATCH = 200
    }
}

/**
 * Two-way sync of the watch history. Events never change once made (a correction removes or replaces them), so it
 * pushes removals and new events, then pulls what other devices added since the newest event it has pulled, with an
 * overlap for rows committed late. After sign-in the table is empty, so the first pull fetches everything.
 */
class HistorySyncer(private val dao: WatchEventDao, private val remote: HistoryRemote) {
    suspend fun sync(userId: String) {
        val removed = dao.getDeleted()
        if (removed.isNotEmpty()) {
            remote.deleteAll(removed.map { it.id })
            dao.hardDelete(removed.map { it.id })
        }
        val fresh = dao.getUnsynced()
        if (fresh.isNotEmpty()) {
            remote.insertAll(fresh.map { it.toDto(userId) })
            dao.markSynced(fresh.map { it.id })
        }
        val since = dao.latestCreatedAt()?.minus(OVERLAP_MS)
        val pulled = remote.fetchSince(userId, since)
        if (pulled.isEmpty()) return
        dao.insertIfAbsent(pulled.map { it.toEntity() })
        pulled.forEach { p -> p.createdAt?.let { dao.markPulled(p.id, parseInstant(it)) } }
    }

    companion object {
        const val OVERLAP_MS = 10 * 60 * 1000L
    }
}

fun WatchEventEntity.toDomain() = WatchEvent(
    id, MediaSource.fromKey(source), externalId, MediaType.fromKey(mediaType), title, WatchEvent.Kind.fromKey(kind),
    fromEpisode, toEpisode, watchedAt,
)

fun WatchEvent.toEntity(synced: Boolean = false) = WatchEventEntity(
    id, source.key, externalId, mediaType.key, title, kind.key, fromEpisode, toEpisode, watchedAt, synced = synced,
)

fun WatchEventEntity.toDto(userId: String) = WatchEventDto(
    id, userId, source, externalId, mediaType, title, kind, fromEpisode, toEpisode, Instant.ofEpochMilli(watchedAt).toString(),
)

fun WatchEventDto.toEntity() = WatchEventEntity(
    id, source, externalId, mediaType, title, kind, fromEpisode, toEpisode, parseInstant(watchedAt),
    synced = true, createdAt = createdAt?.let { parseInstant(it) },
)

/** The device's watch history: recorded by ListRepository on each change, synced alongside the list. */
@Singleton
class WatchHistoryRepository @Inject constructor(private val dao: WatchEventDao, private val remote: SupabaseHistoryRemote) {
    val all: Flow<List<WatchEvent>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun of(source: String, id: String): Flow<List<WatchEvent>> = dao.observeFor(source, id).map { rows -> rows.map { it.toDomain() } }

    /** Records what the change from [previous] to [next] watched, or takes back what a correction undid. */
    suspend fun onWrite(previous: ListEntry?, next: ListEntry, now: Long) {
        WatchHistory.onWrite(previous, next, now) { UUID.randomUUID().toString() }.forEach { dao.upsert(it.toEntity()) }
        if (previous == null) return
        val trim = WatchHistory.onCorrection(previous, next, dao.getFor(next.source.key, next.externalId).map { it.toDomain() })
        // A shortened event goes out as a new one: events are never edited on the server.
        trim.remove.forEach { remove(it.id) }
        trim.shorten.forEach { e -> remove(e.id); dao.upsert(e.copy(id = UUID.randomUUID().toString()).toEntity()) }
    }

    /** Marks a new watch-through of [entry]. */
    suspend fun startRewatch(entry: ListEntry, now: Long) {
        dao.upsert(
            WatchEvent(
                UUID.randomUUID().toString(), entry.source, entry.externalId, entry.mediaType, entry.title,
                WatchEvent.Kind.REWATCH, null, null, now,
            ).toEntity(),
        )
    }

    /** The title left the list: its history goes too. */
    suspend fun removeTitle(source: String, id: String) = dao.getFor(source, id).forEach { remove(it.id) }

    private suspend fun remove(id: String) {
        // A tombstone until the server delete is sent (even unsynced events: a push may have landed without being marked).
        val e = dao.get(id) ?: return
        dao.upsert(e.copy(deleted = true))
    }

    suspend fun sync(userId: String) = HistorySyncer(dao, remote).sync(userId)

    suspend fun clear() = dao.clear()
}
