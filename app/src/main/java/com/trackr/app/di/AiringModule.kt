package com.trackr.app.di

import com.trackr.app.data.airing.AiringNotifier
import com.trackr.app.data.airing.AiringScheduler
import com.trackr.app.data.airing.AlarmClient
import com.trackr.app.data.airing.AndroidAlarmClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiringModule {
    @Binds abstract fun alarmClient(impl: AndroidAlarmClient): AlarmClient

    companion object {
        @Provides @Singleton
        fun scheduler(client: AlarmClient, notifier: AiringNotifier) = AiringScheduler(client) { notifier.postAndMark(it) }
    }
}
