package com.trackr.app.data.mapper

import com.trackr.app.data.local.ListEntryEntity
import com.trackr.app.data.remote.supabase.ListEntryDto
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import java.time.Instant
import java.time.OffsetDateTime

object ListEntryMapper {
    fun ListEntryEntity.toDomain() = ListEntry(
        MediaSource.fromKey(source), externalId, MediaType.fromKey(mediaType), title, posterUrl, backdropUrl,
        ListStatus.fromKey(status), rating, progress, totalEpisodes, updatedAt,
    )

    fun ListEntry.toEntity(dirty: Boolean, deleted: Boolean = false) = ListEntryEntity(
        source.key, externalId, mediaType.key, title, posterUrl, backdropUrl, status.key, rating, progress,
        totalEpisodes, updatedAt, dirty, deleted,
    )

    fun ListEntryDto.toEntity() = ListEntryEntity(
        source, externalId, mediaType, title, posterUrl, backdropUrl, status, rating, progress, totalEpisodes,
        parseInstant(updatedAt), dirty = false, deleted = false,
    )

    fun ListEntryEntity.toDto(userId: String) = ListEntryDto(
        userId = userId, source = source, externalId = externalId, mediaType = mediaType, title = title,
        posterUrl = posterUrl, backdropUrl = backdropUrl, status = status, rating = rating, progress = progress,
        totalEpisodes = totalEpisodes, updatedAt = Instant.ofEpochMilli(updatedAt).toString(),
    )

    fun parseInstant(s: String): Long = runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        .getOrElse { runCatching { Instant.parse(s).toEpochMilli() }.getOrDefault(0L) }
}
