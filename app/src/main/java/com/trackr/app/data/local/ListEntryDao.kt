package com.trackr.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ListEntryDao {
    @Query("SELECT * FROM list_entries WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ListEntryEntity>>

    @Query("SELECT * FROM list_entries WHERE source = :source AND externalId = :id AND deleted = 0")
    fun observe(source: String, id: String): Flow<ListEntryEntity?>

    @Query("SELECT * FROM list_entries WHERE source = :source AND externalId = :id")
    suspend fun get(source: String, id: String): ListEntryEntity?

    @Query("SELECT * FROM list_entries")
    suspend fun getAllRaw(): List<ListEntryEntity>

    @Query("SELECT * FROM list_entries WHERE dirty = 1")
    suspend fun getDirty(): List<ListEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ListEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ListEntryEntity>)

    @Query("DELETE FROM list_entries WHERE source = :source AND externalId = :id")
    suspend fun hardDelete(source: String, id: String)

    @Query("DELETE FROM list_entries")
    suspend fun clear()
}
