package com.trackr.app.domain.model

enum class ListStatus(val key: String, val label: String, val shortLabel: String) {
    WATCHING("watching", "Watching", "Watching"),
    COMPLETED("completed", "Completed", "Completed"),
    PLAN_TO_WATCH("plan_to_watch", "Plan to Watch", "Plan"),
    DROPPED("dropped", "Dropped", "Dropped");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: PLAN_TO_WATCH
    }
}

enum class MediaType(val key: String, val label: String) {
    MOVIE("movie", "Movie"), TV("tv", "TV Series"), ANIME("anime", "Anime");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: MOVIE
    }
}

enum class MediaSource(val key: String) {
    TMDB("tmdb"), ANILIST("anilist");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: TMDB
    }
}
