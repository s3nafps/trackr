package com.trackr.app.domain.util

import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The next episode (or, for a movie, the release) of a title in the list. [exactTime] is false for date-only sources. */
data class UpcomingEpisode(
    val source: MediaSource,
    val externalId: String,
    val type: MediaType,
    val title: String,
    val posterUrl: String?,
    val season: Int?,
    val episode: Int?,
    val airAt: Long,
    val exactTime: Boolean,
)

data class CalendarDay(val date: LocalDate, val label: String, val episodes: List<UpcomingEpisode>)

object EpisodeCalendar {
    /** Upcoming episodes from today on, one section per day in order; within a day, by air time. */
    fun days(episodes: List<UpcomingEpisode>, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): List<CalendarDay> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return episodes
            .map { it to Instant.ofEpochMilli(it.airAt).atZone(zone).toLocalDate() }
            .filter { (_, day) -> !day.isBefore(today) }
            .groupBy({ it.second }, { it.first })
            .toSortedMap()
            .map { (day, list) -> CalendarDay(day, dayLabel(day, today, locale), list.sortedWith(compareBy({ it.airAt }, { it.title }))) }
    }

    fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "EEEE, d MMMM" else "EEEE, d MMMM yyyy", locale))
    }
}
