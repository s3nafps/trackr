package com.trackr.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.trackr.app.data.airing.AiringNotifier
import com.trackr.app.data.airing.AiringRefreshScheduler
import com.trackr.app.data.sync.SyncScheduler
import com.trackr.app.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TrackrApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var airingNotifier: AiringNotifier
    @Inject lateinit var airingRefresh: AiringRefreshScheduler
    @Inject lateinit var widgetUpdater: WidgetUpdater

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        airingNotifier.createChannel()
        syncScheduler.schedulePeriodic()
        airingRefresh.schedulePeriodic()
        widgetUpdater.start()
    }
}
