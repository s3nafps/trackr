package com.trackr.app.data

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.trackr.app.data.local.AiringEntity
import com.trackr.app.data.repository.AiringRepository
import com.trackr.app.data.repository.AuthRepository
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.SupabaseListRemote
import com.trackr.app.data.sync.SyncScheduler
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.ui.screens.mylist.MyListViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import com.trackr.app.data.meta.TitleMetaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyListViewModelTest {
    private val meta = mockk<TitleMetaRepository> { every { all } returns flowOf(emptyMap()) }
    private val dao = FakeDao()
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val repo = ListRepository(dao, mockk<SupabaseListRemote>(relaxed = true), auth, mockk<SyncScheduler>(relaxed = true), mockk(relaxed = true))

    private val rows = kotlinx.coroutines.flow.MutableStateFlow<List<AiringEntity>>(emptyList())
    private fun airing() = mockk<AiringRepository>(relaxed = true).also { every { it.upcoming } returns rows }
    private fun row(id: String, inMillis: Long) =
        AiringEntity("tmdb", id, "tv", "T$id", 3, System.currentTimeMillis() + inMillis, "TIME")

    @Test fun `watching_card_gets_airs_in_label`() = runTest {
        repo.save(item("1", MediaType.TV, "Alpha"), ListStatus.WATCHING, null, 2)
        rows.value = listOf(row("1", 2 * 86_400_000L + 3_600_000L))
        val vm = MyListViewModel(SavedStateHandle(), repo, airing(), meta)
        vm.state.test {
            var s = awaitItem()
            while (s.airsIn.isEmpty()) s = awaitItem()
            assertEquals("Airs in 2d", s.airsIn["tmdb:1"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `plan_card_has_no_label_without_row`() = runTest {
        repo.save(item("1", MediaType.TV, "Alpha"), ListStatus.PLAN_TO_WATCH, null, 0)
        val vm = MyListViewModel(SavedStateHandle(mapOf("status" to "plan_to_watch")), repo, airing(), meta)
        vm.state.test {
            var s = awaitItem()
            while (s.items.isEmpty()) s = awaitItem()
            assertEquals(emptyMap<String, String>(), s.airsIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `upcoming_sort_orders_soonest_first_and_none_last`() = runTest {
        listOf("1", "2", "3").forEach { repo.save(item(it, MediaType.TV, "T$it"), ListStatus.WATCHING, null, 0) }
        rows.value = listOf(row("1", 5 * 3_600_000L), row("3", 1 * 3_600_000L))
        val vm = MyListViewModel(SavedStateHandle(mapOf("s" to "UPCOMING")), repo, airing(), meta)
        vm.state.test {
            var s = awaitItem()
            while (s.airsIn.size < 2) s = awaitItem()
            assertEquals(listOf("3", "1", "2"), s.items.map { it.externalId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun item(id: String, type: MediaType, title: String) = MediaItem(MediaSource.TMDB, id, type, title, null, totalEpisodes = 10)

    @Test fun `opens on the requested tab and reflects list changes`() = runTest {
        repo.save(item("1", MediaType.TV, "Alpha"), ListStatus.WATCHING, null, 2)
        repo.save(item("2", MediaType.MOVIE, "Beta"), ListStatus.COMPLETED, 9, 0)
        val vm = MyListViewModel(SavedStateHandle(mapOf("status" to "completed")), repo, airing(), meta)
        vm.state.test {
            var s = awaitItem()
            while (s.total < 2) s = awaitItem()
            assertEquals(ListStatus.COMPLETED, s.status)
            assertEquals(listOf("Beta"), s.items.map { it.title })
            assertEquals(1, s.statusCounts[ListStatus.WATCHING])
            vm.setStatus(ListStatus.WATCHING)
            s = awaitItem()
            assertEquals(listOf("Alpha"), s.items.map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `plus one updates progress through the repository`() = runTest {
        repo.save(item("1", MediaType.TV, "Alpha"), ListStatus.WATCHING, null, 2)
        val vm = MyListViewModel(SavedStateHandle(), repo, airing(), meta)
        vm.plusOne(repo.entry(MediaSource.TMDB.key, "1").first()!!)
        assertEquals(3, dao.get("tmdb", "1")!!.progress)
    }

    @Test fun `sync failure is reported but does not crash`() = runTest {
        coEvery { auth.currentUserId } returns "u"
        val remote = mockk<SupabaseListRemote>()
        coEvery { remote.fetchAll(any()) } throws java.io.IOException("offline")
        val r = ListRepository(dao, remote, auth, mockk(relaxed = true), mockk(relaxed = true))
        val vm = MyListViewModel(SavedStateHandle(), r, airing(), meta)
        vm.refresh(silent = false)
        vm.state.test {
            var s = awaitItem()
            while (s.message == null) s = awaitItem()
            assertEquals(false, s.refreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
