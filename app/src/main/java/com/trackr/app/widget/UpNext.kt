package com.trackr.app.widget

import com.trackr.app.data.local.AiringEntity
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One widget row; [source]/[externalId]/[mediaType] open the title, [canIncrement] shows the +1 button. */
data class UpNextRow(
    val source: String,
    val externalId: String,
    val mediaType: String,
    val title: String,
    val line: String,
    val canIncrement: Boolean = false,
)

data class UpNextState(val airing: List<UpNextRow>, val watching: List<UpNextRow>) {
    val isEmpty: Boolean get() = airing.isEmpty() && watching.isEmpty()
}

/** What the "Up next" widget shows: episodes dropping this week, then what you're in the middle of. */
object UpNext {
    const val MAX_AIRING = 4
    const val MAX_WATCHING = 6
    private const val WINDOW_DAYS = 7L

    fun build(
        entries: List<ListEntry>,
        airing: List<AiringEntity>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): UpNextState {
        val startOfToday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val windowEnd = Instant.ofEpochMilli(startOfToday).atZone(zone).plusDays(WINDOW_DAYS).toInstant().toEpochMilli()
        val tracked = entries.associateBy { it.source.key to it.externalId }
        val airingRows = airing
            // A row can outlive its entry briefly (removed before the next airing refresh).
            .filter { it.airAt in startOfToday until windowEnd && (it.source to it.externalId) in tracked }
            .sortedBy { it.airAt }
            .take(MAX_AIRING)
            .map { a ->
                val label = whenLabel(a.airAt, a.precision == "DATE", now, zone, locale)
                UpNextRow(a.source, a.externalId, a.mediaType, a.title, listOfNotNull(a.episode?.let { e -> a.season?.let { "S$it E$e" } ?: "Ep $e" }, label).joinToString(" · "))
            }
        val watchingRows = entries
            .filter { it.status == ListStatus.WATCHING }
            .sortedByDescending { it.updatedAt }
            .take(MAX_WATCHING)
            .map { e ->
                val total = e.totalEpisodes
                val finished = total != null && e.progress >= total
                val line = when {
                    e.mediaType == MediaType.MOVIE -> "Movie"
                    finished -> "Ep ${e.progress} of $total"
                    total != null -> "Next: Ep ${e.progress + 1} of $total"
                    else -> "Next: Ep ${e.progress + 1}"
                }
                UpNextRow(e.source.key, e.externalId, e.mediaType.key, e.title, line, canIncrement = e.mediaType != MediaType.MOVIE && !finished)
            }
        return UpNextState(airingRows, watchingRows)
    }

    /** "Today 9:00 PM", "Tomorrow", "Fri 9:00 PM", "Oct 12"; date-only rows (TMDB) drop the time. */
    fun whenLabel(airAt: Long, dateOnly: Boolean, now: Long, zone: ZoneId, locale: Locale): String {
        val at = Instant.ofEpochMilli(airAt).atZone(zone)
        val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), at.toLocalDate())
        if (days == 0L && airAt <= now) return "Out today"
        val day = when (days) {
            0L -> "Today"
            1L -> "Tomorrow"
            in 2L..6L -> at.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            else -> at.format(DateTimeFormatter.ofPattern("MMM d", locale))
        }
        return if (dateOnly) day else "$day ${at.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))}"
    }
}
