package com.trackr.app.data

import com.trackr.app.data.airing.AiringRefreshScheduler
import com.trackr.app.data.history.SupabaseHistoryRemote
import com.trackr.app.data.history.WatchHistoryRepository
import com.trackr.app.data.repository.AuthRepository
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.SupabaseListRemote
import com.trackr.app.data.sync.SyncScheduler
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The list records its watch history as it changes. */
class ListHistoryTest {
    private val dao = FakeDao()
    private val events = FakeEventDao()
    private val history = WatchHistoryRepository(events, mockk<SupabaseHistoryRemote>(relaxed = true))
    private val repo = ListRepository(
        dao, mockk<SupabaseListRemote>(relaxed = true), mockk<AuthRepository>(relaxed = true),
        mockk<SyncScheduler>(relaxed = true), mockk<AiringRefreshScheduler>(relaxed = true), history,
    )
    private val show = MediaItem(MediaSource.TMDB, "1", MediaType.TV, "Show", null, totalEpisodes = 3)
    private suspend fun entry() = repo.entry(show).first()!!
    private suspend fun lines() = history.of("tmdb", "1").first().sortedBy { it.watchedAt }.map { it.kind.key to (it.fromEpisode to it.toEpisode) }

    @Test fun `episodes, a rewatch and a correction land in the history`() = runTest {
        repo.save(show, ListStatus.WATCHING, null, 1)
        repo.incrementProgress(entry())
        repo.incrementProgress(entry()) // completes at 3
        assertEquals(ListStatus.COMPLETED, entry().status)
        assertEquals(listOf("watched" to (1 to 1), "watched" to (2 to 2), "watched" to (3 to 3)), lines())

        repo.startRewatch(entry())
        assertEquals(1, entry().rewatchCount)
        assertEquals(0, entry().progress)
        assertEquals("rewatch", lines().last().first)

        repo.incrementProgress(entry())
        repo.update(entry(), progress = 0) // oops: undo it
        assertEquals("rewatch", lines().last().first)
        assertEquals(4, lines().size)
    }

    @Test fun `saving from the sheet keeps the alert opt-in and the rewatch count`() = runTest {
        repo.save(show, ListStatus.PLAN_TO_WATCH, null, 0)
        repo.setNotify(entry(), true)
        repo.setRewatchCount(entry(), 2)
        repo.save(show, ListStatus.PLAN_TO_WATCH, 8, 0)
        assertTrue(entry().notify)
        assertEquals(2, entry().rewatchCount)
        assertTrue("setting the count logs nothing", lines().isEmpty())
    }

    @Test fun `removing a title clears its history`() = runTest {
        repo.save(show, ListStatus.WATCHING, null, 2)
        repo.remove(entry())
        assertTrue(lines().isEmpty())
    }
}
