package com.trackr.app.data

import androidx.lifecycle.SavedStateHandle
import com.trackr.app.data.realtime.LiveFollower
import com.trackr.app.data.realtime.LiveUpdates
import com.trackr.app.data.repository.FriendsRepository
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.SharedListsRepository
import com.trackr.app.data.repository.SocialRepository
import com.trackr.app.domain.model.ActivityEntry
import com.trackr.app.domain.model.Comment
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.Profile
import com.trackr.app.domain.model.SharedList
import com.trackr.app.domain.model.SharedListItem
import com.trackr.app.ui.screens.friends.FriendsViewModel
import com.trackr.app.ui.screens.social.SharedListViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Friends, comments and shared lists reload by themselves when Supabase Realtime reports a change. */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveUpdatesTest {
    private val main = UnconfinedTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(main)
    @After fun tearDown() = Dispatchers.resetMain()

    private val events = MutableSharedFlow<Unit>()
    private val live = mockk<LiveUpdates> { every { changes(*anyVararg()) } returns events }

    private fun entry(id: String, title: String) =
        ActivityEntry(id, "ua", "ann", null, MediaSource.TMDB, id, MediaType.MOVIE, title, null, ListStatus.WATCHING, null, 0, null, 0)

    private val friends = mockk<FriendsRepository>(relaxed = true) { coEvery { activity(any()) } returns listOf(entry("e1", "Heat")) }
    private val lists = mockk<ListRepository>(relaxed = true) { every { entries } returns MutableStateFlow(emptyList()) }
    private val social = mockk<SocialRepository>(relaxed = true)

    @Test fun `a burst of changes reloads the feed once, quietly`() = runTest(main) {
        val vm = FriendsViewModel(friends, lists, social, live)
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { vm.followLiveUpdates() }
        coEvery { friends.activity(any()) } returns listOf(entry("e2", "Collateral"), entry("e1", "Heat"))

        repeat(5) { events.emit(Unit) }
        advanceTimeBy(LiveFollower.DEBOUNCE_MS + 100)

        coVerify(exactly = 2) { friends.activity(any()) } // the first load, then one live reload for the burst
        assertEquals(listOf("Collateral", "Heat"), (vm.state.value.activity as Load.Success).data.map { it.title })
        assertFalse(vm.state.value.refreshing)
    }

    @Test fun `a failed live reload keeps the feed on screen`() = runTest(main) {
        val vm = FriendsViewModel(friends, lists, social, live)
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { vm.followLiveUpdates() }
        coEvery { friends.activity(any()) } throws IOException("offline")

        events.emit(Unit)
        advanceTimeBy(LiveFollower.DEBOUNCE_MS + 100)

        assertEquals(listOf("Heat"), (vm.state.value.activity as Load.Success).data.map { it.title })
    }

    @Test fun `the open comments sheet picks up new comments`() = runTest(main) {
        val first = Comment("c1", "ub", "ben", null, "Great", 1, canDelete = false)
        val second = Comment("c2", "uc", "cat", null, "Agreed", 2, canDelete = false)
        coEvery { social.comments("e1", "ua") } returns listOf(first)
        val vm = FriendsViewModel(friends, lists, social, live)
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { vm.followLiveUpdates() }
        vm.openComments(entry("e1", "Heat"))

        coEvery { social.comments("e1", "ua") } returns listOf(first, second)
        events.emit(Unit)
        advanceTimeBy(LiveFollower.DEBOUNCE_MS + 100)

        assertEquals(listOf("c1", "c2"), (vm.state.value.comments!!.comments as Load.Success).data.map { it.id })
    }

    @Test fun `coming back to the screen reloads what changed meanwhile`() = runTest(main) {
        val vm = FriendsViewModel(friends, lists, social, live)
        val following = backgroundScope.launch { vm.followLiveUpdates() }
        following.cancel() // the app went to the background
        coVerify(exactly = 1) { friends.activity(any()) }

        backgroundScope.launch { vm.followLiveUpdates() } // and came back
        coVerify(exactly = 2) { friends.activity(any()) }
    }

    @Test fun `a shared list shows titles friends add`() = runTest(main) {
        val ann = Profile("a", "ann")
        val repo = mockk<SharedListsRepository>(relaxed = true) {
            every { currentUserId } returns "a"
            coEvery { list("l1") } returns SharedList("l1", "Movie night", "a", listOf(ann), 1)
            coEvery { items("l1") } returns listOf(SharedListItem(MediaItem(MediaSource.TMDB, "1", MediaType.MOVIE, "Heat", null), ann, 0))
        }
        val vm = SharedListViewModel(SavedStateHandle(mapOf("id" to "l1")), repo, mockk(relaxed = true), live)
        backgroundScope.launch { vm.followLiveUpdates() }
        coEvery { repo.items("l1") } returns listOf(
            SharedListItem(MediaItem(MediaSource.TMDB, "1", MediaType.MOVIE, "Heat", null), ann, 0),
            SharedListItem(MediaItem(MediaSource.TMDB, "2", MediaType.MOVIE, "Ronin", null), Profile("b", "ben"), 1),
        )

        events.emit(Unit)
        advanceTimeBy(LiveFollower.DEBOUNCE_MS + 100)

        assertEquals(listOf("Heat", "Ronin"), (vm.state.value.items as Load.Success).data.map { it.item.title })
    }
}
