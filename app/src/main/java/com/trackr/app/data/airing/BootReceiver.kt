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

/** Alarms are wiped on reboot/app update: rebuild them from the airing table. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var dao: AiringDao
    @Inject lateinit var scheduler: AiringScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                scheduler.apply(dao.getAll(), emptyList())
            } finally {
                pending.finish()
            }
        }
    }
}
