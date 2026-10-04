package com.trackr.app.data

import android.net.Uri
import com.trackr.app.data.importer.DocumentStore
import com.trackr.app.data.importer.ImportException
import com.trackr.app.data.importer.ImportSummary
import com.trackr.app.data.repository.ImportRepository
import com.trackr.app.ui.screens.profile.ExportFormat
import com.trackr.app.ui.screens.profile.ImportExportViewModel
import com.trackr.app.ui.screens.profile.ImportOutcome
import com.trackr.app.ui.screens.profile.ImportSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportExportViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val imports = mockk<ImportRepository>()
    private val docs = mockk<DocumentStore>()
    private val uri = mockk<Uri>()

    @Test fun `a successful import shows its summary`() {
        coEvery { imports.fromAniList("me") } returns ImportSummary(3, 1)
        val vm = ImportExportViewModel(imports, docs)
        vm.importAniList("me")
        assertEquals(ImportOutcome("AniList", ImportSummary(3, 1)), vm.state.value.outcome)
        assertFalse(vm.state.value.busy)
    }

    @Test fun `import errors are shown and clear the previous result`() {
        coEvery { imports.fromAniList("me") } returns ImportSummary(3, 1)
        coEvery { docs.read(uri) } returns mapOf("x.xml" to "<html/>")
        coEvery { imports.fromMal(any()) } throws ImportException("That isn't a MyAnimeList export file.")
        val vm = ImportExportViewModel(imports, docs)
        vm.importAniList("me")
        vm.importFile(ImportSource.MAL, uri)
        assertEquals("That isn't a MyAnimeList export file.", vm.state.value.error)
        assertNull(vm.state.value.outcome)
    }

    @Test fun `a second import is ignored while one is running`() {
        val gate = CompletableDeferred<ImportSummary>()
        coEvery { imports.fromAniList(any()) } coAnswers { gate.await() }
        val vm = ImportExportViewModel(imports, docs)
        vm.importAniList("a")
        vm.importAniList("b")
        assertTrue(vm.state.value.busy)
        gate.complete(ImportSummary(1, 0))
        coVerify(exactly = 1) { imports.fromAniList(any()) }
        assertFalse(vm.state.value.busy)
    }

    @Test fun `letterboxd progress is reported while matching`() {
        val gate = CompletableDeferred<ImportSummary>()
        coEvery { docs.read(uri) } returns mapOf("watched.csv" to "Name\nHeat\n")
        coEvery { imports.fromLetterboxd(any(), any()) } coAnswers {
            secondArg<(Int, Int) -> Unit>().invoke(2, 5)
            gate.await()
        }
        val vm = ImportExportViewModel(imports, docs)
        vm.importFile(ImportSource.LETTERBOXD, uri)
        assertEquals("Matching films on TMDB… 2 of 5", vm.state.value.progress)
        gate.complete(ImportSummary(5, 0))
        assertEquals(5, vm.state.value.outcome?.summary?.added)
    }

    @Test fun `export writes the chosen format`() {
        coEvery { imports.exportCsv() } returns "title\r\n"
        coEvery { docs.write(uri, "title\r\n") } returns Unit
        val vm = ImportExportViewModel(imports, docs)
        vm.export(ExportFormat.CSV, uri)
        coVerify { docs.write(uri, "title\r\n") }
        assertEquals("Saved your list.", vm.state.value.message)
    }
}
