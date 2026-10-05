package com.trackr.app.data

import com.trackr.app.data.airing.AiringRefreshScheduler
import com.trackr.app.data.local.UserPrefs
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.ProfileRepository
import com.trackr.app.ui.screens.profile.ProfileViewModel
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsAiringTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun toggling_switch_persists_and_refreshes() = runTest {
        val prefs = mockk<UserPrefs>(relaxed = true)
        every { prefs.airingEnabled } returns MutableStateFlow(true)
        val refresh = mockk<AiringRefreshScheduler>(relaxed = true)
        val profiles = mockk<ProfileRepository>(relaxed = true)
        val lists = mockk<ListRepository>(relaxed = true)
        every { lists.entries } returns emptyFlow()
        val vm = ProfileViewModel(profiles, lists, prefs, refresh, mockk(relaxed = true))
        vm.setAiringEnabled(false)
        coVerify { prefs.setAiringEnabled(false) }
        verify { refresh.refreshNow() }
    }
}
