package com.trackr.app.data.local

import androidx.room.Entity

/** One "next drop" per tracked title. [airAt] is epoch millis; [precision] is "TIME" or "DATE". */
@Entity(tableName = "airing_schedule", primaryKeys = ["source", "externalId"])
data class AiringEntity(
    val source: String,
    val externalId: String,
    val mediaType: String,
    val title: String,
    val episode: Int?,
    val airAt: Long,
    val precision: String,
    val notified: Boolean = false,
)
