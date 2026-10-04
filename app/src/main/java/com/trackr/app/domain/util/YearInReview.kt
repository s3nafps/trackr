package com.trackr.app.domain.util

import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.StatsCalculator
import java.time.Instant
import java.time.ZoneId

/** What a year looked like: titles completed that year, by type, with hours, ratings and a month-by-month count. */
data class YearInReview(
    val year: Int,
    val completed: Int,
    val movies: Int,
    val shows: Int,
    val anime: Int,
    val episodes: Int,
    val hours: Int,
    val averageRating: Double?,
    val topRated: List<ListEntry>,
    /** Titles completed per month, January first. */
    val perMonth: List<Int>,
    /** Some titles were completed before dates were recorded, so their last edit stands in for the date. */
    val usesEstimatedDates: Boolean,
) {
    val isEmpty: Boolean get() = completed == 0

    /** 1–12, or null for an empty year; ties go to the earlier month. */
    val busiestMonth: Int? get() = perMonth.indices.maxByOrNull { perMonth[it] }?.takeIf { perMonth[it] > 0 }?.plus(1)
}

object YearInReviewCalculator {
    const val TOP_RATED = 5

    /** When [e] was completed: its recorded date, its last edit for rows from before dates were recorded, else null. */
    fun completedAt(e: ListEntry): Long? = when {
        e.status != ListStatus.COMPLETED -> null
        e.completedAt == null -> e.updatedAt
        e.completedAt == ListEntry.COMPLETED_DATE_UNKNOWN -> null
        else -> e.completedAt
    }

    /** Years with at least one completed title, newest first. */
    fun years(entries: List<ListEntry>, zone: ZoneId = ZoneId.systemDefault()): List<Int> =
        entries.mapNotNull { e -> completedAt(e)?.let { Instant.ofEpochMilli(it).atZone(zone).year } }.distinct().sortedDescending()

    fun compute(entries: List<ListEntry>, year: Int, zone: ZoneId = ZoneId.systemDefault()): YearInReview {
        val done = entries.mapNotNull { e -> completedAt(e)?.let { e to Instant.ofEpochMilli(it).atZone(zone) } }
            .filter { (_, at) -> at.year == year }
        val movies = done.count { it.first.mediaType == MediaType.MOVIE }
        val tvEpisodes = done.filter { it.first.mediaType == MediaType.TV }.sumOf { it.first.progress }
        val animeEpisodes = done.filter { it.first.mediaType == MediaType.ANIME }.sumOf { it.first.progress }
        val minutes = movies * StatsCalculator.MOVIE_MINUTES + tvEpisodes * StatsCalculator.TV_EPISODE_MINUTES +
            animeEpisodes * StatsCalculator.ANIME_EPISODE_MINUTES
        val ratings = done.mapNotNull { it.first.rating }
        return YearInReview(
            year = year,
            completed = done.size,
            movies = movies,
            shows = done.count { it.first.mediaType == MediaType.TV },
            anime = done.count { it.first.mediaType == MediaType.ANIME },
            episodes = tvEpisodes + animeEpisodes,
            hours = minutes / 60,
            averageRating = ratings.takeIf { it.isNotEmpty() }?.average(),
            topRated = done.filter { it.first.rating != null }
                .sortedWith(compareByDescending<Pair<ListEntry, java.time.ZonedDateTime>> { it.first.rating }.thenBy { it.second })
                .take(TOP_RATED).map { it.first },
            perMonth = (1..12).map { m -> done.count { it.second.monthValue == m } },
            usesEstimatedDates = done.any { it.first.completedAt == null },
        )
    }
}
