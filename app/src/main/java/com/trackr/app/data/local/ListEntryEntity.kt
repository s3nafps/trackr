package com.trackr.app.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "list_entries", primaryKeys = ["source", "externalId"], indices = [Index("status"), Index("dirty")])
data class ListEntryEntity(
    val source: String,
    val externalId: String,
    val mediaType: String,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val status: String,
    val rating: Int?,
    val progress: Int,
    val totalEpisodes: Int?,
    /** Epoch millis; the last-write-wins clock. */
    val updatedAt: Long,
    /** Local change not yet pushed to Supabase. */
    val dirty: Boolean = false,
    /** Tombstone: removed locally, delete must still be pushed. */
    val deleted: Boolean = false,
    /** Plan-to-Watch opt-in for airing notifications. */
    val notify: Boolean = false,
)
