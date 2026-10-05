package com.trackr.app.data

import com.trackr.app.data.remote.supabase.ListEntryDto
import com.trackr.app.data.remote.supabase.toRow
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ListEntryRowTest {
    @Test fun `cleared values are sent, so the upsert overwrites them on the server`() {
        val row = ListEntryDto(
            userId = "u1", source = "tmdb", externalId = "1", mediaType = "movie", title = "Heat",
            status = "WATCHING", updatedAt = "2026-10-05T00:00:00Z",
        ).toRow()
        assertEquals(JsonNull, row["rating"])
        assertEquals(JsonPrimitive(0), row["progress"])
        assertEquals(JsonPrimitive(false), row["notify"])
        assertEquals(JsonNull, row["completed_at"])
        assertEquals(JsonNull, row["poster_url"])
        assertEquals(JsonPrimitive("Heat"), row["title"])
    }
}
