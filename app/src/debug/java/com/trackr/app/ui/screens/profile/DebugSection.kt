package com.trackr.app.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.airing.AiringScheduler
import com.trackr.app.data.local.AiringDao
import com.trackr.app.data.local.AiringEntity
import com.trackr.app.data.local.ListEntryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugAiringViewModel @Inject constructor(
    private val list: ListEntryDao,
    private val airing: AiringDao,
    private val scheduler: AiringScheduler,
) : ViewModel() {
    /** Schedules an alarm 20s from now for the first tracked title. Reports what happened. */
    fun fireIn20s(report: (String) -> Unit) {
        viewModelScope.launch {
            val e = list.getAllRaw().firstOrNull { !it.deleted && it.status == "watching" }
                ?: list.getAllRaw().firstOrNull { !it.deleted }
            if (e == null) { report("Track a title first"); return@launch }
            val row = AiringEntity(e.source, e.externalId, e.mediaType, e.title, e.progress + 1, System.currentTimeMillis() + 20_000, "TIME")
            airing.upsert(row)
            scheduler.apply(listOf(row), emptyList())
            report("Alarm for ${e.title} in 20s")
        }
    }
}

@Composable
fun DebugSection(vm: DebugAiringViewModel = hiltViewModel()) {
    var msg by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Debug", style = MaterialTheme.typography.headlineSmall)
        OutlinedButton(onClick = { vm.fireIn20s { msg = it } }) { Text("Fire airing alert in 20s") }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
