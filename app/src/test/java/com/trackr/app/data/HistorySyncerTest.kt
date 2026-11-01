package com.trackr.app.data

import com.trackr.app.data.history.HistoryRemote
import com.trackr.app.data.history.HistorySyncer
import com.trackr.app.data.history.WatchEventDto
import com.trackr.app.data.local.WatchEventDao
import com.trackr.app.data.local.WatchEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FakeEventDao : WatchEventDao {
    val rows = MutableStateFlow<Map<String, WatchEventEntity>>(emptyMap())
    private fun put(e: WatchEventEntity) { rows.value = rows.value + (e.id to e) }
    override fun observeAll(): Flow<List<WatchEventEntity>> = rows.map { m -> m.values.filter { !it.deleted } }
    override fun observeFor(source: String, id: String) = rows.map { m -> m.values.filter { !it.deleted && it.source == source && it.externalId == id } }
    override suspend fun getFor(source: String, id: String) = rows.value.values.filter { !it.deleted && it.source == source && it.externalId == id }
    override suspend fun get(id: String) = rows.value[id]
    override suspend fun getUnsynced() = rows.value.values.filter { !it.synced && !it.deleted }
    override suspend fun getDeleted() = rows.value.values.filter { it.deleted }
    override suspend fun latestCreatedAt() = rows.value.values.mapNotNull { it.createdAt }.maxOrNull()
    override suspend fun upsert(entity: WatchEventEntity) = put(entity)
    override suspend fun insertIfAbsent(entities: List<WatchEventEntity>) = entities.filter { it.id !in rows.value }.forEach(::put)
    override suspend fun markSynced(ids: List<String>) = ids.mapNotNull { rows.value[it] }.filter { !it.deleted }.forEach { put(it.copy(synced = true)) }
    override suspend fun markPulled(id: String, createdAt: Long) { rows.value[id]?.takeIf { !it.deleted }?.let { put(it.copy(synced = true, createdAt = createdAt)) } }
    override suspend fun hardDelete(ids: List<String>) { rows.value = rows.value - ids.toSet() }
    override suspend fun clear() { rows.value = emptyMap() }
}

class FakeHistoryRemote : HistoryRemote {
    val data = mutableMapOf<String, WatchEventDto>()
    var clock = 1_000_000L
    val asked = mutableListOf<Long?>()
    override suspend fun fetchSince(userId: String, since: Long?): List<WatchEventDto> {
        asked += since
        return data.values.filter { since == null || Instant.parse(it.createdAt!!).toEpochMilli() > since }
            .sortedBy { it.createdAt }
    }
    override suspend fun insertAll(events: List<WatchEventDto>) {
        events.forEach { e -> data.putIfAbsent(e.id, e.copy(createdAt = Instant.ofEpochMilli(clock++).toString())) }
    }
    override suspend fun deleteAll(ids: List<String>) { ids.forEach { data.remove(it) } }
}

class HistorySyncerTest {
    private fun event(id: String, synced: Boolean = false, deleted: Boolean = false) =
        WatchEventEntity(id, "tmdb", "1", "tv", "Severance", "watched", 1, 3, 500L, synced = synced, deleted = deleted)

    @Test fun `pushes new events, sends removals, and pulls other devices' events`() = runTest {
        val dao = FakeEventDao()
        val remote = FakeHistoryRemote()
        // Another device logged "b"; this device logged "a" and removed "c", which had reached the server.
        remote.insertAll(listOf(WatchEventDto("b", "u", "tmdb", "1", "tv", "Severance", "watched", 4, 4, "2026-11-01T10:00:00Z")))
        remote.insertAll(listOf(WatchEventDto("c", "u", "tmdb", "1", "tv", "Severance", "watched", 5, 5, "2026-11-01T10:00:00Z")))
        dao.upsert(event("a"))
        dao.upsert(event("c", synced = true, deleted = true))

        HistorySyncer(dao, remote).sync("u")

        assertEquals(setOf("a", "b"), remote.data.keys)
        assertEquals(setOf("a", "b"), dao.rows.value.keys)
        assertTrue(dao.rows.value.values.all { it.synced && it.createdAt != null })
        assertNull("the first pull fetches everything", remote.asked.single())
    }

    @Test fun `later pulls ask only for what's new, with an overlap`() = runTest {
        val dao = FakeEventDao()
        val remote = FakeHistoryRemote()
        dao.upsert(event("a"))
        HistorySyncer(dao, remote).sync("u")
        val newest = dao.latestCreatedAt()!!
        HistorySyncer(dao, remote).sync("u")
        assertEquals(newest - HistorySyncer.OVERLAP_MS, remote.asked.last())
        assertEquals(1, dao.rows.value.size)
    }

    @Test fun `a removed event is not brought back by a pull`() = runTest {
        val dao = FakeEventDao()
        val remote = FakeHistoryRemote()
        dao.upsert(event("a"))
        HistorySyncer(dao, remote).sync("u")
        dao.upsert(dao.get("a")!!.copy(deleted = true))
        HistorySyncer(dao, remote).sync("u")
        assertTrue(remote.data.isEmpty())
        assertTrue(dao.rows.value.isEmpty())
    }
}
