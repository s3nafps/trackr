package com.trackr.app.data.update

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
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trackr.app.MainActivity
import com.trackr.app.R
import com.trackr.app.data.local.UserPrefs
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** "Trackr 1.13.0 is available": tapping it opens the app on the update dialog. */
@Singleton
class UpdateNotifier @Inject constructor(@ApplicationContext private val ctx: Context) {
    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** False when notifications are denied or turned off. */
    fun show(info: UpdateInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SHOW_UPDATE, true)
        val tap = PendingIntent.getActivity(ctx, NOTIFICATION_ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val changes = info.highlights
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_airing)
            .setContentTitle("Trackr ${info.version} is available")
            .setContentText(changes.firstOrNull() ?: "Tap to see what's new and update.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                if (changes.isEmpty()) "Tap to see what's new and update." else changes.joinToString("\n") { "• $it" },
            ))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        const val CHANNEL_ID = "updates"
        const val CHANNEL_NAME = "App updates"
        const val EXTRA_SHOW_UPDATE = "show_update"
        private const val NOTIFICATION_ID = 7_001
    }
}

/** Looks for a newer release in the background and notifies once per version. */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val updates: UpdateRepository,
    private val prefs: UserPrefs,
    private val notifier: UpdateNotifier,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val info: UpdateInfo? = try {
            updates.available()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.retry()
        }
        if (info == null) return Result.success()
        if (info.version != prefs.notifiedUpdate.first() && notifier.show(info)) prefs.setNotifiedUpdate(info.version)
        return Result.success()
    }
}

@Singleton
class UpdateCheckScheduler @Inject constructor(@ApplicationContext private val ctx: Context) {
    /** Every 12 hours while online; keeps an existing schedule. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val NAME = "update-check"
    }
}
