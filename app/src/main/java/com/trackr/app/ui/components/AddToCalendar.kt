package com.trackr.app.ui.components

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Opens the calendar app with a new event for an episode. The calendar app saves it, so no calendar permission is needed. */
object AddToCalendar {
    private const val HALF_HOUR_MS = 30 * 60_000L
    private const val DAY_MS = 24 * 60 * 60_000L

    /** [dateOnly] episodes (only the day is known) become all-day events. */
    fun add(
        context: Context, title: String, season: Int?, episode: Int?, airAtMillis: Long, dateOnly: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val label = listOfNotNull(season?.let { "Season $it" }, episode?.let { "Episode $it" }).joinToString(", ")
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, if (label.isEmpty()) title else "$title · $label")
        if (dateOnly) {
            // All-day events are stored as UTC midnight of the local day.
            val day = Instant.ofEpochMilli(airAtMillis).atZone(zone).toLocalDate()
            val begin = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + DAY_MS)
        } else {
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, airAtMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, airAtMillis + HALF_HOUR_MS)
        }
        // No calendar app installed is not worth a crash.
        runCatching { context.startActivity(intent) }
    }
}
