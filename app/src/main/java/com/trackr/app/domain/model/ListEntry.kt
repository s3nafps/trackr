package com.trackr.app.domain.model

data class ListEntry(
    val source: MediaSource,
    val externalId: String,
    val mediaType: MediaType,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val status: ListStatus,
    val rating: Int?,
    val progress: Int,
    val totalEpisodes: Int?,
    val updatedAt: Long,
    val notify: Boolean = false,
) {
    val key: String get() = "${source.key}:$externalId"
    val fraction: Float
        get() = if (totalEpisodes != null && totalEpisodes > 0) (progress.toFloat() / totalEpisodes).coerceIn(0f, 1f) else 0f
}
