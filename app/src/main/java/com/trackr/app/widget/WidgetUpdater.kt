package com.trackr.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.trackr.app.data.local.AiringDao
import com.trackr.app.data.local.ListEntryDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Redraws the widget whenever the list or the airing schedule changes (edits, syncs, airing refreshes, sign-out). */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val listDao: ListEntryDao,
    private val airingDao: AiringDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            combine(listDao.observeAll(), airingDao.observeAll()) { _, _ -> }
                .debounce(DEBOUNCE_MS)
                // No widget placed is a cheap no-op; a failed redraw just waits for the next change.
                .collect { runCatching { UpNextWidget().updateAll(ctx) } }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}
