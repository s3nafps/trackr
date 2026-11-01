package com.trackr.app.data.social

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
import com.trackr.app.data.airing.AiringNotifier
import com.trackr.app.data.local.UserPrefs
import com.trackr.app.data.mapper.ListEntryMapper.parseInstant
import com.trackr.app.data.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Reads social_notifications(since), the feed of friend activity that concerns the signed-in user. */
class SocialFeed @Inject constructor(private val supabase: SupabaseClient) {
    suspend fun since(at: Long): List<SocialNotificationDto> =
        supabase.postgrest.rpc("social_notifications", buildJsonObject { put("since", Instant.ofEpochMilli(at).toString()) })
            .decodeList()
}

/** Friend requests, comments, reactions and recommendations as notifications. */
@Singleton
class SocialNotifier @Inject constructor(@ApplicationContext private val ctx: Context) {
    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Friend requests, comments, reactions and recommendations"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun allowed(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun show(alerts: List<SocialAlert>) {
        if (alerts.isEmpty() || !allowed()) return
        for (alert in alerts) {
            val id = ID_BASE + (alert.key.hashCode() and 0xFFFF)
            val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            when (val t = alert.target) {
                AlertTarget.Friends -> open.putExtra(EXTRA_OPEN_FRIENDS, true)
                is AlertTarget.Title -> open.putExtra(AiringNotifier.EXTRA_OPEN_SOURCE, t.source)
                    .putExtra(AiringNotifier.EXTRA_OPEN_TYPE, t.type)
                    .putExtra(AiringNotifier.EXTRA_OPEN_ID, t.id)
            }
            val tap = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_airing)
                .setContentTitle(alert.title)
                .apply { alert.text?.takeIf { it.isNotBlank() }?.let { setContentText(it).setStyle(NotificationCompat.BigTextStyle().bigText(it)) } }
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setGroup(GROUP)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(ctx).notify(id, n)
            } catch (_: SecurityException) {
                return
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "social"
        const val CHANNEL_NAME = "Friends"
        /** MainActivity opens the Friends tab. */
        const val EXTRA_OPEN_FRIENDS = "open_friends"
        private const val GROUP = "social"
        private const val ID_BASE = 20_000
    }
}

/**
 * Checks for friend activity while the app is closed, about every 15 minutes (Android's shortest repeat), and
 * notifies about anything new. The first check only sets the starting point, so nothing old is announced.
 */
@HiltWorker
class SocialCheckWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val feed: SocialFeed,
    private val prefs: UserPrefs,
    private val notifier: SocialNotifier,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!prefs.socialAlerts.first()) return Result.success()
        auth.awaitSession()
        if (auth.currentUserId == null) return Result.success()
        val since = prefs.socialSince.first()
        if (since == null) {
            prefs.setSocialSince(System.currentTimeMillis())
            return Result.success()
        }
        val rows = try {
            feed.since(since)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.retry()
        }
        if (rows.isEmpty()) return Result.success()
        notifier.show(SocialAlerts.compose(rows))
        prefs.setSocialSince(rows.maxOf { parseInstant(it.at) })
        return Result.success()
    }
}

@Singleton
class SocialCheckScheduler @Inject constructor(@ApplicationContext private val ctx: Context) {
    /** Every 15 minutes while online; keeps an existing schedule. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SocialCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val NAME = "social-check"
    }
}
