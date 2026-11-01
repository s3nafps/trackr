package com.trackr.app.data

import com.trackr.app.data.social.AlertTarget
import com.trackr.app.data.social.SocialAlerts
import com.trackr.app.data.social.SocialNotificationDto
import org.junit.Assert.assertEquals
import org.junit.Test

class SocialAlertsTest {
    private fun row(kind: String, actor: String, title: String? = null, detail: String? = null, id: String? = null) =
        SocialNotificationDto(kind, actor, title, detail, id?.let { "tmdb" }, id, id?.let { "tv" }, "2026-11-01T10:00:00Z")

    @Test fun `alerts group by kind and title and name who did it`() {
        val alerts = SocialAlerts.compose(
            listOf(
                row("reaction", "ann", "Severance", "🔥", "1"),
                row("reaction", "ben", "Severance", "❤️", "1"),
                row("reaction", "cat", "Severance", "🔥", "1"),
                row("comment", "ann", "Severance", "So good", "1"),
                row("friend_request", "dan"),
                row("recommendation", "ben", "Heat", null, "2"),
                row("unknown_kind", "x"),
            ),
        )
        assertEquals(
            listOf("ann and 2 others reacted to Severance", "ann commented on Severance", "dan sent you a friend request", "ben recommended Heat"),
            alerts.map { it.title },
        )
        assertEquals("🔥 ❤️", alerts[0].text)
        assertEquals(AlertTarget.Title("tmdb", "tv", "1"), alerts[1].target)
        assertEquals(AlertTarget.Friends, alerts[2].target)
        assertEquals(AlertTarget.Friends, alerts[3].target) // recommendations live in the Friends inbox
    }

    @Test fun `names read naturally`() {
        assertEquals("ann", SocialAlerts.people(listOf("ann")))
        assertEquals("ann and ben", SocialAlerts.people(listOf("ann", "ben")))
        assertEquals("ann and 3 others", SocialAlerts.people(listOf("ann", "ben", "cat", "dan")))
    }
}
