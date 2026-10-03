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
    override suspend fun upsertAll(entities: List<ListEntryEntity>) = entities.forEach { upsert(it) }
    override suspend fun hardDelete(source: String, id: String) { rows.value = rows.value - (source to id) }
    override suspend fun clear() { rows.value = emptyMap() }
}

class FakeRemote(initial: List<ListEntryDto> = emptyList()) : ListRemote {
    val data = initial.associateBy { it.source to it.externalId }.toMutableMap()
    val deletes = mutableListOf<String>()
    override suspend fun fetchAll(userId: String) = data.values.toList()
    override suspend fun upsert(dto: ListEntryDto) { data[dto.source to dto.externalId] = dto }
    override suspend fun delete(userId: String, source: String, externalId: String) { data.remove(source to externalId); deletes += externalId }
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
}
