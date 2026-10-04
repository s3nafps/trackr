package com.trackr.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackr.app.data.local.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Migration23Test {
    @Test fun `v2 rows keep their data and get a null completedAt`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE list_entries (source TEXT NOT NULL, externalId TEXT NOT NULL, mediaType TEXT NOT NULL, " +
                                "title TEXT NOT NULL, posterUrl TEXT, backdropUrl TEXT, status TEXT NOT NULL, rating INTEGER, " +
                                "progress INTEGER NOT NULL, totalEpisodes INTEGER, updatedAt INTEGER NOT NULL, " +
                                "dirty INTEGER NOT NULL, deleted INTEGER NOT NULL, notify INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(source, externalId))",
                        )
                        db.execSQL("INSERT INTO list_entries VALUES ('tmdb','1','movie','Heat',NULL,NULL,'completed',9,1,1,10,1,0,0)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_2_3.migrate(db)
        db.query("SELECT status, rating, dirty, completedAt FROM list_entries").use { c ->
            c.moveToFirst()
            assertEquals("completed", c.getString(0)); assertEquals(9, c.getInt(1)); assertEquals(1, c.getInt(2))
            assertTrue(c.isNull(3))
        }
        helper.close()
    }
}
