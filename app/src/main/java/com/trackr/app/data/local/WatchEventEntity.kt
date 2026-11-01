package com.trackr.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** See WatchEvent. [createdAt] is the server's insert time (null until pulled back); it is the pull cursor. */
@Entity(tableName = "watch_events", indices = [Index("source", "externalId"), Index("watchedAt")])
data class WatchEventEntity(
    @PrimaryKey val id: String,
    val source: String,
    val externalId: String,
    val mediaType: String,
    val title: String,
    val kind: String,
    val fromEpisode: Int?,
    val toEpisode: Int?,
    val watchedAt: Long,
    /** On the server. */
    val synced: Boolean = false,
    /** Tombstone: removed here, the server delete is still owed. */
    val deleted: Boolean = false,
    val createdAt: Long? = null,
)

@Dao
interface WatchEventDao {
    @Query("SELECT * FROM watch_events WHERE deleted = 0 ORDER BY watchedAt DESC")
    fun observeAll(): Flow<List<WatchEventEntity>>

    @Query("SELECT * FROM watch_events WHERE source = :source AND externalId = :id AND deleted = 0 ORDER BY watchedAt DESC")
    fun observeFor(source: String, id: String): Flow<List<WatchEventEntity>>

    @Query("SELECT * FROM watch_events WHERE source = :source AND externalId = :id AND deleted = 0 ORDER BY watchedAt DESC")
    suspend fun getFor(source: String, id: String): List<WatchEventEntity>

    @Query("SELECT * FROM watch_events WHERE id = :id")
    suspend fun get(id: String): WatchEventEntity?

    @Query("SELECT * FROM watch_events WHERE synced = 0 AND deleted = 0")
    suspend fun getUnsynced(): List<WatchEventEntity>

    @Query("SELECT * FROM watch_events WHERE deleted = 1")
    suspend fun getDeleted(): List<WatchEventEntity>

    @Query("SELECT MAX(createdAt) FROM watch_events")
    suspend fun latestCreatedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entities: List<WatchEventEntity>)

    @Query("UPDATE watch_events SET synced = 1 WHERE id IN (:ids) AND deleted = 0")
    suspend fun markSynced(ids: List<String>)

    /** A pulled row this device already has: record the server time, unless it was removed here meanwhile. */
    @Query("UPDATE watch_events SET synced = 1, createdAt = :createdAt WHERE id = :id AND deleted = 0")
    suspend fun markPulled(id: String, createdAt: Long)

    @Query("DELETE FROM watch_events WHERE id IN (:ids)")
    suspend fun hardDelete(ids: List<String>)

    @Query("DELETE FROM watch_events")
    suspend fun clear()
}
