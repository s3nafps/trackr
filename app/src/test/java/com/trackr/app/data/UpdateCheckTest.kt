package com.trackr.app.data

import com.trackr.app.data.local.UserPrefs
import com.trackr.app.data.update.GithubAsset
import com.trackr.app.data.update.GithubRelease
import com.trackr.app.data.update.GithubReleases
import com.trackr.app.data.update.UpdateCheck
import com.trackr.app.data.update.UpdateRepository
import com.trackr.app.domain.util.AppVersion
import com.trackr.app.ui.UpdateViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val sha = "a".repeat(64)
    private fun release(tag: String, body: String? = null) = GithubRelease(
        tag, "https://github.com/s3nafps/trackr/releases/tag/$tag", body,
        listOf(
            GithubAsset("notes.txt", "https://github.com/s3nafps/trackr/releases/download/$tag/notes.txt"),
            GithubAsset("trackr-$tag.apk", "https://github.com/s3nafps/trackr/releases/download/$tag/trackr-$tag.apk", "sha256:$sha"),
        ),
    )

    private val releases = mockk<GithubReleases>()
    private val prefs = mockk<UserPrefs>(relaxed = true) {
        every { lastUpdateCheck } returns flowOf(0L)
        every { dismissedUpdate } returns flowOf(null)
    }
    private val repo = UpdateRepository(releases, prefs)

    @Test fun `versions compare part by part`() {
        assertTrue(AppVersion.parse("v1.10.0")!! > AppVersion.parse("1.9.3")!!)
        assertEquals(0, AppVersion.parse("1.2")!!.compareTo(AppVersion.parse("v1.2.0")!!))
        assertEquals(AppVersion(listOf(2, 0, 1)), AppVersion.parse("v2.0.1-beta"))
        assertNull(AppVersion.parse("nightly"))
    }

    @Test fun `a newer release becomes an update with its apk and highlights`() {
        val notes = "Intro\n\n## Install\n- Download it\n\n## What's new\n- **For You** on Home.\n- Genres in Search.\n\n## Notes\n- Not this"
        val info = UpdateRepository.toUpdate(release("v1.4.0", notes), current = "1.3.0")!!
        assertEquals("1.4.0", info.version)
        assertEquals("https://github.com/s3nafps/trackr/releases/download/v1.4.0/trackr-v1.4.0.apk", info.apkUrl)
        assertEquals(sha, info.apkSha256)
        assertEquals(listOf("For You on Home.", "Genres in Search."), info.highlights)
        assertNull(UpdateRepository.toUpdate(release("v1.3.0"), current = "1.3.0"))
        assertNull(UpdateRepository.toUpdate(release("v1.2.9"), current = "1.3.0"))
    }

    @Test fun `an apk is offered only from github over https with a published checksum`() {
        fun apk(url: String, digest: String?) = GithubRelease("v9.0.0", "https://github.com/r", null, listOf(GithubAsset("trackr-v9.0.0.apk", url, digest)))
        assertNull(UpdateRepository.toUpdate(apk("https://evil.example/trackr.apk", "sha256:$sha"), "1.0.0")!!.apkUrl)
        assertNull(UpdateRepository.toUpdate(apk("https://github.com/a.apk", null), "1.0.0")!!.apkUrl)
        assertNull(UpdateRepository.toUpdate(apk("http://github.com/a.apk", "sha256:$sha"), "1.0.0")!!.apkUrl)
        assertNull(UpdateRepository.toUpdate(apk("https://github.com/a.apk", "md5:abc"), "1.0.0")!!.apkUrl)
    }

    @Test fun `github's release json decodes`() {
        val json = Json { ignoreUnknownKeys = true }
        val r = json.decodeFromString(
            GithubRelease.serializer(),
            """{"id":1,"tag_name":"v1.4.0","html_url":"https://github.com/r","body":"x","draft":false,
               "assets":[{"id":2,"name":"trackr-v1.4.0.apk","size":5,"browser_download_url":"https://github.com/a.apk"}]}""",
        )
        assertEquals("v1.4.0", r.tag)
        assertEquals("https://github.com/a.apk", r.assets.single().downloadUrl)
    }

    @Test fun `automatic checks wait 12 hours and skip a version put off with Later`() = runTest {
        coEvery { releases.latest() } returns release("v9.0.0")
        every { prefs.lastUpdateCheck } returns flowOf(1_000L)
        assertEquals(UpdateCheck.Skipped, repo.check(manual = false, now = 1_000L + 60_000, current = "1.0.0"))
        coVerify(exactly = 0) { releases.latest() }

        val later = 1_000L + UpdateRepository.CHECK_INTERVAL_MS
        assertTrue(repo.check(manual = false, now = later, current = "1.0.0") is UpdateCheck.Available)
        coVerify { prefs.setLastUpdateCheck(later) }

        every { prefs.dismissedUpdate } returns flowOf("9.0.0")
        assertEquals(UpdateCheck.Skipped, repo.check(manual = false, now = later, current = "1.0.0"))
        assertTrue("asking from About still shows it", repo.check(manual = true, now = later, current = "1.0.0") is UpdateCheck.Available)
    }

    @Test fun `no release yet means up to date`() = runTest {
        coEvery { releases.latest() } returns null
        assertEquals(UpdateCheck.UpToDate, repo.check(manual = true, current = "1.0.0"))
    }

    @Test fun `the dialog shows an update, Later remembers it, and a manual check reports the result`() = runTest {
        val updates = mockk<UpdateRepository>(relaxed = true)
        val info = UpdateRepository.toUpdate(release("v9.0.0"), "1.0.0")!!
        coEvery { updates.check(false, any(), any()) } returns UpdateCheck.Available(info)
        val vm = UpdateViewModel(updates)
        assertEquals(info, vm.state.value.available)
        vm.later()
        assertNull(vm.state.value.available)
        coVerify { updates.dismiss("9.0.0") }

        coEvery { updates.check(true, any(), any()) } returns UpdateCheck.UpToDate
        vm.checkNow()
        assertEquals("You have the latest version.", vm.state.value.message)

        coEvery { updates.check(true, any(), any()) } throws UnknownHostException()
        vm.checkNow()
        assertTrue(vm.state.value.message!!.startsWith("Couldn't check for updates"))
    }

    @Test fun `a failed automatic check stays quiet`() = runTest {
        val updates = mockk<UpdateRepository>(relaxed = true)
        coEvery { updates.check(false, any(), any()) } throws UnknownHostException()
        val vm = UpdateViewModel(updates)
        assertNull(vm.state.value.message)
        assertNull(vm.state.value.available)
    }
}
