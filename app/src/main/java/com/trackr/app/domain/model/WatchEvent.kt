package com.trackr.app.domain.model

/**
 * One line of a title's watch history. [WATCHED] covers episodes [fromEpisode]..[toEpisode] (null for a movie);
 * [REWATCH] marks where a new watch-through started. [watchedAt] is when it was logged (epoch millis).
 */
data class WatchEvent(
    val id: String,
    val source: MediaSource,
    val externalId: String,
    val mediaType: MediaType,
    val title: String,
    val kind: Kind,
    val fromEpisode: Int?,
    val toEpisode: Int?,
    val watchedAt: Long,
) {
    val key: String get() = "${source.key}:$externalId"

    /** Episodes this event covers: the range for a show, 1 for a movie, 0 for a rewatch marker. */
    val episodes: Int
        get() = when {
            kind == Kind.REWATCH -> 0
            fromEpisode != null && toEpisode != null -> (toEpisode - fromEpisode + 1).coerceAtLeast(0)
            else -> 1
        }

    enum class Kind(val key: String) {
        WATCHED("watched"), REWATCH("rewatch");

        companion object {
            fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: WATCHED
        }
    }
}
