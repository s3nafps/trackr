package com.trackr.app.data

import com.trackr.app.data.airing.AiringScheduler
import com.trackr.app.data.local.AiringDao
import com.trackr.app.data.local.AiringEntity
import com.trackr.app.data.local.ListEntryEntity
import com.trackr.app.data.local.UserPrefs
import com.trackr.app.data.repository.AiringRepository
import com.trackr.app.data.repository.MediaRepository
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaDetail
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.util.AiringPolicy
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FakeAiringDao : AiringDao {
    val rows = MutableStateFlow<Map<Pair<String, String>, AiringEntity>>(emptyMap())
    override suspend fun getAll() = rows.value.values.toList()
    override fun observeAll(): Flow<List<AiringEntity>> = rows.map { it.values.sortedBy { r -> r.airAt } }
    override suspend fun get(source: String, id: String) = rows.value[source to id]
    override suspend fun upsert(e: AiringEntity) { rows.value = rows.value + ((e.source to e.externalId) to e) }
    override suspend fun delete(source: String, id: String) { rows.value = rows.value - (source to id) }
    override suspend fun markNotified(source: String, id: String) {
        rows.value[source to id]?.let { upsert(it.copy(notified = true)) }
    }
    override suspend fun clear() { rows.value = emptyMap() }
}

class AiringRepositoryTest {
    private val now = 1_000_000_000L
    private val listDao = FakeDao()
    private val airingDao = FakeAiringDao()
    private val media = mockk<MediaRepository>()
    private val prefs = mockk<UserPrefs>()
    private val scheduler = mockk<AiringScheduler>(relaxed = true)
    private val enabled = MutableStateFlow(true)
    private val repo = AiringRepository(listDao, airingDao, media, prefs, scheduler)

    init { coEvery { prefs.airingEnabled } returns enabled }

    private suspend fun track(
        id: String, status: ListStatus, source: MediaSource = MediaSource.ANILIST, type: MediaType = MediaType.ANIME,
        notify: Boolean = false, deleted: Boolean = false,
    ) = listDao.upsert(
        ListEntryEntity(source.key, id, type.key, "T$id", null, null, status.key, null, 0, null, 1L, false, deleted, notify),
    )

    private fun serve(
        id: String, episode: Int?, atSeconds: Long?, dateOnly: Boolean = false,
        source: MediaSource = MediaSource.ANILIST, type: MediaType = MediaType.ANIME,
    ) {
        val item = MediaItem(source, id, type, "T$id", null, airingEpisode = episode, airingAtEpoch = atSeconds, airingDateOnly = dateOnly)
        coEvery { media.detail(source, id, type, true) } returns MediaDetail(item = item)
    }

    @Test fun `watching title gets row`() = runTest {
        track("1", ListStatus.WATCHING); serve("1", 12, 2_000)
        repo.refresh(now)
        val r = airingDao.get("anilist", "1")!!
        assertEquals(12, r.episode); assertEquals(2_000_000L, r.airAt); assertFalse(r.notified)
    }

    @Test fun `plan without bell gets no row`() = runTest {
        track("1", ListStatus.PLAN_TO_WATCH); serve("1", 1, 2_000)
        repo.refresh(now)
        assertNull(airingDao.get("anilist", "1"))
    }

    @Test fun `plan with bell gets row`() = runTest {
        track("1", ListStatus.PLAN_TO_WATCH, notify = true); serve("1", 1, 2_000)
        repo.refresh(now)
        assertEquals(1, airingDao.get("anilist", "1")!!.episode)
    }

    @Test fun `completed title row deleted and alarm cancelled`() = runTest {
        val old = AiringEntity("anilist", "1", "anime", "T1", 3, 5_000, "TIME")
        airingDao.upsert(old)
        track("1", ListStatus.COMPLETED); serve("1", 4, 6_000)
        repo.refresh(now)
        assertNull(airingDao.get("anilist", "1"))
        val removed = slot<List<AiringEntity>>()
        verify { scheduler.apply(any(), capture(removed), any()) }
        assertEquals(listOf(old), removed.captured)
    }

    @Test fun `removed title row is deleted`() = runTest {
        airingDao.upsert(AiringEntity("anilist", "1", "anime", "T1", 3, 5_000, "TIME"))
        track("1", ListStatus.WATCHING, deleted = true); serve("1", 4, 6_000)
        repo.refresh(now)
        assertNull(airingDao.get("anilist", "1"))
    }

