package com.trackr.app.data

import com.trackr.app.data.repository.AiringRepository
import com.trackr.app.data.repository.AuthRepository
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.ProfileRepository
import com.trackr.app.domain.model.Profile
import com.trackr.app.ui.screens.auth.AuthViewModel
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignOutAiringTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `sign out clears airing and cancels alarms`() {
        val auth = mockk<AuthRepository>(relaxed = true) { every { sessionStatus } returns emptyFlow() }
        val profiles = mockk<ProfileRepository>(relaxed = true) { every { me } returns MutableStateFlow<Profile?>(null) }
        val lists = mockk<ListRepository>(relaxed = true)
        val airing = mockk<AiringRepository>(relaxed = true)
        AuthViewModel(auth, profiles, lists, airing).signOut()
        coVerify { lists.clearLocal() }
        coVerify(exactly = 1) { airing.clearAll() }
    }
}
