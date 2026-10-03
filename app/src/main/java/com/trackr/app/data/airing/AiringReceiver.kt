package com.trackr.app.data.airing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trackr.app.data.local.AiringDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fires at air time: posts the notification for the row and records it as notified. */
@AndroidEntryPoint
class AiringReceiver : BroadcastReceiver() {
    @Inject lateinit var dao: AiringDao
    @Inject lateinit var notifier: AiringNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val source = intent.getStringExtra(AndroidAlarmClient.EXTRA_SOURCE) ?: return
        val id = intent.getStringExtra(AndroidAlarmClient.EXTRA_ID) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val row = dao.get(source, id)
                if (row != null && !row.notified && notifier.post(row)) dao.markNotified(source, id)
            } finally {
                pending.finish()
            }
        }
    }
}
