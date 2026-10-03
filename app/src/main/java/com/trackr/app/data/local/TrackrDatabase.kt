package com.trackr.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ListEntryEntity::class], version = 1, exportSchema = false)
abstract class TrackrDatabase : RoomDatabase() {
    abstract fun listEntryDao(): ListEntryDao
}
