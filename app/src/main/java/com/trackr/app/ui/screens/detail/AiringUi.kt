package com.trackr.app.ui.screens.detail

import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.trackr.app.domain.util.SeasonProgress

enum class BellState { Hidden, Off, On }

/** One-line "what drops next" summary for the detail screen, null without airing data. */
fun airingLine(
    item: MediaItem,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String? {
    val atMillis = item.airingAtEpoch?.times(1000) ?: return null
    val at = Instant.ofEpochMilli(atMillis).atZone(zone)
    fun fmt(pattern: String) = DateTimeFormatter.ofPattern(pattern, locale).format(at)

    if (item.type == MediaType.MOVIE) return "Releases ${fmt("MMM d")}"

    val ep = SeasonProgress.upcoming(item.airingSeason, item.airingEpisode)?.let { "$it · " }.orEmpty()
    val `when` = if (item.airingDateOnly) {
        fmt("EEE, MMM d")
    } else {
        val minutes = (atMillis - nowMillis) / 60_000
        val remaining = when {
            minutes <= 0 -> ""
            minutes >= 24 * 60 -> " (in ${minutes / (24 * 60)}d ${minutes % (24 * 60) / 60}h)"
            minutes >= 60 -> " (in ${minutes / 60}h ${minutes % 60}m)"
            else -> " (in ${minutes}m)"
        }
        fmt("EEE HH:mm") + remaining
    }
    return "Next episode: $ep$`when`"
}
