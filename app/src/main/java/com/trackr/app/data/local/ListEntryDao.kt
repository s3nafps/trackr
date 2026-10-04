package com.trackr.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("DELETE FROM list_entries")
    suspend fun clear()

    // Conditional writes for the syncer: each applies only if the row still has the updatedAt the syncer read,
    // so a local edit made while a sync is in flight is never overwritten or lost.

    @Query("UPDATE list_entries SET dirty = 0 WHERE source = :source AND externalId = :id AND updatedAt = :updatedAt")
    suspend fun markCleanIfUnchanged(source: String, id: String, updatedAt: Long)

    @Query("DELETE FROM list_entries WHERE source = :source AND externalId = :id AND updatedAt = :updatedAt")
    suspend fun hardDeleteIfUnchanged(source: String, id: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entities: List<ListEntryEntity>)

    @Transaction
    suspend fun replaceIfUnchanged(entity: ListEntryEntity, updatedAt: Long) {
        if (get(entity.source, entity.externalId)?.updatedAt == updatedAt) upsert(entity)
    }
}
