package com.trackr.app.widget

import com.trackr.app.data.local.AiringEntity
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.TitleMeta
import com.trackr.app.domain.util.SeasonProgress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One widget row; [source]/[externalId]/[mediaType] open the title, [canIncrement] shows the +1 button. */
data class UpNextRow(
    val source: String,
    val externalId: String,
    val mediaType: String,
    val title: String,
    val line: String,
    val canIncrement: Boolean = false,
) {
    /** The title's key, as [ListEntry.key] builds it. */
    val key: String get() = "$source:$externalId"
}

/** One cell of the hero's progress bar. */
enum class ProgressCell { WATCHED, TO_WATCH, UPCOMING }

/** The title the widget leads with: the episode the +1 marks, where you are in the show, and the progress bar. */
data class UpNextHero(
    val row: UpNextRow,
    /** What the +1 marks ("E14", "S2 E6"), or the next episode out of reach; null when there's neither. */
    val episode: String?,
    /** "Ep 13 of 28 · 1 to watch", "S2 · E5 · 25 to watch" or "Movie". */
    val status: String,
    /** One cell per episode, up to [UpNext.MAX_CELLS]; empty when the total isn't known. */
    val cells: List<ProgressCell>,
)

data class UpNextState(val airing: List<UpNextRow>, val watching: List<UpNextRow>, val hero: UpNextHero? = null) {
    val isEmpty: Boolean get() = airing.isEmpty() && watching.isEmpty()

    /** The airing rows, without the title the hero already leads with. */
    val coming: List<UpNextRow> get() = airing.filter { it.key != hero?.row?.key }
}

/** What the "Up next" widget shows: episodes dropping this week, then what you're in the middle of. */
object UpNext {
    const val MAX_AIRING = 4
    const val MAX_WATCHING = 6
    const val MAX_CELLS = 30
    private const val WINDOW_DAYS = 7L

    /** [meta] is keyed like [ListEntry.key]: season sizes and how many episodes are out, per title. */
    fun build(
        entries: List<ListEntry>,
        airing: List<AiringEntity>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
        meta: Map<String, TitleMeta> = emptyMap(),
    ): UpNextState {
        val startOfToday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val windowEnd = Instant.ofEpochMilli(startOfToday).atZone(zone).plusDays(WINDOW_DAYS).toInstant().toEpochMilli()
        val tracked = entries.associateBy { it.source.key to it.externalId }
        val airingRows = airing
            // A row can outlive its entry briefly (removed before the next airing refresh).
            .filter { it.airAt in startOfToday until windowEnd && (it.source to it.externalId) in tracked }
            .sortedBy { it.airAt }
            .take(MAX_AIRING)
            .map { a ->
                val label = whenLabel(a.airAt, a.precision == "DATE", now, zone, locale)
                UpNextRow(a.source, a.externalId, a.mediaType, a.title, listOfNotNull(a.episode?.let { e -> a.season?.let { "S$it E$e" } ?: "Ep $e" }, label).joinToString(" · "))
            }
        val nextAiring = airing.associateBy { it.source to it.externalId }
        val watchingEntries = entries
            .filter { it.status == ListStatus.WATCHING }
            .sortedByDescending { it.updatedAt }
            .take(MAX_WATCHING)
        val watchingRows = watchingEntries.map { e -> watchingRow(e, meta[e.key], nextAiring[e.source.key to e.externalId], now, zone, locale) }
        // The hero is the most recent title with an episode to mark, else simply the most recent title.
        val heroAt = watchingRows.indexOfFirst { it.canIncrement }.takeIf { it >= 0 } ?: watchingRows.indices.firstOrNull()
        val hero = heroAt?.let { i ->
            val e = watchingEntries[i]
            heroOf(e, watchingRows[i], meta[e.key], nextAiring[e.source.key to e.externalId], now)
        }
        return UpNextState(airingRows, watchingRows, hero)
    }

    /**
     * A title you're watching: the next episode to watch ("Next: S2 E5"), or once you've seen everything that's out,
     * when the next one airs ("S5 E1 · Next Fri") or "Caught up" when nothing is announced. No +1 while caught up, since
     * the next episode isn't out yet.
     */
    fun watchingRow(e: ListEntry, meta: TitleMeta?, next: AiringEntity?, now: Long, zone: ZoneId, locale: Locale): UpNextRow {
        val seasons = meta?.seasonEpisodes.orEmpty()
        fun episodeLabel(n: Int): String =
            SeasonProgress.position(n, seasons)?.takeIf { SeasonProgress.bySeason(seasons) }?.let { (s, ep) -> "S$s E$ep" } ?: "Ep $n"
        val total = e.totalEpisodes
        val caughtUp = airedEpisodes(meta, next, seasons, now)?.let { e.progress >= it } == true
        val allWatched = total != null && e.progress >= total
        val upcoming = next?.takeIf { it.airAt > now }
        // While nothing new is out there's nothing to +1.
        val canIncrement = e.mediaType != MediaType.MOVIE && !caughtUp && !allWatched
        val line = when {
            e.mediaType == MediaType.MOVIE -> "Movie"
            (caughtUp || allWatched) && upcoming != null -> {
                val ep = upcoming.episode?.let { n -> upcoming.season?.let { "S$it E$n" } ?: "Ep $n" }
                listOfNotNull(ep, whenLabel(upcoming.airAt, upcoming.precision == "DATE", now, zone, locale)).joinToString(" · ")
            }
            allWatched -> "Ep ${e.progress} of $total"
            caughtUp -> "Caught up"
            !SeasonProgress.bySeason(seasons) && total != null -> "Next: Ep ${e.progress + 1} of $total"
            else -> "Next: ${episodeLabel(e.progress + 1)}"
        }
        return UpNextRow(e.source.key, e.externalId, e.mediaType.key, e.title, line, canIncrement = canIncrement)
    }

