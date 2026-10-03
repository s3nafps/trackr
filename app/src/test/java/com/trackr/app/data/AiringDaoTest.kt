package com.trackr.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackr.app.data.local.AiringDao
import com.trackr.app.data.local.AiringEntity
import com.trackr.app.data.local.TrackrDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AiringDaoTest {
    private lateinit var db: TrackrDatabase
    private lateinit var dao: AiringDao

    private fun row(id: String, episode: Int? = 1, airAt: Long = 1_000L) =
        AiringEntity("anilist", id, "ANIME", "Title $id", episode, airAt, "TIME")

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, TrackrDatabase::class.java).allowMainThreadQueries().build()
        dao = db.airingDao()
    }

    @After fun tearDown() = db.close()

    @Test fun `upsert replaces row for same title`() = runTest {
        dao.upsert(row("1", episode = 1))
        dao.upsert(row("1", episode = 2, airAt = 5_000L))
        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals(2, all[0].episode); assertEquals(5_000L, all[0].airAt)
    }

    @Test fun `markNotified sets flag only on that row`() = runTest {
        dao.upsert(row("1")); dao.upsert(row("2"))
        dao.markNotified("anilist", "1")
        assertTrue(dao.get("anilist", "1")!!.notified)
        assertFalse(dao.get("anilist", "2")!!.notified)
    }

    @Test fun `delete removes only that title`() = runTest {
        dao.upsert(row("1")); dao.upsert(row("2"))
        dao.delete("anilist", "1")
        assertNull(dao.get("anilist", "1"))
        assertNotNull(dao.get("anilist", "2"))
    }

    @Test fun `clear empties table`() = runTest {
        dao.upsert(row("1")); dao.upsert(row("2"))
        dao.clear()
        assertTrue(dao.getAll().isEmpty())
    }
}
