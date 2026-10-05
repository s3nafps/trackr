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

/**
 * Alarms are wiped on reboot/app update: rebuild them from the airing table. Also runs when the user allows exact
 * alarms, so alerts scheduled as inexact become exact.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var dao: AiringDao
    @Inject lateinit var scheduler: AiringScheduler
    @Inject lateinit var refresh: AiringRefreshScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        refresh.refreshNow()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                scheduler.apply(dao.getAll(), emptyList())
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31), sent when exact alarms are allowed.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