    /**
     * The hero for [e], a row from [watchingRow]: the episode the +1 marks (or else the next one out of reach), the status
     * line and the progress bar. Movies have no progress.
     */
    fun heroOf(e: ListEntry, row: UpNextRow, meta: TitleMeta?, next: AiringEntity?, now: Long): UpNextHero {
        if (e.mediaType == MediaType.MOVIE) return UpNextHero(row, episode = null, status = "Movie", cells = emptyList())
        val seasons = meta?.seasonEpisodes.orEmpty()
        val total = e.totalEpisodes
        val aired = airedEpisodes(meta, next, seasons, now)
        val upcoming = next?.takeIf { it.airAt > now }?.let { u -> u.episode?.let { n -> u.season?.let { "S$it E$n" } ?: "E$n" } }
        val episode = if (row.canIncrement) episodeTag(e.progress + 1, seasons) else upcoming
        val where = if (e.progress == 0) "Not started" else SeasonProgress.label(e.progress, total, seasons)
        val toWatch = aired?.let { it - e.progress }?.takeIf { it > 0 }
        val status = listOfNotNull(where, toWatch?.let { "$it to watch" }).joinToString(" · ")
        val cells = total?.takeIf { it > 0 }?.let { progressCells(it, e.progress, aired) }.orEmpty()
        return UpNextHero(row, episode, status, cells)
    }

    /** "S2 E6" for a show with seasons, else "E14": anime and single-season shows number across the show. */
    private fun episodeTag(n: Int, seasons: List<Int>): String =
        SeasonProgress.position(n, seasons)?.takeIf { SeasonProgress.bySeason(seasons) }?.let { (s, ep) -> "S$s E$ep" } ?: "E$n"

    /**
     * The progress bar: one cell per episode, or per run of episodes past [maxCells]. A cell is watched once most of its
     * episodes are, to watch when some of them are out and not yet watched, and upcoming otherwise.
     */
    fun progressCells(total: Int, watched: Int, aired: Int?, maxCells: Int = MAX_CELLS): List<ProgressCell> {
        val count = minOf(total, maxCells)
        return (0 until count).map { i ->
            val start = i * total / count
            val end = (i + 1) * total / count
            val size = end - start
            val done = (minOf(watched, end) - start).coerceIn(0, size)
            val out = (minOf(aired ?: 0, end) - start).coerceIn(0, size)
            when {
                done * 2 >= size -> ProgressCell.WATCHED
                out > done -> ProgressCell.TO_WATCH
                else -> ProgressCell.UPCOMING
            }
        }
    }

    /**
     * Episodes out so far, counted across the show: from the next scheduled episode when there is one (it's the freshest:
     * the airing refresh runs more often than the title's details), else from the title's stored details. The higher
     * wins, since both only ever undercount. Null when neither says.
     */
    fun airedEpisodes(meta: TitleMeta?, next: AiringEntity?, seasons: List<Int>, now: Long): Int? {
        val fromSchedule = run {
            val n = next ?: return@run null
            val ep = n.episode ?: return@run null
            val season = n.season
            // TMDB numbers episodes within a season, AniList across the show.
            val overall = when {
                season == null -> ep
                seasons.isNotEmpty() -> SeasonProgress.absolute(season, ep, seasons)
                else -> return@run null
            }
            if (n.airAt <= now) overall else overall - 1
        }
        return listOfNotNull(fromSchedule, meta?.airedEpisodes).maxOrNull()
    }

    /**
     * "Today 9:00 PM", "Tomorrow", "Fri 9:00 PM" this week, "Next Fri" the week after, then "Oct 12"; date-only rows
     * (TMDB) drop the time.
     */
    fun whenLabel(airAt: Long, dateOnly: Boolean, now: Long, zone: ZoneId, locale: Locale): String {
        val at = Instant.ofEpochMilli(airAt).atZone(zone)
        val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), at.toLocalDate())
        if (days == 0L && airAt <= now) return "Out today"
        val day = when (days) {
            0L -> "Today"
            1L -> "Tomorrow"
            in 2L..6L -> at.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            in 7L..13L -> return "Next " + at.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            else -> return at.format(DateTimeFormatter.ofPattern("MMM d", locale))
        }
        return if (dateOnly) day else "$day ${at.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))}"
    }
}
