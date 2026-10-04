package com.trackr.app.data

import com.trackr.app.data.local.AiringEntity
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.widget.UpNext
import com.trackr.app.widget.UpNextRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

class UpNextTest {
    private val zone = ZoneOffset.UTC
    private val now = ZonedDateTime.of(2026, 10, 5, 12, 0, 0, 0, zone)
    private fun at(days: Long, hour: Int) = now.plusDays(days).withHour(hour).toInstant().toEpochMilli()

    private fun entry(id: String, status: ListStatus, progress: Int = 0, total: Int? = 12, updatedAt: Long = 0, type: MediaType = MediaType.TV) =
        ListEntry(MediaSource.TMDB, id, type, "T$id", null, null, status, null, progress, total, updatedAt)

    private fun airing(id: String, airAt: Long, episode: Int? = 5, precision: String = "TIME") =
        AiringEntity("tmdb", id, "tv", "T$id", episode, airAt, precision)

    private fun label(airAt: Long, dateOnly: Boolean = false) = UpNext.whenLabel(airAt, dateOnly, now.toInstant().toEpochMilli(), zone, Locale.US)

    @Test fun `labels say today, tomorrow, a weekday or a date`() {
        assertTrue(label(at(0, 21)).let { it.startsWith("Today ") && it.contains("9:00") && it.endsWith("PM") })
        assertEquals("Out today", label(at(0, 8)))
        assertEquals("Tomorrow", label(at(1, 9), dateOnly = true))
        val in3 = now.plusDays(3)
        assertTrue(label(at(3, 18)).startsWith(in3.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US) + " "))
        assertEquals("Oct 15", label(at(10, 9), dateOnly = true))
    }

    @Test fun `airing shows tracked titles from today through the next week, soonest first and capped`() {
        val entries = (1..9).map { entry("$it", ListStatus.WATCHING) }
        val rows = UpNext.build(
            entries,
            listOf(
                airing("1", at(2, 20)),
                airing("2", at(0, 8), episode = 3), // earlier today
                airing("3", at(-1, 20)), // yesterday
                airing("4", at(7, 1)), // a week out
                airing("5", at(1, 9), episode = null, precision = "DATE"),
                airing("untracked", at(0, 22)),
                airing("6", at(3, 20)),
                airing("7", at(4, 20)),
            ),
            now.toInstant().toEpochMilli(), zone, Locale.US,
        ).airing
        assertEquals(listOf("2", "5", "1", "6"), rows.map { it.externalId })
        assertEquals(UpNext.MAX_AIRING, rows.size)
        assertEquals("Ep 3 · Out today", rows[0].line)
        assertEquals("Tomorrow", rows[1].line)
    }

    @Test fun `continue watching lists watching titles, most recent first, with the next episode`() {
        val rows = UpNext.build(
            listOf(
                entry("old", ListStatus.WATCHING, progress = 4, updatedAt = 1),
                entry("new", ListStatus.WATCHING, progress = 0, total = null, updatedAt = 3),
                entry("done", ListStatus.WATCHING, progress = 12, updatedAt = 2),
                entry("film", ListStatus.WATCHING, total = 1, updatedAt = 4, type = MediaType.MOVIE),
                entry("plan", ListStatus.PLAN_TO_WATCH, updatedAt = 9),
                entry("finished", ListStatus.COMPLETED, updatedAt = 9),
            ),
            emptyList(), now.toInstant().toEpochMilli(), zone, Locale.US,
        ).watching
        assertEquals(
            listOf(
                UpNextRow("tmdb", "film", "movie", "Tfilm", "Movie", canIncrement = false),
                UpNextRow("tmdb", "new", "tv", "Tnew", "Next: Ep 1", canIncrement = true),
                UpNextRow("tmdb", "done", "tv", "Tdone", "Ep 12 of 12", canIncrement = false),
                UpNextRow("tmdb", "old", "tv", "Told", "Next: Ep 5 of 12", canIncrement = true),
            ),
            rows,
        )
    }

    @Test fun `nothing tracked means an empty widget`() {
        assertTrue(UpNext.build(emptyList(), listOf(airing("1", at(0, 20))), now.toInstant().toEpochMilli(), zone, Locale.US).isEmpty)
    }
}
