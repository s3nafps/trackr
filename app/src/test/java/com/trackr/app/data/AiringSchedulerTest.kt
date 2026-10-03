package com.trackr.app.data

import com.trackr.app.data.airing.AiringScheduler
import com.trackr.app.data.airing.AlarmClient
import com.trackr.app.data.local.AiringEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAlarmClient(var exactAllowed: Boolean = true) : AlarmClient {
    data class Set(val code: Int, val at: Long, val exact: Boolean, val source: String, val id: String)
    val sets = mutableListOf<Set>()
    val cancels = mutableListOf<Int>()
    override fun canScheduleExact() = exactAllowed
    override fun set(code: Int, atMillis: Long, exact: Boolean, source: String, externalId: String) {
        sets += Set(code, atMillis, exact, source, externalId)
    }
    override fun cancel(code: Int) { cancels += code }
}

class AiringSchedulerTest {
    private val hour = 3_600_000L
    private val now = 1_000_000_000L
    private val client = FakeAlarmClient()
    private val fired = mutableListOf<AiringEntity>()
    private val scheduler = AiringScheduler(client) { fired += it }

    private fun row(id: String = "1", airAt: Long, episode: Int? = 3, notified: Boolean = false) =
        AiringEntity("anilist", id, "ANIME", "T$id", episode, airAt, "TIME", notified)

    @Test fun `future row sets exact alarm`() {
        val r = row(airAt = now + hour)
        scheduler.apply(listOf(r), emptyList(), now)
        val s = client.sets.single()
        assertEquals(now + hour, s.at); assertTrue(s.exact)
        assertEquals("anilist", s.source); assertEquals("1", s.id)
        assertEquals(AiringScheduler.requestCode("anilist", "1"), s.code)
        assertTrue(fired.isEmpty())
    }

    @Test fun `exact not granted falls back to inexact`() {
        client.exactAllowed = false
        scheduler.apply(listOf(row(airAt = now + hour)), emptyList(), now)
        assertFalse(client.sets.single().exact)
    }

    @Test fun `moved time replaces alarm with same code`() {
        scheduler.apply(listOf(row(airAt = now + hour)), emptyList(), now)
        scheduler.apply(listOf(row(airAt = now + 2 * hour)), emptyList(), now)
        assertEquals(2, client.sets.size)
        assertEquals(client.sets[0].code, client.sets[1].code)
        assertEquals(now + 2 * hour, client.sets[1].at)
    }

    @Test fun `removed row cancels alarm`() {
        scheduler.apply(emptyList(), listOf(row("7", airAt = now + hour)), now)
        assertEquals(listOf(AiringScheduler.requestCode("anilist", "7")), client.cancels)
        assertTrue(client.sets.isEmpty())
    }

    @Test fun `notified row cancels instead of setting`() {
        scheduler.apply(listOf(row(airAt = now + hour, notified = true)), emptyList(), now)
        assertTrue(client.sets.isEmpty())
        assertEquals(1, client.cancels.size)
        assertTrue(fired.isEmpty())
    }

    @Test fun `past row under 12h fires immediately`() {
        val r = row(airAt = now - 11 * hour)
        scheduler.apply(listOf(r), emptyList(), now)
        assertEquals(listOf(r), fired)
        assertTrue(client.sets.isEmpty())
    }

    @Test fun `past row over 12h is skipped`() {
        scheduler.apply(listOf(row(airAt = now - 13 * hour)), emptyList(), now)
        assertTrue(fired.isEmpty()); assertTrue(client.sets.isEmpty())
    }

    @Test fun `past row exactly 12h old still fires`() {
        scheduler.apply(listOf(row(airAt = now - 12 * hour)), emptyList(), now)
        assertEquals(1, fired.size)
    }

    @Test fun `request code stable per title`() {
        assertEquals(AiringScheduler.requestCode("tmdb", "5"), AiringScheduler.requestCode("tmdb", "5"))
        assertTrue(AiringScheduler.requestCode("tmdb", "5") != AiringScheduler.requestCode("anilist", "5"))
    }

    @Test fun `cancelAll cancels every row`() {
        scheduler.cancelAll(listOf(row("1", airAt = now), row("2", airAt = now)))
        assertEquals(2, client.cancels.size)
    }
}
