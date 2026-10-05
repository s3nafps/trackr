package com.trackr.app.domain

import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.SeasonInfo
import com.trackr.app.domain.util.EpisodeCalendar
import com.trackr.app.domain.util.SeasonProgress
import com.trackr.app.domain.util.UpcomingEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class SeasonsAndCalendarTest {
    private val seasons = listOf(10, 8, 6)

    @Test fun `overall progress reads as season and episode`() {
        assertNull(SeasonProgress.position(0, seasons))
        assertEquals(1 to 1, SeasonProgress.position(1, seasons))
        assertEquals(1 to 10, SeasonProgress.position(10, seasons))
        assertEquals(2 to 1, SeasonProgress.position(11, seasons))
        assertEquals(3 to 6, SeasonProgress.position(24, seasons))
        assertEquals(3 to 8, SeasonProgress.position(26, seasons)) // a season not listed yet counts on in the last
    }

    @Test fun `season and episode convert back to overall progress`() {
        assertEquals(0, SeasonProgress.absolute(1, 0, seasons))
        assertEquals(15, SeasonProgress.absolute(2, 5, seasons))
        assertEquals(18, SeasonProgress.absolute(3, 0, seasons))
        for (p in 1..24) {
            val (s, e) = SeasonProgress.position(p, seasons)!!
            assertEquals(p, SeasonProgress.absolute(s, e, seasons))
        }
    }

    @Test fun `labels use seasons only for shows with several`() {
        assertEquals("S2 · E5", SeasonProgress.label(15, 24, seasons))
        assertEquals("Not started", SeasonProgress.label(0, 24, seasons))
        assertEquals("Ep 5 of 12", SeasonProgress.label(5, 12, listOf(12)))
        assertEquals("Ep 5", SeasonProgress.label(5, null, emptyList()))
        assertEquals("S3 · E2", SeasonProgress.upcoming(3, 2))
        assertEquals("Episode 12", SeasonProgress.upcoming(null, 12))
        assertNull(SeasonProgress.upcoming(2, null))
    }

    @Test fun `specials and empty seasons don't count`() {
        val info = listOf(SeasonInfo(2, "S2", 8, null, null), SeasonInfo(0, "Specials", 4, null, null), SeasonInfo(1, "S1", 10, null, null), SeasonInfo(3, "S3", 0, null, null))
        assertEquals(listOf(10, 8), SeasonProgress.regular(info))
    }

    private val zone = ZoneId.of("Europe/Paris")
    private fun at(day: LocalDate, hour: Int) = ZonedDateTime.of(day.atTime(hour, 0), zone).toInstant().toEpochMilli()
    private fun ep(title: String, airAt: Long) =
        UpcomingEpisode(MediaSource.TMDB, title, MediaType.TV, title, null, 1, 1, airAt, exactTime = true)

    @Test fun `calendar groups by day from today, in time order`() {
        val today = LocalDate.of(2026, 10, 5) // a Monday
        val days = EpisodeCalendar.days(
            listOf(
                ep("Late", at(today, 22)), ep("Early", at(today, 8)), ep("Yesterday", at(today.minusDays(1), 21)),
                ep("Tomorrow", at(today.plusDays(1), 20)), ep("Friday", at(today.plusDays(4), 21)), ep("NextYear", at(LocalDate.of(2027, 1, 4), 9)),
            ),
            now = at(today, 12), zone = zone, locale = Locale.UK,
        )
        assertEquals(listOf("Today", "Tomorrow", "Friday, 9 October", "Monday, 4 January 2027"), days.map { it.label })
        assertEquals(listOf("Early", "Late"), days.first().episodes.map { it.title })
    }
}
