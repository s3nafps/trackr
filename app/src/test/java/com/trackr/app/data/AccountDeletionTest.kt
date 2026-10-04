package com.trackr.app.data

import com.trackr.app.data.repository.AiringRepository
import com.trackr.app.data.repository.AuthRepository
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.ProfileRepository
import com.trackr.app.domain.model.Profile
import com.trackr.app.ui.screens.auth.AuthViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDeletionTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val auth = mockk<AuthRepository>(relaxed = true) { every { sessionStatus } returns emptyFlow() }
    private val profiles = mockk<ProfileRepository>(relaxed = true) { every { me } returns MutableStateFlow<Profile?>(null) }
    private val lists = mockk<ListRepository>(relaxed = true)
    private val airing = mockk<AiringRepository>(relaxed = true)

    @Test fun `successful deletion wipes local data without syncing`() {
        val vm = AuthViewModel(auth, profiles, lists, airing)
        vm.deleteAccount()
        coVerify(exactly = 1) { auth.deleteAccount() }
        coVerify(exactly = 0) { lists.sync() }
        coVerify { lists.clearLocal() }
        coVerify(exactly = 1) { airing.clearAll() }
        verify { profiles.clearMe() }
        assertFalse(vm.state.value.deletingAccount)
        assertNull(vm.state.value.deleteError)
    }

    @Test fun `failed deletion keeps local data and reports an error`() {
        coEvery { auth.deleteAccount() } throws IOException("offline")
        val vm = AuthViewModel(auth, profiles, lists, airing)
        vm.deleteAccount()
        coVerify(exactly = 0) { lists.clearLocal() }
        coVerify(exactly = 0) { airing.clearAll() }
        assertFalse(vm.state.value.deletingAccount)
        assertNotNull(vm.state.value.deleteError)
        vm.dismissDeleteError()
        assertNull(vm.state.value.deleteError)
    }

    @Test fun `login stays disabled until the post-deletion wipe finishes`() {
        val wipe = CompletableDeferred<Unit>()
        coEvery { lists.clearLocal() } coAnswers { wipe.await() }
        val vm = AuthViewModel(auth, profiles, lists, airing)
        vm.deleteAccount()
        assertTrue(vm.state.value.busy)
        wipe.complete(Unit)
        assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.deletingAccount)
    }

    @Test fun `login stays disabled until the sign-out wipe finishes`() {
        val wipe = CompletableDeferred<Unit>()
        coEvery { lists.clearLocal() } coAnswers { wipe.await() }
        val vm = AuthViewModel(auth, profiles, lists, airing)
        vm.signOut()
        assertTrue(vm.state.value.busy)
        wipe.complete(Unit)
        assertFalse(vm.state.value.busy)
    }

    @Test fun `failed deletion re-enables the UI`() {
        coEvery { auth.deleteAccount() } throws IOException("offline")
        val vm = AuthViewModel(auth, profiles, lists, airing)
        vm.deleteAccount()
        assertFalse(vm.state.value.busy)
    }
}
