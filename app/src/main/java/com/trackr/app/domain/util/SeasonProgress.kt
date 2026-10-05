package com.trackr.app.domain.util

import com.trackr.app.domain.model.SeasonInfo

/**
 * TV progress is stored as one episode count across the whole show; with the episode count of each season it reads as
 * "S2 · E5". Shows with a single season (and anime, where every season is its own title) keep "Ep 5 of 12".
 */
object SeasonProgress {
    /** Episode counts of the numbered seasons, in order; season 0 holds specials, which progress doesn't count. */
    fun regular(seasons: List<SeasonInfo>): List<Int> =
        seasons.filter { it.number > 0 && it.episodeCount > 0 }.sortedBy { it.number }.map { it.episodeCount }

    /**
     * The season (1-based) and episode within it of the last of [progress] watched episodes, or null before the first
     * one. Episodes past the known seasons (a season not listed yet) count on in the last one.
     */
    fun position(progress: Int, seasons: List<Int>): Pair<Int, Int>? {
        if (seasons.isEmpty() || progress <= 0) return null
        var left = progress
        seasons.forEachIndexed { i, eps ->
            if (left <= eps) return (i + 1) to left
            left -= eps
        }
        return seasons.size to seasons.last() + left
    }

    /** The overall episode count for [episode] of [season] (episode 0 = the season not started). */
    fun absolute(season: Int, episode: Int, seasons: List<Int>): Int = seasons.take((season - 1).coerceAtLeast(0)).sum() + episode

    /** Whether to show progress by season: only for shows with more than one. */
    fun bySeason(seasons: List<Int>) = seasons.size > 1

    /** "S2 · E5", "Not started", or without seasons "Ep 5 of 24" / "Ep 5". */
    fun label(progress: Int, total: Int?, seasons: List<Int>): String {
        if (bySeason(seasons)) {
            val (s, e) = position(progress, seasons) ?: return "Not started"
            return "S$s · E$e"
        }
        return total?.let { "Ep $progress of $it" } ?: "Ep $progress"
    }

    /** Label for an upcoming episode: "S3 · E2", or "Episode 12" when the source numbers episodes across the show. */
    fun upcoming(season: Int?, episode: Int?): String? = when {
        episode == null -> null
        season != null -> "S$season · E$episode"
        else -> "Episode $episode"
    }
}
