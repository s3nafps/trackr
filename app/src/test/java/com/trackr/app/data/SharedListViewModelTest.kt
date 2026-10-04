package com.trackr.app.data

import androidx.lifecycle.SavedStateHandle
import com.trackr.app.data.repository.FriendsRepository
import com.trackr.app.data.repository.SharedListsRepository
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.Profile
import com.trackr.app.domain.model.SharedList
import com.trackr.app.domain.model.SharedListItem
import com.trackr.app.ui.screens.social.SharedListViewModel
import com.trackr.app.ui.screens.social.SharedListsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SharedListViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val ann = Profile("a", "ann")
    private val ben = Profile("b", "ben")
    private fun item(id: String) = SharedListItem(MediaItem(MediaSource.TMDB, id, MediaType.MOVIE, "T$id", null), ann, 0)
    private val list = SharedList("l1", "Movie night", ownerId = "a", members = listOf(ann, ben), itemCount = 3)

    private val repo = mockk<SharedListsRepository>(relaxed = true) {
        every { currentUserId } returns "a"
        coEvery { list("l1") } returns list
        coEvery { items("l1") } returns listOf(item("1"), item("2"), item("3"))
    }
    private val friends = mockk<FriendsRepository>(relaxed = true)
    private fun vm() = SharedListViewModel(SavedStateHandle(mapOf("id" to "l1")), repo, friends)

    @Test fun `opening loads the list and its items, and knows who owns it`() {
        val s = vm().state.value
        assertEquals(Load.Success(list), s.list)
        assertEquals(3, (s.items as Load.Success).data.size)
        assertTrue(s.isOwner)
    }

    @Test fun `a non-owner is not the owner`() {
        every { repo.currentUserId } returns "b"
        assertFalse(vm().state.value.isOwner)
    }

    @Test fun `pick for us never repeats itself back to back`() {
        val vm = vm()
        var last: SharedListItem? = null
        repeat(20) {
            vm.pickForUs()
            val p = vm.state.value.pick
            assertNotEquals(last, p)
            last = p
        }
        vm.closePick()
        assertEquals(null, vm.state.value.pick)
    }

    @Test fun `removing an item is optimistic and comes back if the server refuses`() {
        val vm = vm()
        vm.removeItem(item("1"))
        coVerify { repo.removeItem("l1", item("1").item) }
        assertEquals(listOf("2", "3"), (vm.state.value.items as Load.Success).data.map { it.item.externalId })

        coEvery { repo.removeItem("l1", item("2").item) } throws IOException("offline")
        vm.removeItem(item("2"))
        assertEquals(listOf("2", "3"), (vm.state.value.items as Load.Success).data.map { it.item.externalId })
        assertTrue(vm.state.value.toast != null)
    }

    @Test fun `leaving closes the screen`() {
        val vm = vm()
        vm.leave()
        coVerify { repo.leave("l1") }
        assertTrue(vm.state.value.closed)
    }

    @Test fun `adding a member reloads the list`() {
        val vm = vm()
        val withCat = list.copy(members = list.members + Profile("c", "cat"))
        coEvery { repo.list("l1") } returns withCat
        vm.openMembers()
        vm.addMember(Profile("c", "cat"))
        coVerify { repo.addMember("l1", "c") }
        assertEquals(Load.Success(withCat), vm.state.value.list)
        assertEquals(null, vm.state.value.members?.busy)
    }

    @Test fun `creating a list opens it and reports friends that couldn't be added`() {
        coEvery { repo.lists() } returns emptyList()
        coEvery { repo.create("Movie night", setOf("b", "x")) } returns ("new" to listOf("x"))
        val vm = SharedListsViewModel(repo, friends)
        vm.openCreate()
        vm.create("Movie night", setOf("b", "x"))
        val s = vm.state.value
        assertEquals("new", s.openList)
        assertEquals(null, s.creating)
        assertTrue(s.toast!!.contains("1"))
    }
}
