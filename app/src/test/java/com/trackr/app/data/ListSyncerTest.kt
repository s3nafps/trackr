package com.trackr.app.data

import com.trackr.app.data.local.ListEntryDao
import com.trackr.app.data.local.ListEntryEntity
import com.trackr.app.data.remote.supabase.ListEntryDto
import com.trackr.app.data.sync.ListRemote
import com.trackr.app.data.sync.ListSyncer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FakeDao : ListEntryDao {
    val rows = MutableStateFlow<Map<Pair<String, String>, ListEntryEntity>>(emptyMap())
    override fun observeAll(): Flow<List<ListEntryEntity>> = rows.map { it.values.filter { e -> !e.deleted } }
    override fun observe(source: String, id: String) = rows.map { it[source to id]?.takeIf { e -> !e.deleted } }
    override suspend fun get(source: String, id: String) = rows.value[source to id]
    override suspend fun getAllRaw() = rows.value.values.toList()
    override suspend fun getDirty() = rows.value.values.filter { it.dirty }
    override suspend fun upsert(entity: ListEntryEntity) { rows.value = rows.value + ((entity.source to entity.externalId) to entity) }
    override suspend fun clear() { rows.value = emptyMap() }
    override suspend fun markCleanIfUnchanged(source: String, id: String, updatedAt: Long) {
        get(source, id)?.takeIf { it.updatedAt == updatedAt }?.let { upsert(it.copy(dirty = false)) }
    }
    override suspend fun hardDeleteIfUnchanged(source: String, id: String, updatedAt: Long) {
        if (get(source, id)?.updatedAt == updatedAt) rows.value = rows.value - (source to id)
    }
    override suspend fun insertIfAbsent(entities: List<ListEntryEntity>) =
        entities.filter { get(it.source, it.externalId) == null }.forEach { upsert(it) }
}

class FakeRemote(initial: List<ListEntryDto> = emptyList()) : ListRemote {
    val data = initial.associateBy { it.source to it.externalId }.toMutableMap()
    val deletes = mutableListOf<String>()
    /** Runs while the request is "in flight", to simulate the user editing during a sync. */
    var duringUpsert: suspend () -> Unit = {}
    var duringDelete: suspend () -> Unit = {}
    override suspend fun fetchAll(userId: String) = data.values.toList()
    override suspend fun upsert(dto: ListEntryDto) { data[dto.source to dto.externalId] = dto; duringUpsert() }
    override suspend fun delete(userId: String, source: String, externalId: String) {
        data.remove(source to externalId); deletes += externalId; duringDelete()
    }
}

private fun entity(id: String, status: String = "watching", at: Long, dirty: Boolean = false, deleted: Boolean = false, progress: Int = 0, notify: Boolean = false) =
    ListEntryEntity("tmdb", id, "movie", "T$id", null, null, status, null, progress, null, at, dirty, deleted, notify)

private fun dto(id: String, status: String = "watching", at: Long, progress: Int = 0, notify: Boolean = false) =
    ListEntryDto("u", "tmdb", id, "movie", "T$id", null, null, status, null, progress, null, Instant.ofEpochMilli(at).toString(), notify)

class ListSyncerTest {
    @Test fun `syncer pushes and pulls notify`() = runTest {
        val dao = FakeDao().also {
            it.upsert(entity("1", at = 2000, dirty = true, notify = true))
            it.upsert(entity("2", at = 1000))
        }
        val remote = FakeRemote(listOf(dto("2", at = 5000, notify = true)))
        ListSyncer(dao, remote).sync("u")
        assertTrue(remote.data["tmdb" to "1"]!!.notify)
        assertTrue(dao.get("tmdb", "2")!!.notify)
    }

    @Test fun `dirty local newer than remote is pushed and marked clean`() = runTest {
        val dao = FakeDao().also { it.upsert(entity("1", "completed", at = 2000, dirty = true)) }
        val remote = FakeRemote(listOf(dto("1", "watching", at = 1000)))
        ListSyncer(dao, remote).sync("u")
        assertEquals("completed", remote.data["tmdb" to "1"]!!.status)
        assertFalse(dao.get("tmdb", "1")!!.dirty)
    }

    @Test fun `remote newer than dirty local overwrites local (last write wins)`() = runTest {
        val dao = FakeDao().also { it.upsert(entity("1", "completed", at = 1000, dirty = true)) }
        val remote = FakeRemote(listOf(dto("1", "dropped", at = 5000)))
        ListSyncer(dao, remote).sync("u")
        assertEquals("dropped", dao.get("tmdb", "1")!!.status)
        assertEquals("dropped", remote.data["tmdb" to "1"]!!.status)
    }

    @Test fun `new remote rows are pulled`() = runTest {
        val dao = FakeDao()
        ListSyncer(dao, FakeRemote(listOf(dto("9", at = 100)))).sync("u")
        assertNotNull(dao.get("tmdb", "9"))
        assertFalse(dao.get("tmdb", "9")!!.dirty)
    }

