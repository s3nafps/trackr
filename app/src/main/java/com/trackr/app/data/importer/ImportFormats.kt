package com.trackr.app.data.importer

import com.trackr.app.domain.model.ListStatus
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/** Result of an import: [notFound] names the titles that couldn't be matched to a TMDB/AniList id. */
data class ImportSummary(val added: Int, val skipped: Int, val notFound: List<String> = emptyList())

class ImportException(message: String) : Exception(message)

/** Opened files: a zip becomes one entry per file, a gzip its single unpacked file, anything else itself. */
object ImportFiles {
    private const val MAX_BYTES = 50L * 1024 * 1024

    fun unpack(name: String, bytes: ByteArray): Map<String, String> = when {
        bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> buildMap {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { e ->
                    put(e.name.substringAfterLast('/'), String(readCapped(zip), Charsets.UTF_8))
                }
            }
        }
        bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte() ->
            mapOf(name.removeSuffix(".gz") to String(GZIPInputStream(ByteArrayInputStream(bytes)).use { readCapped(it) }, Charsets.UTF_8))
        else -> mapOf(name to String(bytes, Charsets.UTF_8))
    }

    /** Reads at most 50 MB, which also guards against zip bombs: an export is a few MB at most. */
    fun readCapped(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BYTES) throw ImportException("That file is too large to import.")
        }
        return out.toByteArray()
    }
}

/** AniList list statuses; paused and rewatching titles are still in progress, so they count as Watching. */
fun aniListStatus(raw: String?): ListStatus? = when (raw) {
    "CURRENT", "REPEATING", "PAUSED" -> ListStatus.WATCHING
    "COMPLETED" -> ListStatus.COMPLETED
    "PLANNING" -> ListStatus.PLAN_TO_WATCH
    "DROPPED" -> ListStatus.DROPPED
    else -> null
}

/** A calendar date as epoch millis at noon UTC, so it falls on that date (and year) in practically every time zone. */
fun dateMillis(date: LocalDate): Long = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

/** "2024-03-15" → millis; blanks and MAL's "0000-00-00" mean no date. */
fun isoDateMillis(s: String?): Long? = s?.trim()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let(::dateMillis)

/** AniList FuzzyDate: a year alone still places it in that year's review. */
fun fuzzyDateMillis(year: Int?, month: Int?, day: Int?): Long? =
    year?.let { runCatching { dateMillis(LocalDate.of(it, month ?: 7, day ?: 1)) }.getOrNull() }

/** A 0–10 score where 0 means "not rated", as AniList (POINT_10) and MyAnimeList use. */
fun tenPointRating(score: Double?): Int? = score?.takeIf { it > 0 }?.let { Math.round(it).toInt().coerceIn(1, 10) }

data class MalEntry(
    val malId: Int, val title: String, val episodes: Int?, val watched: Int, val score: Int?, val status: ListStatus,
    val finishedAt: Long? = null,
)

/** MyAnimeList's official list export (Profile → Export → anime list, an .xml.gz). */
object MalExport {
    private val anime = Regex("<anime>(.*?)</anime>", RegexOption.DOT_MATCHES_ALL)
    private val tags = listOf("series_animedb_id", "series_title", "series_episodes", "my_watched_episodes", "my_score", "my_status", "my_finish_date")
        .associateWith { Regex("<$it>\\s*(?:<!\\[CDATA\\[(.*?)]]>|([^<]*))\\s*</$it>", RegexOption.DOT_MATCHES_ALL) }

    private fun tag(block: String, name: String): String? =
        tags.getValue(name).find(block)?.let { m -> (m.groups[1]?.value ?: m.groups[2]?.value)?.trim() }

    fun status(raw: String?): ListStatus? = when (raw?.trim()?.lowercase()) {
        "watching", "on-hold", "1", "3" -> ListStatus.WATCHING
        "completed", "2" -> ListStatus.COMPLETED
        "dropped", "4" -> ListStatus.DROPPED
        "plan to watch", "6" -> ListStatus.PLAN_TO_WATCH
        else -> null
    }

    fun parse(xml: String): List<MalEntry> {
        if (!xml.contains("<myanimelist")) throw ImportException("That isn't a MyAnimeList export file.")
        return anime.findAll(xml).mapNotNull { m ->
            val b = m.groupValues[1]
            val id = tag(b, "series_animedb_id")?.toIntOrNull() ?: return@mapNotNull null
            MalEntry(
                malId = id,
                title = unescape(tag(b, "series_title").orEmpty()),
                episodes = tag(b, "series_episodes")?.toIntOrNull()?.takeIf { it > 0 },
                watched = tag(b, "my_watched_episodes")?.toIntOrNull() ?: 0,
                score = tenPointRating(tag(b, "my_score")?.toDoubleOrNull()),
                status = status(tag(b, "my_status")) ?: return@mapNotNull null,
                finishedAt = isoDateMillis(tag(b, "my_finish_date")),
            )
        }.toList()
    }

    private fun unescape(s: String) =
        s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#039;", "'").replace("&amp;", "&")
}

data class LetterboxdFilm(val name: String, val year: Int?, val rating: Int?, val watched: Boolean, val watchedAt: Long? = null)

/** Letterboxd's export zip (Settings → Import & Export): watched.csv, ratings.csv, watchlist.csv. */
object Letterboxd {
    private val known = setOf("watched.csv", "ratings.csv", "watchlist.csv", "diary.csv")

    /** [files] maps file name to CSV text; a single CSV of unknown name is read as a watched list. */
    fun parse(files: Map<String, String>): List<LetterboxdFilm> {
        val csvs = files.filterKeys { it.lowercase() in known }
            .ifEmpty { files.filterKeys { it.lowercase().endsWith(".csv") }.mapKeys { "watched.csv" } }
        if (csvs.isEmpty()) throw ImportException("No Letterboxd CSV files found. Pick the export .zip or a CSV from it.")
        val films = LinkedHashMap<String, LetterboxdFilm>()
        // Watchlist first so watched/rated rows for the same film win.
        val order = listOf("watchlist.csv", "watched.csv", "diary.csv", "ratings.csv")
        csvs.entries.sortedBy { order.indexOf(it.key.lowercase()) }.forEach { (file, text) ->
            val watched = file.lowercase() != "watchlist.csv"
            Csv.parseWithHeader(text).forEach { row ->
                val name = row["Name"]?.trim().orEmpty().ifEmpty { return@forEach }
                val year = row["Year"]?.trim()?.toIntOrNull()
                // Letterboxd rates 0.5–5 stars; double it for our 1–10.
                val rating = row["Rating"]?.trim()?.toDoubleOrNull()?.let { tenPointRating(it * 2) }
                // Diary URIs point at diary entries, not films, so title + year is the shared key.
                val key = "$name|$year"
                val prev = films[key]
                // Diary has the real "Watched Date"; elsewhere "Date" is when it was logged. The first watch counts.
                val watchedAt = if (watched) isoDateMillis(row["Watched Date"]) ?: isoDateMillis(row["Date"]) else null
                films[key] = LetterboxdFilm(
                    name, year, rating ?: prev?.rating, watched || prev?.watched == true,
                    listOfNotNull(watchedAt, prev?.watchedAt).minOrNull(),
                )
            }
        }
        return films.values.toList()
    }
}
