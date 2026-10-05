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
)

data class UpNextState(val airing: List<UpNextRow>, val watching: List<UpNextRow>) {
    val isEmpty: Boolean get() = airing.isEmpty() && watching.isEmpty()
}

/** What the "Up next" widget shows: episodes dropping this week, then what you're in the middle of. */
object UpNext {
    const val MAX_AIRING = 4
    const val MAX_WATCHING = 6
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
        val watchingRows = entries
            .filter { it.status == ListStatus.WATCHING }
            .sortedByDescending { it.updatedAt }
            .take(MAX_WATCHING)
            .map { e -> watchingRow(e, meta[e.key], nextAiring[e.source.key to e.externalId], now, zone, locale) }
        return UpNextState(airingRows, watchingRows)
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
