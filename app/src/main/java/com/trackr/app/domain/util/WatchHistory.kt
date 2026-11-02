package com.trackr.app.domain.util

import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.WatchEvent

/** What a change to a list entry adds to, or takes back from, its watch history. */
object WatchHistory {
    /** Oldest first; at the same instant a rewatch marker comes first, since what's logged with it belongs to it. */
    private val chronological = compareBy<WatchEvent>({ it.watchedAt }, { if (it.kind == WatchEvent.Kind.REWATCH) 0 else 1 })
    /**
     * History for a change from [previous] to [next]: the episodes newly marked (a range, so jumping from 3 to 10 is one
     * line), or for a movie the move to Completed. Changes that don't watch anything (rating, status) add nothing.
     */
    fun onWrite(previous: ListEntry?, next: ListEntry, now: Long, newId: () -> String): List<WatchEvent> {
        fun event(from: Int?, to: Int?) = WatchEvent(
            newId(), next.source, next.externalId, next.mediaType, next.title, WatchEvent.Kind.WATCHED, from, to, now,
        )
        return if (next.mediaType == MediaType.MOVIE) {
            if (next.status == ListStatus.COMPLETED && previous?.status != ListStatus.COMPLETED) listOf(event(null, null)) else emptyList()
        } else {
            val before = previous?.progress ?: 0
            if (next.progress > before) listOf(event(before + 1, next.progress)) else emptyList()
        }
    }

    /** Events to drop and events to shorten after a correction. */
    data class Trim(val remove: List<WatchEvent>, val shorten: List<WatchEvent>) {
        val isEmpty: Boolean get() = remove.isEmpty() && shorten.isEmpty()
    }

    /**
     * Takes back what a correction undoes, within the current watch-through only (after the newest rewatch marker):
     * episodes above the lowered progress, or a movie's last "watched" when it leaves Completed. [events] is the
     * title's history in any order.
     */
    fun onCorrection(previous: ListEntry, next: ListEntry, events: List<WatchEvent>): Trim {
        val current = events.sortedWith(chronological.reversed()).takeWhile { it.kind != WatchEvent.Kind.REWATCH }
        if (next.mediaType == MediaType.MOVIE) {
            val undone = previous.status == ListStatus.COMPLETED && next.status != ListStatus.COMPLETED
            val last = current.firstOrNull { it.kind == WatchEvent.Kind.WATCHED }
            return Trim(if (undone && last != null) listOf(last) else emptyList(), emptyList())
        }
        if (next.progress >= previous.progress) return Trim(emptyList(), emptyList())
        val keep = next.progress
        val remove = mutableListOf<WatchEvent>()
        val shorten = mutableListOf<WatchEvent>()
        for (e in current) {
            val from = e.fromEpisode ?: continue
            val to = e.toEpisode ?: continue
            when {
                from > keep -> remove += e
                to > keep -> shorten += e.copy(toEpisode = keep)
            }
        }
        return Trim(remove, shorten)
    }

    /** One row of the history list: [label] ("Episodes 5–7", "Watched", "Started again") and when, epoch millis. */
    data class Line(val label: String, val at: Long, val rewatch: Boolean = false)

    /**
     * The history as rows, newest first. Episodes logged one by one on the same day merge into one range
     * ("Episodes 5–7"), so a binge reads as one line.
     */
    fun lines(events: List<WatchEvent>, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<Line> {
        fun day(at: Long) = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val merged = mutableListOf<WatchEvent>()
        for (e in events.sortedWith(chronological)) {
            val last = merged.lastOrNull()
            val joins = last != null && e.kind == WatchEvent.Kind.WATCHED && last.kind == WatchEvent.Kind.WATCHED &&
                e.fromEpisode != null && last.toEpisode != null && e.fromEpisode == last.toEpisode + 1 &&
                day(e.watchedAt) == day(last.watchedAt)
            if (joins) merged[merged.lastIndex] = last!!.copy(toEpisode = e.toEpisode, watchedAt = e.watchedAt) else merged += e
        }
        return merged.asReversed().map { e ->
            val label = when {
                e.kind == WatchEvent.Kind.REWATCH -> "Started watching again"
                e.fromEpisode == null || e.toEpisode == null -> "Watched"
                e.fromEpisode == e.toEpisode -> "Episode ${e.toEpisode}"
                else -> "Episodes ${e.fromEpisode}–${e.toEpisode}"
            }
            Line(label, e.watchedAt, rewatch = e.kind == WatchEvent.Kind.REWATCH)
        }
    }
}
