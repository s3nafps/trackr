package com.trackr.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ListEntryEntity::class, AiringEntity::class, TitleMetaEntity::class], version = 6, exportSchema = false)
abstract class TrackrDatabase : RoomDatabase() {
    abstract fun listEntryDao(): ListEntryDao
    abstract fun airingDao(): AiringDao
    abstract fun titleMetaDao(): TitleMetaDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE list_entries ADD COLUMN notify INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS airing_schedule (source TEXT NOT NULL, externalId TEXT NOT NULL, " +
                "mediaType TEXT NOT NULL, title TEXT NOT NULL, episode INTEGER, airAt INTEGER NOT NULL, " +
                "precision TEXT NOT NULL, notified INTEGER NOT NULL, PRIMARY KEY(source, externalId))",
        )
    }
}

/** completedAt for Year in review; existing rows stay null (completed before it was recorded). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE list_entries ADD COLUMN completedAt INTEGER")
    }
}

/** Genres and season sizes of listed titles, kept per device (filled in by MetaBackfillWorker). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS title_meta (source TEXT NOT NULL, externalId TEXT NOT NULL, genres TEXT NOT NULL, " +
                "seasonEpisodes TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(source, externalId))",
        )
    }
}

/** The season of the next TV episode, so it reads "S3 · E2" (TMDB numbers episodes within seasons). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE airing_schedule ADD COLUMN season INTEGER")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE title_meta ADD COLUMN airedEpisodes INTEGER")
    }
}
