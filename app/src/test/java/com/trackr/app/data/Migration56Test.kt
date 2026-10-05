package com.trackr.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackr.app.data.local.MIGRATION_5_6
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Migration56Test {
    @Test fun `v5 title details keep their data and get an unknown aired count`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE title_meta (source TEXT NOT NULL, externalId TEXT NOT NULL, genres TEXT NOT NULL, " +
                                "seasonEpisodes TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(source, externalId))",
                        )
                        db.execSQL("INSERT INTO title_meta VALUES ('tmdb','1','Drama','10,10,10,10',5)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_5_6.migrate(db)
        db.query("SELECT seasonEpisodes, fetchedAt, airedEpisodes FROM title_meta").use { c ->
            c.moveToFirst()
            assertEquals("10,10,10,10", c.getString(0)); assertEquals(5, c.getInt(1))
            assertTrue(c.isNull(2))
        }
        helper.close()
    }
}
