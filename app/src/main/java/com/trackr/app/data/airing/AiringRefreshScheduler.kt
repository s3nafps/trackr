package com.trackr.app.data.airing

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiringRefreshScheduler @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun refreshNow() {
        val req = OneTimeWorkRequestBuilder<AiringRefreshWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("airing-refresh-now", ExistingWorkPolicy.REPLACE, req)
    }

    fun schedulePeriodic() {
        val req = PeriodicWorkRequestBuilder<AiringRefreshWorker>(6, TimeUnit.HOURS).setConstraints(constraints).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("airing-refresh-periodic", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
