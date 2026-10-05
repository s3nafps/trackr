package com.trackr.app.data.realtime

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/**
 * A screen's live-updates loop: [reload] runs after each burst of changes to [tables] (one bulk sync is many rows), and
 * once when following resumes, for what changed while the screen wasn't visible.
 */
class LiveFollower(private val live: LiveUpdates, private vararg val tables: String) {
    private var paused = false

    @OptIn(FlowPreview::class)
    suspend fun follow(reload: () -> Unit) {
        if (paused) reload()
        try {
            live.changes(*tables).debounce(DEBOUNCE_MS).collect { reload() }
        } finally {
            paused = true
        }
    }

    companion object {
        const val DEBOUNCE_MS = 600L
    }
}
