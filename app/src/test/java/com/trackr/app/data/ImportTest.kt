package com.trackr.app.data

import com.trackr.app.data.airing.AiringRefreshScheduler
import com.trackr.app.data.importer.Backup
import com.trackr.app.data.importer.Csv
import com.trackr.app.data.importer.ImportException
import com.trackr.app.data.importer.ImportFiles
import com.trackr.app.data.importer.ImportSummary
import com.trackr.app.data.importer.Letterboxd
import com.trackr.app.data.importer.LetterboxdFilm
import com.trackr.app.data.importer.MalExport
import com.trackr.app.data.importer.aniListStatus
import com.trackr.app.data.importer.dateMillis
import com.trackr.app.data.importer.fuzzyDateMillis
import com.trackr.app.data.importer.isoDateMillis
import com.trackr.app.data.importer.tenPointRating
import com.trackr.app.data.mapper.ListEntryMapper.toEntity
import com.trackr.app.data.remote.anilist.AniListClient
import com.trackr.app.data.remote.tmdb.TmdbApi
import com.trackr.app.data.remote.tmdb.TmdbPage
import com.trackr.app.data.remote.tmdb.TmdbResult
import com.trackr.app.data.repository.AuthRepository
import com.trackr.app.data.repository.ImportRepository
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.SupabaseListRemote
import com.trackr.app.data.sync.SyncScheduler
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportFormatsTest {
    @Test fun `csv handles quotes, embedded commas and newlines, CRLF and a BOM`() {
        val rows = Csv.parse("﻿Name,Year\r\n\"Crouching Tiger, Hidden Dragon\",2000\r\n\"He said \"\"hi\"\"\nthen left\",1999\n\n")
        assertEquals(listOf(listOf("Name", "Year"), listOf("Crouching Tiger, Hidden Dragon", "2000"), listOf("He said \"hi\"\nthen left", "1999")), rows)
    }

    @Test fun `csv writes what it reads`() {
        val text = Csv.write(listOf("a", "b"), listOf(listOf("x, y", "say \"hi\""), listOf(null, 3)))
        assertEquals(listOf(listOf("a", "b"), listOf("x, y", "say \"hi\""), listOf("", "3")), Csv.parse(text))
    }

    @Test fun `anilist statuses and ten point scores map to ours`() {
        assertEquals(ListStatus.WATCHING, aniListStatus("CURRENT"))
        assertEquals(ListStatus.WATCHING, aniListStatus("PAUSED"))
        assertEquals(ListStatus.WATCHING, aniListStatus("REPEATING"))
        assertEquals(ListStatus.COMPLETED, aniListStatus("COMPLETED"))
        assertEquals(ListStatus.PLAN_TO_WATCH, aniListStatus("PLANNING"))
        assertEquals(ListStatus.DROPPED, aniListStatus("DROPPED"))
        assertNull(aniListStatus("SOMETHING"))
        assertNull(tenPointRating(0.0))
        assertNull(tenPointRating(null))
        assertEquals(8, tenPointRating(7.5))
        assertEquals(1, tenPointRating(0.4))
        assertEquals(10, tenPointRating(10.0))
    }

    @Test fun `mal export is parsed including cdata titles and unrated entries`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <myanimelist>
              <myinfo><user_name>me</user_name></myinfo>
              <anime>
                <series_animedb_id>5114</series_animedb_id>
                <series_title><![CDATA[Fullmetal Alchemist: Brotherhood]]></series_title>
                <series_episodes>64</series_episodes>
                <my_watched_episodes>64</my_watched_episodes>
                <my_score>10</my_score>
                <my_status>Completed</my_status>
              </anime>
              <anime>
                <series_animedb_id>1</series_animedb_id>
                <series_title>Tom &amp; Jerry</series_title>
                <series_episodes>0</series_episodes>
                <my_watched_episodes>3</my_watched_episodes>
                <my_score>0</my_score>
                <my_status>On-Hold</my_status>
              </anime>
              <anime>
                <series_animedb_id>2</series_animedb_id>
                <series_title>Later</series_title>
                <my_status>Plan to Watch</my_status>
              </anime>
            </myanimelist>
        """.trimIndent()
        val e = MalExport.parse(xml)
        assertEquals(listOf(5114, 1, 2), e.map { it.malId })
        assertEquals("Fullmetal Alchemist: Brotherhood", e[0].title)
        assertEquals(64, e[0].episodes); assertEquals(10, e[0].score); assertEquals(ListStatus.COMPLETED, e[0].status)
        assertEquals("Tom & Jerry", e[1].title)
        assertNull(e[1].episodes); assertNull(e[1].score); assertEquals(ListStatus.WATCHING, e[1].status); assertEquals(3, e[1].watched)
        assertEquals(ListStatus.PLAN_TO_WATCH, e[2].status); assertEquals(0, e[2].watched)
        assertThrows(ImportException::class.java) { MalExport.parse("<html>nope</html>") }
    }

    @Test fun `letterboxd merges watched, ratings and watchlist`() {
        val films = Letterboxd.parse(
            mapOf(
                "watched.csv" to "Date,Name,Year,Letterboxd URI\n2024-01-01,Heat,1995,https://boxd.it/a\n2024-01-02,\"Crouching Tiger, Hidden Dragon\",2000,https://boxd.it/b\n",
                "ratings.csv" to "Date,Name,Year,Letterboxd URI,Rating\n2024-01-01,Heat,1995,https://boxd.it/a,4.5\n",
                "watchlist.csv" to "Date,Name,Year,Letterboxd URI\n2024-02-01,Dune,2021,https://boxd.it/c\n2024-02-01,Heat,1995,https://boxd.it/a\n",
                "profile.csv" to "Username\nme\n",
            ),
        )
        assertEquals(
            setOf(
                LetterboxdFilm("Heat", 1995, 9, watched = true, watchedAt = dateMillis(LocalDate.of(2024, 1, 1))),
                LetterboxdFilm("Crouching Tiger, Hidden Dragon", 2000, null, watched = true, watchedAt = dateMillis(LocalDate.of(2024, 1, 2))),
                LetterboxdFilm("Dune", 2021, null, watched = false),
            ),
            films.toSet(),
        )
    }

    @Test fun `a lone csv of another name is read as watched, and no csv is an error`() {
        val films = Letterboxd.parse(mapOf("export.csv" to "Name,Year,Rating\nAlien,1979,5\n"))
        assertEquals(listOf(LetterboxdFilm("Alien", 1979, 10, watched = true)), films)
        assertThrows(ImportException::class.java) { Letterboxd.parse(mapOf("notes.txt" to "hi")) }
    }

    @Test fun `zip and gzip files are unpacked, plain files pass through`() {
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("letterboxd-me/")); z.closeEntry()
                z.putNextEntry(ZipEntry("letterboxd-me/watched.csv")); z.write("Name,Year\nHeat,1995\n".toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        assertEquals(mapOf("watched.csv" to "Name,Year\nHeat,1995\n"), ImportFiles.unpack("export.zip", zip))
        val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write("<myanimelist/>".toByteArray()) } }.toByteArray()
        assertEquals(mapOf("animelist.xml" to "<myanimelist/>"), ImportFiles.unpack("animelist.xml.gz", gz))
        assertEquals(mapOf("b.json" to "{}"), ImportFiles.unpack("b.json", "{}".toByteArray()))
    }

    @Test fun `backup json round-trips and rejects foreign, newer and unknown data`() {
        val entries = listOf(
            ListEntry(MediaSource.TMDB, "1", MediaType.MOVIE, "Heat", "p", null, ListStatus.COMPLETED, 9, 1, 1, 1_700_000_000_000, notify = false, completedAt = 1_699_000_000_000),
            ListEntry(MediaSource.ANILIST, "21", MediaType.ANIME, "One Piece", null, "b", ListStatus.WATCHING, null, 1100, null, 1_700_000_001_000, notify = true),
        )
        assertEquals(entries, Backup.fromJson(Backup.toJson(entries, Instant.EPOCH)))
        assertThrows(ImportException::class.java) { Backup.fromJson("""{"entries": []}""") }
        assertThrows(ImportException::class.java) { Backup.fromJson("not json") }
        assertThrows(ImportException::class.java) { Backup.fromJson("""{"app":"trackr","version":99,"exported_at":"x","entries":[]}""") }
        val unknown = """{"app":"trackr","version":1,"exported_at":"x","entries":[
            {"source":"imdb","external_id":"1","media_type":"movie","title":"A","status":"completed","updated_at":"2024-01-01T00:00:00Z"},
            {"source":"tmdb","external_id":"2","media_type":"movie","title":"B","status":"on_hold","updated_at":"2024-01-01T00:00:00Z"},
            {"source":"tmdb","external_id":"3","media_type":"movie","title":"C","status":"completed","updated_at":"2024-01-01T00:00:00Z","extra":1}
        ]}"""
        assertEquals(listOf("3"), Backup.fromJson(unknown).map { it.externalId })
    }

    @Test fun `import dates are parsed to noon UTC on that day`() {
        assertEquals(Instant.parse("2024-03-15T12:00:00Z").toEpochMilli(), isoDateMillis("2024-03-15"))
        assertNull(isoDateMillis("0000-00-00")); assertNull(isoDateMillis("")); assertNull(isoDateMillis(null))
        assertEquals(Instant.parse("2023-07-01T12:00:00Z").toEpochMilli(), fuzzyDateMillis(2023, null, null))
        assertEquals(Instant.parse("2023-02-05T12:00:00Z").toEpochMilli(), fuzzyDateMillis(2023, 2, 5))
        assertNull(fuzzyDateMillis(null, 2, 5)); assertNull(fuzzyDateMillis(2023, 2, 30))
    }

    @Test fun `mal finish dates are read`() {
        val xml = "<myanimelist><anime><series_animedb_id>1</series_animedb_id><series_title>A</series_title>" +
            "<my_status>Completed</my_status><my_finish_date>2024-03-15</my_finish_date></anime>" +
            "<anime><series_animedb_id>2</series_animedb_id><series_title>B</series_title>" +
            "<my_status>Completed</my_status><my_finish_date>0000-00-00</my_finish_date></anime></myanimelist>"
        assertEquals(listOf(isoDateMillis("2024-03-15"), null), MalExport.parse(xml).map { it.finishedAt })
    }

    @Test fun `letterboxd prefers the diary's watched date and keeps the first watch`() {
        val films = Letterboxd.parse(
            mapOf(
                "watched.csv" to "Date,Name,Year\n2024-05-01,Heat,1995\n",
                "diary.csv" to "Date,Name,Year,Watched Date\n2024-05-02,Heat,1995,2023-12-30\n2024-06-01,Heat,1995,2024-06-01\n",
            ),
        )
        assertEquals(isoDateMillis("2023-12-30"), films.single().watchedAt)
    }

    @Test fun `csv export has a header and one escaped row per entry`() {
        val e = ListEntry(MediaSource.TMDB, "7", MediaType.MOVIE, "Crouching Tiger, Hidden Dragon", null, null, ListStatus.COMPLETED, 8, 1, 1, 0)
        val rows = Csv.parse(Backup.toCsv(listOf(e)))
        assertEquals(listOf("title", "media_type", "status", "rating", "progress", "total_episodes", "source", "external_id", "updated_at", "completed_at"), rows[0])
        assertEquals(listOf("Crouching Tiger, Hidden Dragon", "movie", "completed", "8", "1", "1", "tmdb", "7", "1970-01-01T00:00:00Z", ""), rows[1])
    }
}

class ImportRepositoryTest {
    private val dao = FakeDao()
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private val airingRefresh = mockk<AiringRefreshScheduler>(relaxed = true)
    private val lists = ListRepository(dao, mockk<SupabaseListRemote>(relaxed = true), mockk<AuthRepository>(relaxed = true), scheduler, airingRefresh)
    private val anilist = mockk<AniListClient>()
    private val tmdb = mockk<TmdbApi>()
    private val repo = ImportRepository(anilist, tmdb, lists)

    private fun anime(id: String, eps: Int? = 12) = MediaItem(MediaSource.ANILIST, id, MediaType.ANIME, "A$id", "poster$id", totalEpisodes = eps)

    @Test fun `anilist import adds new titles, keeps existing ones and revives removed ones`() = runTest {
        dao.upsert(ListEntry(MediaSource.ANILIST, "1", MediaType.ANIME, "Mine", null, null, ListStatus.DROPPED, 3, 2, 12, 5).toEntity(dirty = false))
        dao.upsert(ListEntry(MediaSource.ANILIST, "2", MediaType.ANIME, "Gone", null, null, ListStatus.DROPPED, null, 0, 12, 5).toEntity(dirty = true, deleted = true))
        coEvery { anilist.userAnimeList("someone") } returns listOf(
            AniListClient.ListRow(anime("1"), "COMPLETED", 9.0, 12),
            AniListClient.ListRow(anime("2"), "CURRENT", 0.0, 4),
            AniListClient.ListRow(anime("3"), "COMPLETED", 7.0, 3),
            AniListClient.ListRow(anime("4", eps = null), "PAUSED", 0.0, 30),
            AniListClient.ListRow(anime("5"), "PLANNING", 0.0, 50),
            AniListClient.ListRow(anime("6"), "WHATEVER", 0.0, 0),
        )
        val summary = repo.fromAniList(" https://anilist.co/user/someone/ ")
        assertEquals(ImportSummary(added = 4, skipped = 1), summary)

        val mine = dao.get("anilist", "1")!!
        assertEquals("Mine", mine.title); assertEquals("dropped", mine.status); assertFalse(mine.dirty)
        val revived = dao.get("anilist", "2")!!
        assertFalse(revived.deleted); assertTrue(revived.dirty); assertEquals("watching", revived.status); assertNull(revived.rating)
        val completed = dao.get("anilist", "3")!!
        assertEquals(12, completed.progress) // completed means every episode
        assertEquals(7, completed.rating); assertEquals("poster3", completed.posterUrl)
        assertEquals(30, dao.get("anilist", "4")!!.progress) // unknown length isn't clamped
        assertEquals(12, dao.get("anilist", "5")!!.progress) // but a known one is
        assertNull(dao.get("anilist", "6"))
        verify(exactly = 1) { scheduler.syncNow() }
        verify(exactly = 1) { airingRefresh.refreshNow() }
    }

    @Test fun `completed imports keep the source's date, or are marked as unknown so Year in review skips them`() = runTest {
        val finished = Instant.parse("2023-02-05T12:00:00Z").toEpochMilli()
        coEvery { anilist.userAnimeList("me") } returns listOf(
            AniListClient.ListRow(anime("1"), "COMPLETED", 8.0, 12, completedAt = finished),
            AniListClient.ListRow(anime("2"), "COMPLETED", 8.0, 12),
            AniListClient.ListRow(anime("3"), "CURRENT", 0.0, 2, completedAt = finished),
        )
        repo.fromAniList("me")
        assertEquals(finished, dao.get("anilist", "1")!!.completedAt)
        assertEquals(ListEntry.COMPLETED_DATE_UNKNOWN, dao.get("anilist", "2")!!.completedAt)
        assertNull(dao.get("anilist", "3")!!.completedAt)
    }

    @Test fun `anilist errors become a readable message and blank names are refused`() = runTest {
        coEvery { anilist.userAnimeList(any()) } throws IOException("Private User")
        val e = runCatching { repo.fromAniList("@hidden") }.exceptionOrNull()
        assertTrue(e is ImportException && e.message!!.contains("hidden"))
        assertTrue(runCatching { repo.fromAniList("  ") }.exceptionOrNull() is ImportException)
    }

    @Test fun `nothing new means no sync is scheduled`() = runTest {
        coEvery { anilist.userAnimeList("me") } returns emptyList()
        assertEquals(ImportSummary(0, 0), repo.fromAniList("me"))
        verify(exactly = 0) { scheduler.syncNow() }
    }

    @Test fun `mal import matches through anilist and reports titles it can't find`() = runTest {
        coEvery { anilist.byMalIds(listOf(5114, 999)) } returns mapOf(5114 to anime("5114", eps = 64))
        val xml = "<myanimelist><anime><series_animedb_id>5114</series_animedb_id><series_title>FMA</series_title>" +
            "<my_watched_episodes>20</my_watched_episodes><my_score>9</my_score><my_status>Watching</my_status></anime>" +
            "<anime><series_animedb_id>999</series_animedb_id><series_title><![CDATA[Obscure]]></series_title>" +
            "<my_status>Completed</my_status></anime></myanimelist>"
        assertEquals(ImportSummary(1, 0, listOf("Obscure")), repo.fromMal(mapOf("animelist.xml" to xml)))
        val e = dao.get("anilist", "5114")!!
        assertEquals("watching", e.status); assertEquals(20, e.progress); assertEquals(9, e.rating)
    }

    @Test fun `letterboxd films are matched by title and year`() = runTest {
        fun movie(id: Int, title: String, date: String) = TmdbResult(id = id, title = title, releaseDate = date, posterPath = "/$id.jpg")
        coEvery { tmdb.searchMovies("Heat", false, 1995) } returns TmdbPage(results = listOf(movie(2, "Heat (TV)", "1995-02-01"), movie(949, "Heat", "1995-12-15")))
        coEvery { tmdb.searchMovies("Dune", false, 2021) } returns TmdbPage(results = emptyList())
        coEvery { tmdb.searchMovies("Dune", false, null) } returns TmdbPage(results = listOf(movie(438631, "Dune", "2021-09-15")))
        coEvery { tmdb.searchMovies("Nope", false, 1990) } returns TmdbPage(results = emptyList())
        coEvery { tmdb.searchMovies("Nope", false, null) } returns TmdbPage(results = emptyList())
        val progress = mutableListOf<Pair<Int, Int>>()
        val summary = repo.fromLetterboxd(
            mapOf(
                "watched.csv" to "Name,Year\nHeat,1995\nNope,1990\n",
                "ratings.csv" to "Name,Year,Rating\nHeat,1995,4\n",
                "watchlist.csv" to "Name,Year\nDune,2021\n",
            ),
        ) { done, total -> synchronized(progress) { progress += done to total } }
        assertEquals(ImportSummary(2, 0, listOf("Nope (1990)")), summary)
        // Both Heat results are from 1995, so TMDB's order decides.
        val heat = dao.get("tmdb", "2")!!
        assertEquals("completed", heat.status); assertEquals(8, heat.rating); assertEquals(1, heat.progress); assertEquals(1, heat.totalEpisodes)
        val dune = dao.get("tmdb", "438631")!!
        assertEquals("plan_to_watch", dune.status); assertEquals(0, dune.progress); assertEquals("movie", dune.mediaType)
        assertEquals(3 to 3, progress.maxBy { it.first })
    }

    @Test fun `a year match beats TMDB's first result`() = runTest {
        coEvery { tmdb.searchMovies("Solaris", false, 1972) } returns TmdbPage(
            results = listOf(TmdbResult(id = 1, title = "Solaris", releaseDate = "2002-11-27"), TmdbResult(id = 2, title = "Solaris", releaseDate = "1972-03-20")),
        )
        repo.fromLetterboxd(mapOf("watched.csv" to "Name,Year\nSolaris,1972\n"))
        assertTrue(dao.get("tmdb", "2") != null)
        assertNull(dao.get("tmdb", "1"))
    }

    @Test fun `offline letterboxd import fails instead of reporting everything as not found`() = runTest {
        coEvery { tmdb.searchMovies(any(), any(), any()) } throws java.net.UnknownHostException("offline")
        assertTrue(runCatching { repo.fromLetterboxd(mapOf("watched.csv" to "Name,Year\nHeat,1995\n")) }.exceptionOrNull() is ImportException)
        assertTrue(dao.getAllRaw().isEmpty())
    }

    @Test fun `a backup exported from one list imports into another`() = runTest {
        lists.importEntries(
            listOf(
                ListEntry(MediaSource.TMDB, "1", MediaType.MOVIE, "Heat", null, null, ListStatus.COMPLETED, 9, 1, 1, 0),
                ListEntry(MediaSource.ANILIST, "21", MediaType.ANIME, "One Piece", null, null, ListStatus.WATCHING, null, 1100, null, 0, notify = true),
            ),
        )
        val json = repo.exportJson()
        val otherDao = FakeDao()
        val other = ListRepository(otherDao, mockk(relaxed = true), mockk(relaxed = true), scheduler, airingRefresh)
        assertEquals(ImportSummary(2, 0), ImportRepository(anilist, tmdb, other).fromBackup(mapOf("b.json" to json)))
        val op = otherDao.get("anilist", "21")!!
        assertEquals(1100, op.progress); assertTrue(op.notify); assertTrue(op.dirty)
        assertEquals(3, Csv.parse(repo.exportCsv()).size)
    }
}
