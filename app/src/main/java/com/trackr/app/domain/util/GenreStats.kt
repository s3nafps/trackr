package com.trackr.app.domain.util

import com.trackr.app.domain.model.Genre
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.TitleMeta

data class GenreShare(val genre: String, val titles: Int)

/** The genres of what the user has watched; [counted] of the [watched] titles have genres known so far. */
data class GenreBreakdown(val top: List<GenreShare> = emptyList(), val counted: Int = 0, val watched: Int = 0)

object GenreStats {
    /**
     * Genres across the titles the user is watching or has completed, most common first. TMDB's combined TV genres
     * count for each part ("Sci-Fi & Fantasy" is Sci-Fi and Fantasy), and the same genre from TMDB and AniList counts
     * once per title.
     */
    fun breakdown(entries: List<ListEntry>, meta: Map<String, TitleMeta>, limit: Int = 8): GenreBreakdown {
        val watched = entries.filter { it.status == ListStatus.WATCHING || it.status == ListStatus.COMPLETED }
        val known = watched.mapNotNull { e -> meta[e.key]?.genres?.takeIf { it.isNotEmpty() } }
        val counts = HashMap<String, Int>()
        known.forEach { genres -> genres.flatMap(::canonical).toSet().forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        val top = counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit).map { GenreShare(it.key, it.value) }
        return GenreBreakdown(top, counted = known.size, watched = watched.size)
    }

    /** Our name(s) for a source's genre name; names we don't map (e.g. "History", "Mecha") are kept as they are. */
    fun canonical(name: String): List<String> = Genre.entries.filter { it.matchesName(name) }.map { it.label }.ifEmpty { listOf(name) }
}
