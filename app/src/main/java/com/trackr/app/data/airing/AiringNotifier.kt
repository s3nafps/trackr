package com.trackr.app.data.airing

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.trackr.app.MainActivity
import com.trackr.app.R
import com.trackr.app.data.local.AiringDao
import com.trackr.app.data.local.AiringEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiringNotifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dao: AiringDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    /** Posts the notification; false when notifications are denied/disabled. */
    fun post(row: AiringEntity): Boolean {
        if (!canPost()) return false
        val (title, text) = message(row)
        val code = AiringScheduler.requestCode(row.source, row.externalId)
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_SOURCE, row.source)
            .putExtra(EXTRA_OPEN_ID, row.externalId)
            .putExtra(EXTRA_OPEN_TYPE, row.mediaType)
        val tap = PendingIntent.getActivity(ctx, code, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_airing)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        return try {
            NotificationManagerCompat.from(ctx).notify(code, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** Used by the scheduler's late-fire path: post, then record it so refreshes never re-fire. */
    fun postAndMark(row: AiringEntity) {
        if (post(row)) scope.launch { dao.markNotified(row.source, row.externalId) }
    }

    companion object {
        const val CHANNEL_ID = "airing"
        const val CHANNEL_NAME = "New episodes & releases"
        const val EXTRA_OPEN_SOURCE = "open_source"
        const val EXTRA_OPEN_ID = "open_id"
        const val EXTRA_OPEN_TYPE = "open_type"

        /** (title, text). Episode rows read "<title> · Episode n is out"; episode-less rows are releases. */
        fun message(row: AiringEntity): Pair<String, String> =
            row.title to (row.episode?.let { "${row.title} · Episode $it is out" } ?: "${row.title} is out today")
    }
}
