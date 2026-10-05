package com.trackr.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** See TitleMeta. Lists are stored as text: genres separated by U+001F, season episode counts by commas. */
@Entity(tableName = "title_meta", primaryKeys = ["source", "externalId"])
data class TitleMetaEntity(
    val source: String,
    val externalId: String,
    val genres: String,
    val seasonEpisodes: String,
    val fetchedAt: Long,
)

@Dao
interface TitleMetaDao {
    @Query("SELECT * FROM title_meta")
    fun observeAll(): Flow<List<TitleMetaEntity>>

    @Query("SELECT * FROM title_meta")
    suspend fun getAll(): List<TitleMetaEntity>

    @Query("SELECT * FROM title_meta WHERE source = :source AND externalId = :id")
    suspend fun get(source: String, id: String): TitleMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TitleMetaEntity)

    @Query("DELETE FROM title_meta")
    suspend fun clear()
}
