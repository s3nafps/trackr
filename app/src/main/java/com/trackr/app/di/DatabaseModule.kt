package com.trackr.app.di

import android.content.Context
import androidx.room.Room
import com.trackr.app.data.local.AiringDao
import com.trackr.app.data.local.ListEntryDao
import com.trackr.app.data.local.MIGRATION_1_2
import com.trackr.app.data.local.TrackrDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): TrackrDatabase =
        Room.databaseBuilder(ctx, TrackrDatabase::class.java, "trackr.db").addMigrations(MIGRATION_1_2).fallbackToDestructiveMigration().build()

    @Provides
    fun listDao(db: TrackrDatabase): ListEntryDao = db.listEntryDao()

    @Provides
    fun airingDao(db: TrackrDatabase): AiringDao = db.airingDao()
}
