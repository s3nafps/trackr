package com.trackr.app.domain.model

/**
 * Genres offered as filters. Each source names them differently: TMDB has separate movie and TV genre ids (TV merges
 * some, e.g. "Sci-Fi & Fantasy"), and AniList uses names. A null id or name means that source has no such genre, so the
 * filter leaves it out. [aliases] are the other names a loaded title may carry for the same genre.
 */
enum class Genre(val label: String, val tmdbMovie: Int?, val tmdbTv: Int?, val anilist: String?, vararg val aliases: String) {
    ACTION("Action", 28, 10759, "Action", "Action & Adventure"),
    ADVENTURE("Adventure", 12, 10759, "Adventure", "Action & Adventure"),
    COMEDY("Comedy", 35, 35, "Comedy"),
    DRAMA("Drama", 18, 18, "Drama"),
    FANTASY("Fantasy", 14, 10765, "Fantasy", "Sci-Fi & Fantasy"),
    SCI_FI("Sci-Fi", 878, 10765, "Sci-Fi", "Sci-Fi & Fantasy"),
    ROMANCE("Romance", 10749, null, "Romance"),
    MYSTERY("Mystery", 9648, 9648, "Mystery"),
    THRILLER("Thriller", 53, null, "Thriller"),
    HORROR("Horror", 27, null, "Horror"),
    CRIME("Crime", 80, 80, null),
    ANIMATION("Animation", 16, 16, null),
    FAMILY("Family", 10751, 10751, null),
    DOCUMENTARY("Documentary", 99, 99, null),
    SLICE_OF_LIFE("Slice of Life", null, null, "Slice of Life"),
    SPORTS("Sports", null, null, "Sports");

    /** Whether any source behind [type] (all of them when null) can filter by this genre. */
    fun appliesTo(type: MediaType?): Boolean = when (type) {
        null -> tmdbMovie != null || tmdbTv != null || anilist != null
        MediaType.MOVIE -> tmdbMovie != null
        MediaType.TV -> tmdbTv != null
        MediaType.ANIME -> anilist != null
    }

    /** Whether a title that is already loaded (a search result, say) is in this genre, going by its genre names. */
    fun matches(item: MediaItem): Boolean = item.genres.any { it == label || it == anilist || it in aliases }

    companion object {
        fun forType(type: MediaType?): List<Genre> = entries.filter { it.appliesTo(type) }
    }
}
