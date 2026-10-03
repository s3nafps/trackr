package com.trackr.app.data.airing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Thin seam over [AlarmManager] so the scheduler can be tested with a fake. */
interface AlarmClient {
    fun canScheduleExact(): Boolean
    fun set(code: Int, atMillis: Long, exact: Boolean, source: String, externalId: String)
    fun cancel(code: Int)
}

class AndroidAlarmClient @Inject constructor(@ApplicationContext private val ctx: Context) : AlarmClient {
    private val manager get() = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun canScheduleExact() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun intent(code: Int, source: String? = null, externalId: String? = null, flags: Int): PendingIntent? {
        val i = Intent().setComponent(ComponentName(ctx, RECEIVER_CLASS))
        if (source != null) i.putExtra(EXTRA_SOURCE, source)
        if (externalId != null) i.putExtra(EXTRA_ID, externalId)
        return PendingIntent.getBroadcast(ctx, code, i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    override fun set(code: Int, atMillis: Long, exact: Boolean, source: String, externalId: String) {
        val pi = intent(code, source, externalId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        if (exact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
    }

    override fun cancel(code: Int) {
        val pi = intent(code, flags = PendingIntent.FLAG_NO_CREATE) ?: return
        manager.cancel(pi)
        pi.cancel()
    }

    companion object {
        const val RECEIVER_CLASS = "com.trackr.app.data.airing.AiringReceiver"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_ID = "externalId"
    }
}