    @Test fun `same episode keeps notified flag`() = runTest {
        airingDao.upsert(AiringEntity("anilist", "1", "anime", "T1", 12, 2_000_000, "TIME", notified = true))
        track("1", ListStatus.WATCHING); serve("1", 12, 2_100) // time shifted, same episode
        repo.refresh(now)
        val r = airingDao.get("anilist", "1")!!
        assertTrue(r.notified); assertEquals(2_100_000L, r.airAt)
    }

    @Test fun `new episode resets notified`() = runTest {
        airingDao.upsert(AiringEntity("anilist", "1", "anime", "T1", 12, 2_000_000, "TIME", notified = true))
        track("1", ListStatus.WATCHING); serve("1", 13, 9_000)
        repo.refresh(now)
        val r = airingDao.get("anilist", "1")!!
        assertEquals(13, r.episode); assertFalse(r.notified)
    }

    @Test fun `no next airing deletes row`() = runTest {
        airingDao.upsert(AiringEntity("anilist", "1", "anime", "T1", 12, 2_000_000, "TIME"))
        track("1", ListStatus.WATCHING); serve("1", null, null)
        repo.refresh(now)
        assertNull(airingDao.get("anilist", "1"))
    }

    @Test fun `title with no airing and no row creates none`() = runTest {
        track("1", ListStatus.WATCHING); serve("1", null, null)
        repo.refresh(now)
        assertTrue(airingDao.getAll().isEmpty())
    }

    @Test fun `fetch failure keeps old row`() = runTest {
        val old = AiringEntity("anilist", "1", "anime", "T1", 12, 2_000_000, "TIME")
        airingDao.upsert(old)
        track("1", ListStatus.WATCHING)
        coEvery { media.detail(MediaSource.ANILIST, "1", MediaType.ANIME, true) } throws IOException("offline")
        repo.refresh(now)
        assertEquals(old, airingDao.get("anilist", "1"))
    }

    @Test fun `one failing title does not block others`() = runTest {
        track("1", ListStatus.WATCHING); track("2", ListStatus.WATCHING)
        coEvery { media.detail(MediaSource.ANILIST, "1", MediaType.ANIME, true) } throws IOException("offline")
        serve("2", 5, 3_000)
        repo.refresh(now)
        assertNull(airingDao.get("anilist", "1")); assertEquals(5, airingDao.get("anilist", "2")!!.episode)
    }

    @Test fun `master switch off removes everything`() = runTest {
        airingDao.upsert(AiringEntity("anilist", "1", "anime", "T1", 12, 2_000_000, "TIME"))
        track("1", ListStatus.WATCHING); serve("1", 12, 2_000)
        enabled.value = false
        repo.refresh(now)
        assertTrue(airingDao.getAll().isEmpty())
    }

    @Test fun `anime row precision is time and tv is date`() = runTest {
        track("1", ListStatus.WATCHING); serve("1", 2, 2_000)
        track("9", ListStatus.WATCHING, MediaSource.TMDB, MediaType.TV); serve("9", 4, 3_000, dateOnly = true, source = MediaSource.TMDB, type = MediaType.TV)
        repo.refresh(now)
        assertEquals("TIME", airingDao.get("anilist", "1")!!.precision)
        assertEquals("DATE", airingDao.get("tmdb", "9")!!.precision)
    }

    @Test fun `refresh hands upserted rows to scheduler`() = runTest {
        track("1", ListStatus.WATCHING); serve("1", 2, 2_000)
        repo.refresh(now)
        val up = slot<List<AiringEntity>>()
        verify { scheduler.apply(capture(up), any(), now) }
        assertEquals("1", up.captured.single().externalId)
    }

    @Test fun `clearAll cancels alarms and empties table`() = runTest {
        val r = AiringEntity("anilist", "1", "anime", "T1", 12, 2_000_000, "TIME")
        airingDao.upsert(r)
        repo.clearAll()
        assertTrue(airingDao.getAll().isEmpty())
        verify { scheduler.cancelAll(listOf(r)) }
    }

    @Test fun `eligibility policy`() {
        assertTrue(AiringPolicy.isEligible(ListStatus.WATCHING, false))
        assertFalse(AiringPolicy.isEligible(ListStatus.PLAN_TO_WATCH, false))
        assertTrue(AiringPolicy.isEligible(ListStatus.PLAN_TO_WATCH, true))
        assertFalse(AiringPolicy.isEligible(ListStatus.COMPLETED, true))
        assertFalse(AiringPolicy.isEligible(ListStatus.DROPPED, true))
    }
}
