package com.trackr.app.ui.screens.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.importer.DocumentStore
import com.trackr.app.data.importer.ImportException
import com.trackr.app.data.importer.ImportSummary
import com.trackr.app.data.repository.ImportRepository
import com.trackr.app.data.repository.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ImportSource(val label: String) { MAL("MyAnimeList"), LETTERBOXD("Letterboxd"), BACKUP("your backup") }

enum class ExportFormat(val mime: String, val extension: String) { JSON("application/json", "json"), CSV("text/csv", "csv") }

data class ImportOutcome(val source: String, val summary: ImportSummary)

data class ImportExportUiState(
    val busy: Boolean = false,
    val progress: String? = null,
    val outcome: ImportOutcome? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val imports: ImportRepository,
    private val docs: DocumentStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ImportExportUiState())
    val state: StateFlow<ImportExportUiState> = _state.asStateFlow()

    fun importAniList(userName: String) = runImport("AniList") { imports.fromAniList(userName) }

    fun importFile(source: ImportSource, uri: Uri) = runImport(source.label) {
        val files = docs.read(uri)
        when (source) {
            ImportSource.MAL -> imports.fromMal(files)
            ImportSource.LETTERBOXD -> imports.fromLetterboxd(files) { done, total ->
                _state.update { it.copy(progress = "Matching films on TMDB… $done of $total") }
            }
            ImportSource.BACKUP -> imports.fromBackup(files)
        }
    }

    fun export(format: ExportFormat, uri: Uri) {
        if (_state.value.busy) return
        _state.value = ImportExportUiState(busy = true, progress = "Saving…")
        viewModelScope.launch {
            _state.value = try {
                docs.write(uri, if (format == ExportFormat.JSON) imports.exportJson() else imports.exportCsv())
                ImportExportUiState(message = "Saved your list.")
            } catch (e: Exception) {
                ImportExportUiState(error = "Couldn't save the file.")
            }
        }
    }

    private fun runImport(label: String, block: suspend () -> ImportSummary) {
        if (_state.value.busy) return
        _state.value = ImportExportUiState(busy = true, progress = "Importing from $label…")
        viewModelScope.launch {
            _state.value = try {
                ImportExportUiState(outcome = ImportOutcome(label, block()))
            } catch (e: ImportException) {
                ImportExportUiState(error = e.message)
            } catch (e: Exception) {
                ImportExportUiState(error = e.userMessage())
            }
        }
    }
}
