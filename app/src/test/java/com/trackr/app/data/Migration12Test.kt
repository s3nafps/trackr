package com.trackr.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackr.app.data.local.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Migration12Test {
    @Test fun `v1 dirty row survives migration with notify off`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE list_entries (source TEXT NOT NULL, externalId TEXT NOT NULL, mediaType TEXT NOT NULL, " +
                                "title TEXT NOT NULL, posterUrl TEXT, backdropUrl TEXT, status TEXT NOT NULL, rating INTEGER, " +
                                "progress INTEGER NOT NULL, totalEpisodes INTEGER, updatedAt INTEGER NOT NULL, " +
                                "dirty INTEGER NOT NULL, deleted INTEGER NOT NULL, PRIMARY KEY(source, externalId))",
                        )
                        db.execSQL("INSERT INTO list_entries VALUES ('tmdb','1','TV','Show',NULL,NULL,'WATCHING',NULL,2,NULL,10,1,0)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_1_2.migrate(db)
        db.query("SELECT dirty, notify, progress FROM list_entries").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0)); assertEquals(0, c.getInt(1)); assertEquals(2, c.getInt(2))
        }
        db.query("SELECT COUNT(*) FROM airing_schedule").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        helper.close()
    }
}
