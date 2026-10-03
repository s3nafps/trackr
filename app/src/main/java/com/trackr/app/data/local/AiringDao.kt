package com.trackr.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiringDao {
    @Query("SELECT * FROM airing_schedule")
    suspend fun getAll(): List<AiringEntity>

    @Query("SELECT * FROM airing_schedule ORDER BY airAt")
    fun observeAll(): Flow<List<AiringEntity>>

    @Query("SELECT * FROM airing_schedule WHERE source = :source AND externalId = :id")
    suspend fun get(source: String, id: String): AiringEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: AiringEntity)

    @Query("DELETE FROM airing_schedule WHERE source = :source AND externalId = :id")
    suspend fun delete(source: String, id: String)

    @Query("UPDATE airing_schedule SET notified = 1 WHERE source = :source AND externalId = :id")
    suspend fun markNotified(source: String, id: String)

    @Query("DELETE FROM airing_schedule")
    suspend fun clear()
}
