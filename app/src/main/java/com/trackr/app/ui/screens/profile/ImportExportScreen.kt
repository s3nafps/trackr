package com.trackr.app.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackr.app.ui.components.LocalProfile
import com.trackr.app.ui.components.TrackrTopBar
import java.time.LocalDate

@Composable
fun ImportExportScreen(onBack: () -> Unit, vm: ImportExportViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var pending by rememberSaveable { mutableStateOf<ImportSource?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val source = pending
        pending = null
        if (uri != null && source != null) vm.importFile(source, uri)
    }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.JSON.mime)) { uri ->
        uri?.let { vm.export(ExportFormat.JSON, it) }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime)) { uri ->
        uri?.let { vm.export(ExportFormat.CSV, it) }
    }
    Column(Modifier.fillMaxSize()) {
        TrackrTopBar("Import & export", LocalProfile.current?.avatarUrl, {}, onBack = onBack)
        ImportExportContent(
            state = state,
            onImportAniList = vm::importAniList,
            // Exports arrive as .xml.gz / .zip / .json with unreliable MIME types, so let any file be picked.
            onPick = { source -> pending = source; picker.launch(arrayOf("*/*")) },
            onExport = { format ->
                val name = "trackr-${LocalDate.now()}.${format.extension}"
                if (format == ExportFormat.JSON) saveJson.launch(name) else saveCsv.launch(name)
            },
        )
    }
}

@Composable
fun ImportExportContent(
    state: ImportExportUiState,
    onImportAniList: (String) -> Unit,
    onPick: (ImportSource) -> Unit,
    onExport: (ExportFormat) -> Unit,
) {
    var userName by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Import", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Adds titles you don't have yet. Anything already in your list stays as it is.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Status(state)

        SourceCard("AniList", "Your public anime list, with status, score and progress.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    userName, { userName = it }, Modifier.weight(1f), singleLine = true, enabled = !state.busy,
                    placeholder = { Text("Username") },
                )
                Button(onClick = { onImportAniList(userName) }, enabled = userName.isNotBlank() && !state.busy) { Text("Import") }
            }
        }
        SourceCard(
            "MyAnimeList",
            "Export your anime list on myanimelist.net (myanimelist.net/panel.php?go=export), then choose the downloaded .xml.gz file.",
        ) { ChooseFile(state.busy) { onPick(ImportSource.MAL) } }
        SourceCard(
            "Letterboxd",
            "Export your data on letterboxd.com (Settings → Data), then choose the .zip. Films are matched on TMDB by title and year.",
        ) { ChooseFile(state.busy) { onPick(ImportSource.LETTERBOXD) } }
        SourceCard("Trackr backup", "Restore a backup made with Export below.") { ChooseFile(state.busy) { onPick(ImportSource.BACKUP) } }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Export", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Save your whole list as a backup you can restore, or as a spreadsheet.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onExport(ExportFormat.JSON) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Backup (JSON)") }
            OutlinedButton(onClick = { onExport(ExportFormat.CSV) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Spreadsheet (CSV)") }
        }
    }
}

@Composable
private fun SourceCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun ChooseFile(busy: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = !busy) { Text("Choose file") }
}

/** Progress, the last import's summary, or an error; nothing when idle. */
@Composable
private fun Status(state: ImportExportUiState) {
    when {
        state.busy -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(state.progress ?: "Working…", style = MaterialTheme.typography.bodyMedium)
        }
        state.error != null -> Text(state.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        state.outcome != null -> {
            val s = state.outcome.summary
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Imported from ${state.outcome.source}", style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        "${s.added} added",
                        s.skipped.takeIf { it > 0 }?.let { "$it already in your list" },
                        s.notFound.size.takeIf { it > 0 }?.let { "$it not found" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (s.notFound.isNotEmpty()) {
                    val shown = s.notFound.take(MAX_NOT_FOUND_SHOWN)
                    Text(
                        "Couldn't match: " + shown.joinToString(", ") + (if (s.notFound.size > shown.size) " and ${s.notFound.size - shown.size} more" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        state.message != null -> Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

private const val MAX_NOT_FOUND_SHOWN = 10
