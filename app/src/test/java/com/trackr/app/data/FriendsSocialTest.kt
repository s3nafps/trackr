package com.trackr.app.data

import com.trackr.app.data.repository.FriendsRepository
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.SocialRepository
import com.trackr.app.domain.model.ActivityEntry
import com.trackr.app.domain.model.EntrySocial
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.Profile
import com.trackr.app.domain.model.Recommendation
import com.trackr.app.ui.screens.friends.FriendsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsSocialTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val entry = ActivityEntry("e1", "ua", "ann", null, MediaSource.TMDB, "1", MediaType.MOVIE, "Heat", null, ListStatus.COMPLETED, 9, 1, 1, 0)
    private val rec = Recommendation("r1", Profile("ub", "ben"), MediaItem(MediaSource.TMDB, "2", MediaType.MOVIE, "Collateral", null), null, seen = false, createdAt = 1)
    private val friends = mockk<FriendsRepository>(relaxed = true) { coEvery { activity(any()) } returns listOf(entry) }
    private val lists = mockk<ListRepository>(relaxed = true) { every { entries } returns MutableStateFlow(emptyList()) }
    private val social = mockk<SocialRepository>(relaxed = true) {
        coEvery { socialFor(listOf("e1")) } returns mapOf("e1" to EntrySocial(mapOf("🔥" to 1), emptySet(), commentCount = 1))
        coEvery { inbox() } returns listOf(rec)
    }

    private suspend fun FriendsViewModel.now() = state.first { it.activity is com.trackr.app.domain.model.Load.Success }

    @Test fun `the feed loads reactions and the inbox`() = runTest {
        val vm = FriendsViewModel(friends, lists, social, mockk(relaxed = true))
        val s = vm.now()
        assertEquals(EntrySocial(mapOf("🔥" to 1), emptySet(), 1), s.social["e1"])
        assertEquals(listOf(rec), s.inbox)
    }

    @Test fun `reacting is optimistic and undone when the server refuses`() = runTest {
        val vm = FriendsViewModel(friends, lists, social, mockk(relaxed = true))
        vm.now()
        vm.toggleReaction("e1", "🔥")
        coVerify { social.setReaction("e1", "🔥", true) }
        assertEquals(EntrySocial(mapOf("🔥" to 2), setOf("🔥"), 1), vm.now().social["e1"])

        coEvery { social.setReaction("e1", "😂", true) } throws IOException("offline")
        vm.toggleReaction("e1", "😂")
        val s = vm.now()
        assertEquals(EntrySocial(mapOf("🔥" to 2), setOf("🔥"), 1), s.social["e1"])
        assertTrue(s.toast != null)
    }

    @Test fun `opening a recommendation marks it seen, dismissing removes it`() = runTest {
        val vm = FriendsViewModel(friends, lists, social, mockk(relaxed = true))
        vm.now()
        vm.openRecommendation(rec)
        coVerify { social.markSeen("r1") }
        assertTrue(vm.now().inbox.single().seen)
        vm.dismissRecommendation(rec)
        coVerify { social.deleteRecommendation("r1") }
        assertTrue(vm.now().inbox.isEmpty())
    }

    @Test fun `posting a comment bumps the entry's comment count`() = runTest {
        coEvery { social.comments("e1", "ua") } returns emptyList()
        val vm = FriendsViewModel(friends, lists, social, mockk(relaxed = true))
        vm.now()
        vm.openComments(entry)
        assertEquals("e1", vm.now().comments?.entryId)
        vm.postComment("great")
        assertEquals(2, vm.now().social["e1"]?.commentCount)
        vm.closeComments()
        assertEquals(null, vm.now().comments)
    }
}
