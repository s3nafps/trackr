package com.trackr.app.data

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
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
import io.mockk.mockk
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
    private val dao = FakeDao()
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val repo = ListRepository(dao, mockk<SupabaseListRemote>(relaxed = true), auth, mockk<SyncScheduler>(relaxed = true), mockk(relaxed = true))

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun item(id: String, type: MediaType, title: String) = MediaItem(MediaSource.TMDB, id, type, title, null, totalEpisodes = 10)

    @Test fun `opens on the requested tab and reflects list changes`() = runTest {
        repo.save(item("1", MediaType.TV, "Alpha"), ListStatus.WATCHING, null, 2)
        repo.save(item("2", MediaType.MOVIE, "Beta"), ListStatus.COMPLETED, 9, 0)
        val vm = MyListViewModel(SavedStateHandle(mapOf("status" to "completed")), repo)
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
        val vm = MyListViewModel(SavedStateHandle(), repo)
        vm.plusOne(repo.entry(MediaSource.TMDB.key, "1").first()!!)
        assertEquals(3, dao.get("tmdb", "1")!!.progress)
    }

    @Test fun `sync failure is reported but does not crash`() = runTest {
        coEvery { auth.currentUserId } returns "u"
        val remote = mockk<SupabaseListRemote>()
        coEvery { remote.fetchAll(any()) } throws java.io.IOException("offline")
        val r = ListRepository(dao, remote, auth, mockk(relaxed = true), mockk(relaxed = true))
        val vm = MyListViewModel(SavedStateHandle(), r)
        vm.refresh(silent = false)
        vm.state.test {
            var s = awaitItem()
            while (s.message == null) s = awaitItem()
            assertEquals(false, s.refreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
