package com.trackr.app.di

import com.trackr.app.data.airing.AlarmClient
import com.trackr.app.data.airing.AndroidAlarmClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AiringModule {
    @Binds abstract fun alarmClient(impl: AndroidAlarmClient): AlarmClient
}
