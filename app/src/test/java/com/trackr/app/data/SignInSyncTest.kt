package com.trackr.app.data

import com.trackr.app.data.repository.AiringRepository
import com.trackr.app.data.repository.AuthRepository
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.ProfileRepository
import com.trackr.app.domain.model.Profile
import com.trackr.app.ui.screens.auth.AuthViewModel
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Signing back in brings the list down again: sign-out wipes it from the device. */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInSyncTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val status = MutableSharedFlow<SessionStatus>()
    private var userId: String? = "u1"
    private val auth = mockk<AuthRepository>(relaxed = true) {
        every { sessionStatus } returns status
        every { currentUserId } answers { userId }
    }
    private val profiles = mockk<ProfileRepository>(relaxed = true) { every { me } returns MutableStateFlow<Profile?>(null) }
    private val lists = mockk<ListRepository>(relaxed = true)
    private val airing = mockk<AiringRepository>(relaxed = true)

    private fun signedIn() = SessionStatus.Authenticated(mockk<UserSession> { every { user } returns null }, mockk<SessionSource>())

    @Test fun `the list is pulled once per sign-in, not on every token refresh`() = runTest {
        AuthViewModel(auth, profiles, lists, airing)
        status.emit(signedIn())
        status.emit(signedIn()) // token refresh
        coVerify(exactly = 1) { lists.sync() }

        status.emit(SessionStatus.NotAuthenticated(true))
        status.emit(signedIn())
        coVerify(exactly = 2) { lists.sync() }
    }
}
