package com.trackr.app.data

import com.trackr.app.data.local.AiringEntity
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.TitleMeta
import com.trackr.app.widget.UpNext
import com.trackr.app.widget.ProgressCell
import com.trackr.app.widget.UpNextRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals("Next Thu", label(at(10, 9))) // Mon Oct 5 + 10 days: Thursday of next week
        assertEquals("Oct 25", label(at(20, 9), dateOnly = true))
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

    // A show with four aired seasons of ten episodes; season 5 isn't out yet.
    private val fourSeasons = TitleMeta(emptyList(), listOf(10, 10, 10, 10), fetchedAt = 0, airedEpisodes = 40)
    private fun watching(progress: Int, meta: TitleMeta?, vararg airing: AiringEntity, total: Int? = null) =
        UpNext.build(
            listOf(entry("1", ListStatus.WATCHING, progress = progress, total = total)), airing.toList(),
            now.toInstant().toEpochMilli(), zone, Locale.US, meta = meta?.let { mapOf("tmdb:1" to it) }.orEmpty(),
        ).watching.single()

    @Test fun `caught up on a show waits for the next episode instead of offering one that isn't out`() {
        val row = watching(40, fourSeasons)
        assertEquals("Caught up", row.line)
        assertFalse(row.canIncrement)
    }

    @Test fun `caught up with the next episode announced says when it comes, next week included`() {
        val nextWed = airing("1", at(9, 9), episode = 1, precision = "DATE").copy(season = 5)
        val row = watching(40, fourSeasons, nextWed)
        assertEquals("S5 E1 · Next Wed", row.line)
        assertFalse(row.canIncrement)
        assertEquals("S5 E1 · Fri", watching(40, fourSeasons, nextWed.copy(airAt = at(4, 9))).line)
    }

    @Test fun `episodes still to watch read by season, with +1`() {
        val row = watching(15, fourSeasons)
        assertEquals("Next: S2 E6", row.line)
        assertTrue(row.canIncrement)
    }

    @Test fun `a total that counts announced episodes doesn't hide being caught up`() {
        // TMDB's episode total can include announced episodes: 50 here, with 40 out.
        assertEquals("Caught up", watching(40, fourSeasons, total = 50).line)
        // Once season 5 premieres it's listed with its episodes, and its first one is next.
        assertEquals("Next: S5 E1", watching(40, fourSeasons.copy(seasonEpisodes = listOf(10, 10, 10, 10, 10), airedEpisodes = 41), total = 50).line)
    }

    @Test fun `the airing schedule alone tells what's out, for anime numbered across the show`() {
        val next = airing("1", at(1, 18), episode = 8) // episode 8 airs tomorrow, so 7 are out
        val caughtUp = watching(7, null, next, total = 12)
        assertTrue(caughtUp.line.startsWith("Ep 8 · Tomorrow"))
        assertFalse(caughtUp.canIncrement)
        assertEquals("Next: Ep 6 of 12", watching(5, null, next, total = 12).line)
        // Once it has aired today, it counts as out.
        val aired = watching(7, null, next.copy(airAt = at(0, 8)), total = 12)
        assertEquals("Next: Ep 8 of 12", aired.line)
        assertTrue(aired.canIncrement)
    }

    @Test fun `aired count takes the freshest of the schedule and the stored details`() {
        val n = now.toInstant().toEpochMilli()
        val nextS5 = airing("1", at(3, 9), episode = 2).copy(season = 5)
        assertEquals(41, UpNext.airedEpisodes(fourSeasons, nextS5, listOf(10, 10, 10, 10, 8), n))
        assertEquals(40, UpNext.airedEpisodes(fourSeasons, null, listOf(10, 10, 10, 10), n))
        // A within-season episode number can't be placed without season sizes; the stored count still answers.
        assertEquals(40, UpNext.airedEpisodes(fourSeasons, nextS5, emptyList(), n))
        assertEquals(null, UpNext.airedEpisodes(null, null, emptyList(), n))
    }

    private fun build(vararg entries: ListEntry, meta: TitleMeta? = null, airing: List<AiringEntity> = emptyList()) =
        UpNext.build(
            entries.toList(), airing, now.toInstant().toEpochMilli(), zone, Locale.US,
            meta = meta?.let { mapOf("tmdb:1" to it) }.orEmpty(),
        )

    @Test fun `the hero is the most recent title with an episode to mark, not just the most recent title`() {
        val state = build(
            entry("caught", ListStatus.WATCHING, progress = 12, total = 12, updatedAt = 5),
            entry("next", ListStatus.WATCHING, progress = 3, total = 12, updatedAt = 1),
        )
        assertEquals("next", state.hero?.row?.externalId)
        assertEquals("E4", state.hero?.episode)
    }

    @Test fun `with nothing to mark the hero is the most recent title, and coming up leaves it out`() {
        val state = build(
            entry("a", ListStatus.WATCHING, progress = 4, total = null, updatedAt = 5),
            entry("c", ListStatus.PLAN_TO_WATCH),
            airing = listOf(airing("a", at(1, 9), episode = 5), airing("c", at(2, 9))),
        )
        assertEquals("a", state.hero?.row?.externalId)
        assertEquals("E5", state.hero?.episode) // the next episode, out of reach until tomorrow
        assertEquals(listOf("c"), state.coming.map { it.externalId })
    }

    @Test fun `with nothing being watched there is no hero and everything airing is coming up`() {
        val state = build(entry("1", ListStatus.PLAN_TO_WATCH), airing = listOf(airing("1", at(1, 20))))
        assertNull(state.hero)
        assertEquals(listOf("1"), state.coming.map { it.externalId })
    }

    @Test fun `the progress bar shows watched, out but unwatched, and still to come, one cell per episode`() {
        val cells = UpNext.progressCells(total = 28, watched = 13, aired = 14)
        assertEquals(28, cells.size)
        assertEquals(13, cells.count { it == ProgressCell.WATCHED })
        assertEquals(ProgressCell.TO_WATCH, cells[13])
        assertEquals(ProgressCell.UPCOMING, cells[14])
    }

    @Test fun `long shows are grouped into at most MAX_CELLS cells`() {
        val cells = UpNext.progressCells(total = 1000, watched = 500, aired = 500)
        assertEquals(UpNext.MAX_CELLS, cells.size)
        assertEquals(15, cells.count { it == ProgressCell.WATCHED }) // half of the 30 cells
        assertEquals(ProgressCell.WATCHED, cells.first())
        assertEquals(ProgressCell.UPCOMING, cells.last())
    }

    @Test fun `the hero shows the episode the +1 marks, the season, and how many are waiting`() {
        val hero = build(entry("1", ListStatus.WATCHING, progress = 15, total = 40), meta = fourSeasons).hero!!
        assertEquals("S2 E6", hero.episode)
        assertEquals("S2 · E5 · 25 to watch", hero.status)
        assertEquals(UpNext.MAX_CELLS, hero.cells.size)
    }

    @Test fun `the progress bar takes the aired count from the schedule`() {
        val hero = build(entry("1", ListStatus.WATCHING, progress = 13, total = 28), airing = listOf(airing("1", at(-1, 9), episode = 14))).hero!!
        assertEquals("E14", hero.episode)
        assertEquals("Ep 13 of 28 · 1 to watch", hero.status)
        assertEquals(ProgressCell.TO_WATCH, hero.cells[13])
        assertEquals(ProgressCell.UPCOMING, hero.cells[14])
    }

    @Test fun `a movie's hero says so and has no progress bar`() {
        val hero = build(entry("1", ListStatus.WATCHING, total = 1, type = MediaType.MOVIE)).hero!!
        assertEquals("Movie", hero.status)
        assertNull(hero.episode)
        assertTrue(hero.cells.isEmpty())
    }

    @Test fun `nothing tracked means an empty widget`() {
        assertTrue(UpNext.build(emptyList(), listOf(airing("1", at(0, 20))), now.toInstant().toEpochMilli(), zone, Locale.US).isEmpty)
    }
}
