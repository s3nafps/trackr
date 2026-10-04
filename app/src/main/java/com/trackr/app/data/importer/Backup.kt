package com.trackr.app.data.importer

import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
data class BackupEntry(
    val source: String,
    @SerialName("external_id") val externalId: String,
    @SerialName("media_type") val mediaType: String,
    val title: String,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    val status: String,
    val rating: Int? = null,
    val progress: Int = 0,
    @SerialName("total_episodes") val totalEpisodes: Int? = null,
    @SerialName("updated_at") val updatedAt: String,
    val notify: Boolean = false,
)

@Serializable
data class BackupFile(
    val app: String = APP,
    val version: Int = VERSION,
    @SerialName("exported_at") val exportedAt: String,
    val entries: List<BackupEntry>,
) {
    companion object {
        const val APP = "trackr"
        const val VERSION = 1
    }
}

/** Trackr's own export: a versioned JSON backup that imports back losslessly, plus a CSV for spreadsheets. */
object Backup {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun toJson(entries: List<ListEntry>, now: Instant = Instant.now()): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            exportedAt = now.toString(),
            entries = entries.map {
                BackupEntry(
                    it.source.key, it.externalId, it.mediaType.key, it.title, it.posterUrl, it.backdropUrl, it.status.key,
                    it.rating, it.progress, it.totalEpisodes, Instant.ofEpochMilli(it.updatedAt).toString(), it.notify,
                )
            },
        ),
    )

    fun fromJson(text: String): List<ListEntry> {
        val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }.getOrNull()
        if (file == null || file.app != BackupFile.APP) throw ImportException("That isn't a Trackr backup file.")
        if (file.version > BackupFile.VERSION) throw ImportException("This backup is from a newer version of Trackr. Update the app first.")
        return file.entries.mapNotNull { e ->
            // Unknown keys would otherwise fall back to a default and import as the wrong thing.
            val source = MediaSource.entries.firstOrNull { it.key == e.source } ?: return@mapNotNull null
            val type = MediaType.entries.firstOrNull { it.key == e.mediaType } ?: return@mapNotNull null
            val status = ListStatus.entries.firstOrNull { it.key == e.status } ?: return@mapNotNull null
            ListEntry(
                source, e.externalId, type, e.title, e.posterUrl, e.backdropUrl, status, e.rating, e.progress, e.totalEpisodes,
                runCatching { Instant.parse(e.updatedAt).toEpochMilli() }.getOrDefault(0L), e.notify,
            )
        }
    }

    fun toCsv(entries: List<ListEntry>): String = Csv.write(
        listOf("title", "media_type", "status", "rating", "progress", "total_episodes", "source", "external_id", "updated_at"),
        entries.map {
            listOf(
                it.title, it.mediaType.key, it.status.key, it.rating, it.progress, it.totalEpisodes, it.source.key, it.externalId,
                Instant.ofEpochMilli(it.updatedAt).toString(),
            )
        },
    )
}
