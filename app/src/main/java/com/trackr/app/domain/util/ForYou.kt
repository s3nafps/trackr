package com.trackr.app.domain.util

import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem

/** The Home "For You" row: recommendations for the titles the user liked, merged into one list. */
object ForYou {
    /** A rating at or above this counts as liked. */
    const val LIKED_RATING = 7

    /**
     * The titles to base recommendations on, best first: those rated [LIKED_RATING]+ (highest, then most recently
     * updated), topped up with unrated Completed and Watching titles. Low-rated, dropped and planned titles say
     * nothing about taste, so they are never used.
     */
    fun seeds(entries: List<ListEntry>, max: Int = 5): List<ListEntry> {
        val liked = entries.filter { (it.rating ?: 0) >= LIKED_RATING && it.status != ListStatus.DROPPED }
            .sortedWith(compareByDescending<ListEntry> { it.rating }.thenByDescending { it.updatedAt })
        val watched = entries.filter { it.rating == null && (it.status == ListStatus.COMPLETED || it.status == ListStatus.WATCHING) }
            .sortedByDescending { it.updatedAt }
        return (liked + watched).take(max)
    }

    /**
     * Merges each seed's recommendations (in seed order): titles recommended for more seeds come first, then by the best
     * position any seed gave them, so the top picks of every seed take turns. Titles in [listed] are left out.
     */
    fun rank(recommendations: List<List<MediaItem>>, listed: Set<String>, limit: Int = 30): List<MediaItem> {
        class Score(val item: MediaItem, var hits: Int, var best: Int)
        val scores = LinkedHashMap<String, Score>()
        recommendations.forEach { recs ->
            recs.distinctBy { it.key }.forEachIndexed { i, item ->
                if (item.key in listed) return@forEachIndexed
                val s = scores.getOrPut(item.key) { Score(item, 0, i) }
                s.hits++
                s.best = minOf(s.best, i)
            }
        }
        return scores.values.sortedWith(compareByDescending<Score> { it.hits }.thenBy { it.best }).take(limit).map { it.item }
    }
}