    @Test fun `local tombstone newer than remote deletes remotely and locally`() = runTest {
        val dao = FakeDao().also { it.upsert(entity("1", at = 3000, dirty = true, deleted = true)) }
        val remote = FakeRemote(listOf(dto("1", at = 1000)))
        ListSyncer(dao, remote).sync("u")
        assertEquals(listOf("1"), remote.deletes)
        assertNull(dao.get("tmdb", "1"))
    }

    @Test fun `remote edit after local delete resurrects the entry`() = runTest {
        val dao = FakeDao().also { it.upsert(entity("1", at = 1000, dirty = true, deleted = true)) }
        val remote = FakeRemote(listOf(dto("1", at = 9000, progress = 4)))
        ListSyncer(dao, remote).sync("u")
        val e = dao.get("tmdb", "1")!!
        assertFalse(e.deleted); assertEquals(4, e.progress)
        assertTrue(remote.deletes.isEmpty())
    }

    @Test fun `clean local row missing on remote was deleted elsewhere`() = runTest {
        val dao = FakeDao().also { it.upsert(entity("1", at = 1000)) }
        ListSyncer(dao, FakeRemote()).sync("u")
        assertNull(dao.get("tmdb", "1"))
    }

    @Test fun `dirty local row missing remotely is pushed`() = runTest {
        val dao = FakeDao().also { it.upsert(entity("1", at = 1000, dirty = true)) }
        val remote = FakeRemote()
        ListSyncer(dao, remote).sync("u")
        assertNotNull(remote.data["tmdb" to "1"])
    }

    @Test fun `edit made while its push is in flight is kept and pushed next sync`() = runTest {
        val dao = FakeDao().also { it.upsert(entity("1", progress = 3, at = 2000, dirty = true)) }
        val remote = FakeRemote()
        remote.duringUpsert = { dao.upsert(entity("1", progress = 4, at = 3000, dirty = true)) }
        ListSyncer(dao, remote).sync("u")
        val e = dao.get("tmdb", "1")!!
        assertEquals(4, e.progress); assertTrue(e.dirty)

        remote.duringUpsert = {}
        ListSyncer(dao, remote).sync("u")
        assertEquals(4, remote.data["tmdb" to "1"]!!.progress)
        assertFalse(dao.get("tmdb", "1")!!.dirty)
    }

    @Test fun `entry re-added while its delete is in flight survives`() = runTest {
        val dao = FakeDao().also { it.upsert(entity("1", at = 3000, dirty = true, deleted = true)) }
        val remote = FakeRemote(listOf(dto("1", at = 1000)))
        remote.duringDelete = { dao.upsert(entity("1", "completed", at = 4000, dirty = true)) }
        ListSyncer(dao, remote).sync("u")
        val e = dao.get("tmdb", "1")!!
        assertFalse(e.deleted); assertTrue(e.dirty); assertEquals("completed", e.status)
    }

    // The edits below land while another row ("0") is being pushed, i.e. after the syncer took its local snapshot.

    @Test fun `newer remote row does not overwrite an edit made mid-sync`() = runTest {
        val dao = FakeDao().also {
            it.upsert(entity("0", at = 1000, dirty = true))
            it.upsert(entity("1", "watching", at = 1000))
        }
        val remote = FakeRemote(listOf(dto("1", "dropped", at = 5000)))
        remote.duringUpsert = { dao.upsert(entity("1", "completed", at = 9000, dirty = true)) }
        ListSyncer(dao, remote).sync("u")
        assertEquals("completed", dao.get("tmdb", "1")!!.status)
        assertTrue(dao.get("tmdb", "1")!!.dirty)
    }

    @Test fun `clean row edited mid-sync is not dropped as deleted elsewhere`() = runTest {
        val dao = FakeDao().also {
            it.upsert(entity("0", at = 1000, dirty = true))
            it.upsert(entity("1", at = 1000))
        }
        val remote = FakeRemote()
        remote.duringUpsert = { dao.upsert(entity("1", progress = 2, at = 2000, dirty = true)) }
        ListSyncer(dao, remote).sync("u")
        assertEquals(2, dao.get("tmdb", "1")!!.progress)
    }

    @Test fun `entry added locally mid-sync is not replaced by the pulled remote row`() = runTest {
        val dao = FakeDao().also { it.upsert(entity("0", at = 1000, dirty = true)) }
        val remote = FakeRemote(listOf(dto("1", "dropped", at = 1000)))
        remote.duringUpsert = { dao.upsert(entity("1", "watching", at = 2000, dirty = true)) }
        ListSyncer(dao, remote).sync("u")
        assertEquals("watching", dao.get("tmdb", "1")!!.status)
        assertTrue(dao.get("tmdb", "1")!!.dirty)
    }
}
