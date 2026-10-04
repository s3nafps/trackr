package com.trackr.app.data

import com.trackr.app.data.mapper.SocialMapper
import com.trackr.app.data.remote.supabase.CommentDto
import com.trackr.app.data.remote.supabase.ReactionDto
import com.trackr.app.data.remote.supabase.RecommendationDto
import com.trackr.app.domain.model.EntrySocial
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialMapperTest {
    private val ann = Profile("a", "ann")
    private val ben = Profile("b", "ben")

    @Test fun `reactions and comments are counted per entry with mine marked`() {
        val s = SocialMapper.summarize(
            listOf("e1", "e2", "e3"),
            listOf(ReactionDto("e1", "a", "🔥"), ReactionDto("e1", "b", "🔥"), ReactionDto("e1", "b", "😂"), ReactionDto("e2", "a", "❤️")),
            listOf("e1", "e1", "e3"),
            me = "b",
        )
        assertEquals(EntrySocial(mapOf("🔥" to 2, "😂" to 1), setOf("🔥", "😂"), 2), s["e1"])
        assertEquals(EntrySocial(mapOf("❤️" to 1), emptySet(), 0), s["e2"])
        assertEquals(EntrySocial(commentCount = 1), s["e3"])
    }

    @Test fun `toggling a reaction updates counts and mine optimistically`() {
        val s = EntrySocial(mapOf("🔥" to 1), emptySet())
        val on = s.toggled("🔥")
        assertEquals(EntrySocial(mapOf("🔥" to 2), setOf("🔥")), on)
        assertEquals(s, on.toggled("🔥"))
        assertEquals(EntrySocial(mapOf("👏" to 1), setOf("👏")), EntrySocial().toggled("👏"))
        assertEquals(EntrySocial(), EntrySocial().toggled("👏").toggled("👏"))
    }

    @Test fun `comments are oldest first and deletable by their author or the entry owner`() {
        val rows = listOf(
            CommentDto("c2", "e", "b", "second", "2026-10-05T10:00:00Z"),
            CommentDto("c1", "e", "a", "first", "2026-10-05T09:00:00Z"),
            CommentDto("c3", "e", "gone", "orphan", "2026-10-05T11:00:00Z"),
        )
        val profiles = mapOf("a" to ann, "b" to ben)
        val asBen = SocialMapper.comments(rows, profiles, me = "b", entryOwner = "a")
        assertEquals(listOf("c1", "c2"), asBen.map { it.id })
        assertEquals(listOf(false, true), asBen.map { it.canDelete })
        assertEquals("ann", asBen.first().username)
        assertEquals(listOf(true, true), SocialMapper.comments(rows, profiles, me = "a", entryOwner = "a").map { it.canDelete })
    }

    @Test fun `recommendations are newest first and skip unknown senders or media`() {
        fun rec(id: String, from: String, at: String, source: String = "tmdb", type: String = "movie", note: String? = null) =
            RecommendationDto(id, from, "me", source, "42", type, "Heat", "p", note, false, at)
        val recs = SocialMapper.recommendations(
            listOf(
                rec("r1", "a", "2026-10-01T00:00:00Z", note = "  "),
                rec("r2", "b", "2026-10-03T00:00:00Z", source = "anilist", type = "anime", note = "watch it"),
                rec("r3", "gone", "2026-10-04T00:00:00Z"),
                rec("r4", "a", "2026-10-05T00:00:00Z", source = "imdb"),
            ),
            mapOf("a" to ann, "b" to ben),
        )
        assertEquals(listOf("r2", "r1"), recs.map { it.id })
        assertEquals(MediaSource.ANILIST, recs[0].item.source); assertEquals(MediaType.ANIME, recs[0].item.type)
        assertEquals("watch it", recs[0].note)
        assertNull(recs[1].note)
        assertEquals("ben", recs[0].from.username)
        assertFalse(recs[0].seen)
        assertTrue(recs.all { it.item.title == "Heat" })
    }
}
