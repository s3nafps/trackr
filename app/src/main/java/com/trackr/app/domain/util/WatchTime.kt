package com.trackr.app.domain.util

import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.StatsCalculator
import com.trackr.app.domain.model.TitleMeta
import com.trackr.app.domain.model.WatchEvent

/** Time spent watching, from runtimes (minutes per episode, or a movie's length) kept in title_meta. */
object WatchTime {
    /**
     * Episodes (or movie viewings) watched on [entry], rewatches included: every finished watch-through plus the
     * current one. A rewatch counts from when it starts, so a movie being rewatched counts its first viewing.
     */
    fun viewings(entry: ListEntry, aired: Int? = null): Int {
        if (entry.mediaType == MediaType.MOVIE) return entry.rewatchCount + if (entry.status == ListStatus.COMPLETED) 1 else 0
        val perRun = entry.totalEpisodes ?: aired ?: entry.progress
        return entry.rewatchCount * perRun + entry.progress
    }

    /** A typical length when the source gives none (also what friends' profiles use). */
    fun typicalRuntime(type: MediaType): Int = when (type) {
        MediaType.MOVIE -> StatsCalculator.MOVIE_MINUTES
        MediaType.TV -> StatsCalculator.TV_EPISODE_MINUTES
        MediaType.ANIME -> StatsCalculator.ANIME_EPISODE_MINUTES
    }

    /** Minutes spent on [entry], with its real runtime when known. */
    fun minutes(entry: ListEntry, meta: TitleMeta?): Long =
        viewings(entry, meta?.airedEpisodes).toLong() * (meta?.runtimeMinutes ?: typicalRuntime(entry.mediaType))

    /** Minutes across a list; [estimated] counts watched titles whose runtime isn't known, so a typical one was used. */
    data class Total(val minutes: Long, val estimated: Int)

    fun total(entries: List<ListEntry>, meta: Map<String, TitleMeta>): Total {
        val watched = entries.filter { viewings(it, meta[it.key]?.airedEpisodes) > 0 }
        return Total(watched.sumOf { minutes(it, meta[it.key]) }, watched.count { meta[it.key]?.runtimeMinutes == null })
    }

    /** Minutes logged in the watch history between [from] (inclusive) and [until] (exclusive), epoch millis. */
    fun inPeriod(events: List<WatchEvent>, meta: Map<String, TitleMeta>, from: Long, until: Long): Long =
        events.filter { it.watchedAt >= from && it.watchedAt < until }
            .sumOf { e -> (meta[e.key]?.runtimeMinutes ?: typicalRuntime(e.mediaType)).toLong() * e.episodes }

    /** "45 min", "12 h", "1,204 h". */
    fun format(minutes: Long): String = when {
        minutes < 60 -> "$minutes min"
        else -> String.format(java.util.Locale.US, "%,d h", minutes / 60)
    }

    /** "≈ 13 days", or null under a day. */
    fun days(minutes: Long): String? = (minutes / (60 * 24)).takeIf { it >= 1 }?.let { if (it == 1L) "≈ 1 day" else "≈ $it days" }
}
