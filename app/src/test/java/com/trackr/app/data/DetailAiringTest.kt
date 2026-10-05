package com.trackr.app.data

import androidx.lifecycle.SavedStateHandle
import com.trackr.app.data.local.UserPrefs
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.MediaRepository
import com.trackr.app.data.repository.SocialRepository
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaDetail
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.ui.screens.detail.BellState
import com.trackr.app.ui.screens.detail.DetailViewModel
import com.trackr.app.ui.screens.detail.airingLine
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class DetailAiringTest {
    private val zone = ZoneId.of("UTC")
    private fun epoch(s: String) = ZonedDateTime.parse(s).toEpochSecond()
    private val now = epoch("2026-10-22T14:00:00Z") * 1000

    private fun item(type: MediaType = MediaType.TV, at: Long? = null, ep: Int? = 12, dateOnly: Boolean = false) =
        MediaItem(MediaSource.ANILIST, "1", type, "Frieren", null, airingEpisode = ep, airingAtEpoch = at, airingDateOnly = dateOnly)

    @Test fun airingLine_time_precision() {
        val i = item(at = epoch("2026-10-24T18:30:00Z"))
        assertEquals("Next episode: Ep 12 · Sat 18:30 (in 2d 4h)", airingLine(i, now, zone, Locale.ENGLISH))
    }

    @Test fun airingLine_date_precision() {
        val i = item(at = epoch("2026-10-24T09:00:00Z"), dateOnly = true)
        assertEquals("Next episode: Ep 12 · Sat, Oct 24", airingLine(i, now, zone, Locale.ENGLISH))
    }

    @Test fun airingLine_movie() {
        val i = item(MediaType.MOVIE, at = epoch("2026-10-24T09:00:00Z"), ep = null, dateOnly = true)
        assertEquals("Releases Oct 24", airingLine(i, now, zone, Locale.ENGLISH))
    }

    @Test fun airingLine_null_without_data() {
        assertNull(airingLine(item(at = null), now, zone, Locale.ENGLISH))
    }

    // --- bell ---
    private val entryFlow = MutableStateFlow<ListEntry?>(null)
    private val lists = mockk<ListRepository>(relaxed = true)
    private val enabled = MutableStateFlow(true)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { lists.entry(any<String>(), any()) } returns entryFlow
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(): DetailViewModel {
        val media = mockk<MediaRepository>()
        coEvery { media.detail(any(), any(), any(), any()) } returns MediaDetail(item())
        val prefs = mockk<UserPrefs>()
        every { prefs.airingEnabled } returns enabled
        val social = mockk<SocialRepository>(relaxed = true)
        val saved = SavedStateHandle(mapOf("source" to "anilist", "type" to "tv", "id" to "1"))
        return DetailViewModel(saved, media, lists, social, prefs, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
    }

    private fun entry(status: ListStatus, notify: Boolean = false) =
        ListEntry(MediaSource.ANILIST, "1", MediaType.TV, "Frieren", null, null, status, null, 0, null, 0, notify)

    private suspend fun DetailViewModel.bell() = bellState.first { true }

    @Test fun bell_hidden_when_completed() = runTest {
        entryFlow.value = entry(ListStatus.COMPLETED)
        assertEquals(BellState.Hidden, vm().bell())
    }

    @Test fun bell_on_for_watching() = runTest {
        entryFlow.value = entry(ListStatus.WATCHING)
        assertEquals(BellState.On, vm().bell())
    }

    @Test fun bell_toggle_calls_setNotify_for_plan() = runTest {
        val e = entry(ListStatus.PLAN_TO_WATCH)
        entryFlow.value = e
        val v = vm()
        assertEquals(BellState.Off, v.bell())
        v.toggleBell()
        coVerify { lists.setNotify(e, true) }
    }

    @Test fun bell_hidden_when_permission_denied() = runTest {
        entryFlow.value = entry(ListStatus.PLAN_TO_WATCH)
        val v = vm()
        v.onPermissionResult(false)
        assertEquals(BellState.Hidden, v.bell())
    }

    @Test fun bell_hidden_when_master_switch_off() = runTest {
        entryFlow.value = entry(ListStatus.WATCHING)
        enabled.value = false
        assertEquals(BellState.Hidden, vm().bell())
    }
}
