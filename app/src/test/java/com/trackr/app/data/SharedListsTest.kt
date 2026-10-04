package com.trackr.app.data

import com.trackr.app.data.mapper.SocialMapper
import com.trackr.app.data.remote.supabase.SharedItemDto
import com.trackr.app.data.remote.supabase.SharedListDto
import com.trackr.app.data.remote.supabase.SharedMemberDto
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.Profile
import com.trackr.app.domain.util.GroupPick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SharedListsTest {
    private val ann = Profile("a", "ann")
    private val zed = Profile("z", "Zed")
    private val bob = Profile("b", "bob")

    private fun item(list: String, id: String, at: String, poster: String? = "p$id", by: String? = "a", type: String = "movie") =
        SharedItemDto(list, "tmdb", id, type, "T$id", poster, by, at)

    @Test fun `lists come newest first with the owner leading the members and a poster cover`() {
        val lists = SocialMapper.sharedLists(
            listOf(SharedListDto("l1", "Old", "z", "2026-01-01T00:00:00Z"), SharedListDto("l2", "New", "z", "2026-02-01T00:00:00Z")),
            listOf(SharedMemberDto("l1", "a"), SharedMemberDto("l1", "z"), SharedMemberDto("l1", "b"), SharedMemberDto("l1", "gone")),
            listOf(
                item("l1", "1", "2026-01-02T00:00:00Z"), item("l1", "2", "2026-01-05T00:00:00Z", poster = null),
                item("l1", "3", "2026-01-04T00:00:00Z"), item("l1", "4", "2026-01-03T00:00:00Z"), item("l1", "5", "2026-01-06T00:00:00Z"),
                item("l1", "6", "2026-01-01T00:00:00Z"),
            ),
            mapOf("a" to ann, "z" to zed, "b" to bob),
        )
        assertEquals(listOf("l2", "l1"), lists.map { it.id })
        val old = lists[1]
        assertEquals(listOf("z", "a", "b"), old.members.map { it.id })
        assertEquals(6, old.itemCount)
        assertEquals(listOf("p5", "p3", "p4", "p1"), old.posters)
        assertEquals(0, lists[0].itemCount)
    }

    @Test fun `items are newest first, skip unknown media and keep a departed adder as nobody`() {
        val items = SocialMapper.sharedItems(
            listOf(
                item("l", "1", "2026-01-01T00:00:00Z"), item("l", "2", "2026-01-03T00:00:00Z", by = "gone"),
                item("l", "3", "2026-01-02T00:00:00Z", type = "book"), item("l", "4", "2026-01-04T00:00:00Z", by = null, type = "anime"),
            ),
            mapOf("a" to ann),
        )
        assertEquals(listOf("4", "2", "1"), items.map { it.item.externalId })
        assertEquals(MediaType.ANIME, items[0].item.type)
        assertNull(items[0].addedBy); assertNull(items[1].addedBy); assertEquals(ann, items[2].addedBy)
    }

    @Test fun `group pick is random, never repeats when it can, and handles tiny lists`() {
        assertNull(GroupPick.pick(emptyList<String>()))
        assertEquals("only", GroupPick.pick(listOf("only"), previous = "only"))
        val items = listOf("a", "b", "c")
        val random = Random(42)
        var last: String? = null
        val seen = mutableSetOf<String>()
        repeat(50) {
            val p = GroupPick.pick(items, last, random)!!
            assertNotEquals(last, p)
            seen += p; last = p
        }
        assertTrue(seen == items.toSet())
    }
}
