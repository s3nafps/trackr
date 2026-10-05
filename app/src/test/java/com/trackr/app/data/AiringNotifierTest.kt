package com.trackr.app.data

import com.trackr.app.data.airing.AiringNotifier
import com.trackr.app.data.local.AiringEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AiringNotifierTest {
    private fun row(title: String, episode: Int?, type: String) =
        AiringEntity("anilist", "1", type, title, episode, 0L, "TIME")

    @Test fun `episode message copy`() {
        val (t, text) = AiringNotifier.message(row("Frieren", 12, "anime"))
        assertEquals("Frieren", t)
        assertEquals("Frieren · Episode 12 is out", text)
    }

    @Test fun `movie message copy`() {
        assertEquals("Dune: Part Three is out today", AiringNotifier.message(row("Dune: Part Three", null, "movie")).second)
    }

    @Test fun `episode null means movie copy`() {
        assertEquals("Odd Show is out today", AiringNotifier.message(row("Odd Show", null, "tv")).second)
    }

    @Test fun `tv episodes numbered by season say which season`() {
        val row = AiringEntity("tmdb", "2", "tv", "Severance", 3, 0L, "DATE", season = 2)
        assertEquals("Severance · Season 2, episode 3 is out", AiringNotifier.message(row).second)
    }
}
