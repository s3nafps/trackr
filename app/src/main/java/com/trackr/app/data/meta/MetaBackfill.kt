package com.trackr.app.data.meta

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trackr.app.data.local.ListEntryDao
import com.trackr.app.data.repository.ListRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Fills in genres and seasons for listed titles (see [TitleMetaRepository.backfill]); queues itself again while more are due. */
@HiltWorker
class MetaBackfillWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val lists: ListRepository,
    private val meta: TitleMetaRepository,
    private val scheduler: MetaBackfillScheduler,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val remaining = meta.backfill(lists.entries.first())
        if (remaining > 0) scheduler.runAgain()
        return Result.success()
    }
}

@Singleton
class MetaBackfillScheduler @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val request
        get() = OneTimeWorkRequestBuilder<MetaBackfillWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

    /** A run that's already queued or running will see the new titles too. */
    fun runSoon() {
        WorkManager.getInstance(ctx).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** Called by the running worker: queues the next batch after it. */
    fun runAgain() {
        WorkManager.getInstance(ctx).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val NAME = "title-meta-backfill"
    }
}

/**
 * Started with the app: whenever titles are added to the list (or on launch), the backfill runs once things settle.
 * Watches the DAO rather than [ListRepository], so starting the app doesn't build the Supabase client.
 */
@Singleton
class MetaKeeper @Inject constructor(private val listDao: ListEntryDao, private val scheduler: MetaBackfillScheduler) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            listDao.observeAll().map { rows -> rows.map { it.source to it.externalId }.toSet() }
                .distinctUntilChanged().debounce(5_000).collect { scheduler.runSoon() }
        }
    }
}
