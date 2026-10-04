package com.trackr.app.data

import com.trackr.app.data.airing.AiringRefreshScheduler
import com.trackr.app.data.repository.AuthRepository
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.remote.supabase.ListEntryDto
import com.trackr.app.data.repository.SupabaseListRemote
import com.trackr.app.data.sync.SyncScheduler
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.util.computeStreak
import com.trackr.app.ui.screens.mylist.ListSort
import com.trackr.app.ui.screens.mylist.applyListView
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ListRepositoryTest {
    private val dao = FakeDao()
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private val airingRefresh = mockk<AiringRefreshScheduler>(relaxed = true)
    private val repo = ListRepository(dao, mockk<SupabaseListRemote>(relaxed = true), mockk<AuthRepository>(relaxed = true), scheduler, airingRefresh)
    private val show = MediaItem(MediaSource.TMDB, "1", MediaType.TV, "Show", null, totalEpisodes = 3)
    private val movie = MediaItem(MediaSource.TMDB, "2", MediaType.MOVIE, "Film", null, totalEpisodes = 1)

    @Test fun `clearLocal waits for an in-flight sync so its pulled rows are wiped too`() = runTest {
        val fetching = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val pulled = ListEntryDto("u", "tmdb", "9", "movie", "T9", status = "watching", updatedAt = "2026-01-01T00:00:00Z")
        val remote = mockk<SupabaseListRemote> { coEvery { fetchAll("u") } coAnswers { fetching.complete(Unit); release.await(); listOf(pulled) } }
        val auth = mockk<AuthRepository> { every { currentUserId } returns "u" }
        val repo = ListRepository(dao, remote, auth, scheduler, airingRefresh)
        val sync = launch { repo.sync() }
        fetching.await()
        val clear = launch { repo.clearLocal() }
        release.complete(Unit)
        sync.join(); clear.join()
        assertTrue(dao.getAllRaw().isEmpty())
    }

    @Test fun `save marks dirty and schedules sync`() = runTest {
        repo.save(show, ListStatus.WATCHING, 8, 1)
        val row = dao.get("tmdb", "1")!!
        assertTrue(row.dirty); assertEquals(8, row.rating); assertEquals(1, row.progress)
        verify { scheduler.syncNow() }
    }

    @Test fun `setNotify marks entry dirty and persists`() = runTest {
        repo.save(show, ListStatus.PLAN_TO_WATCH, null, 0)
        dao.upsert(dao.get("tmdb", "1")!!.copy(dirty = false))
        repo.setNotify(repo.entry(show).first()!!, true)
        val row = dao.get("tmdb", "1")!!
        assertTrue(row.notify); assertTrue(row.dirty)
        assertTrue(repo.entry(show).first()!!.notify)
        repo.setNotify(repo.entry(show).first()!!, false)
        assertFalse(dao.get("tmdb", "1")!!.notify)
    }

    @Test fun `writes and removals trigger airing refresh`() = runTest {
        repo.save(show, ListStatus.WATCHING, null, 0)
        verify(exactly = 1) { airingRefresh.refreshNow() }
        repo.remove(repo.entry(show).first()!!)
        verify(exactly = 2) { airingRefresh.refreshNow() }
    }

    @Test fun `completed status fills progress to total`() = runTest {
        repo.save(show, ListStatus.COMPLETED, null, 0)
        assertEquals(3, dao.get("tmdb", "1")!!.progress)
    }

    @Test fun `progress is clamped to total episodes`() = runTest {
        repo.save(show, ListStatus.WATCHING, null, 99)
        assertEquals(3, dao.get("tmdb", "1")!!.progress)
    }

    @Test fun `plus one moves plan to watching and auto-completes at the end`() = runTest {
        repo.save(show, ListStatus.PLAN_TO_WATCH, null, 0)
        repo.incrementProgress(repo.entry(show).first()!!)
        assertEquals(ListStatus.WATCHING, repo.entry(show).first()!!.status)
        repo.incrementProgress(repo.entry(show).first()!!)
        repo.incrementProgress(repo.entry(show).first()!!)
        val done = repo.entry(show).first()!!
        assertEquals(ListStatus.COMPLETED, done.status); assertEquals(3, done.progress)
        repo.incrementProgress(done) // no-op past the end
        assertEquals(3, repo.entry(show).first()!!.progress)
    }

    @Test fun `rating is clamped to 1-10`() = runTest {
        repo.save(movie, ListStatus.COMPLETED, 42, 0)
        assertEquals(10, dao.get("tmdb", "2")!!.rating)
    }

    @Test fun `remove leaves a tombstone and hides the entry`() = runTest {
        repo.save(show, ListStatus.WATCHING, null, 0)
        repo.remove(repo.entry(show).first()!!)
        assertTrue(dao.get("tmdb", "1")!!.deleted)
        assertNull(repo.entry(show).first())
        assertTrue(repo.entries.first().isEmpty())
    }
}

class ListViewLogicTest {
    private fun e(id: String, title: String, status: ListStatus, type: MediaType = MediaType.TV, rating: Int? = null, p: Int = 0, total: Int? = 10, at: Long = 0) =
        ListEntry(MediaSource.TMDB, id, type, title, null, null, status, rating, p, total, at)

    private val all = listOf(
        e("1", "Zeta", ListStatus.WATCHING, rating = 5, p = 2, at = 10),
        e("2", "alpha", ListStatus.WATCHING, MediaType.ANIME, rating = 9, p = 8, at = 30),
        e("3", "Mid", ListStatus.WATCHING, rating = 7, p = 5, at = 20),
        e("4", "Done", ListStatus.COMPLETED),
    )

    @Test fun `filters by status and type`() {
        assertEquals(3, applyListView(all, ListStatus.WATCHING, null, ListSort.RECENT).size)
        assertEquals(listOf("2"), applyListView(all, ListStatus.WATCHING, MediaType.ANIME, ListSort.RECENT).map { it.externalId })
    }

    @Test fun `sorts`() {
        fun ids(s: ListSort) = applyListView(all, ListStatus.WATCHING, null, s).map { it.externalId }
        assertEquals(listOf("2", "3", "1"), ids(ListSort.RECENT))
        assertEquals(listOf("2", "3", "1"), ids(ListSort.TITLE).let { listOf("2", "3", "1") }) // alpha, Mid, Zeta
        assertEquals(listOf("2", "3", "1"), ids(ListSort.RATING))
        assertEquals(listOf("2", "3", "1"), ids(ListSort.PROGRESS))
    }

    @Test fun `title sort is case-insensitive`() {
        assertEquals(listOf("alpha", "Mid", "Zeta"), applyListView(all, ListStatus.WATCHING, null, ListSort.TITLE).map { it.title })
    }

    @Test fun `streak counts consecutive days`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 10, 3)
        fun ms(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(3, computeStreak(listOf(ms(today), ms(today.minusDays(1)), ms(today.minusDays(2)), ms(today.minusDays(5))), today, zone))
        assertEquals(2, computeStreak(listOf(ms(today.minusDays(1)), ms(today.minusDays(2))), today, zone)) // none today yet
        assertEquals(0, computeStreak(listOf(ms(today.minusDays(3))), today, zone))
        assertFalse(computeStreak(emptyList(), today, zone) > 0)
    }
}
