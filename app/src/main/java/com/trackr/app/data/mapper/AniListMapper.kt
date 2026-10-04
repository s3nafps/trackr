package com.trackr.app.data.mapper

import com.trackr.app.anilist.AnimeDetailQuery
import com.trackr.app.anilist.fragment.MediaFields
import com.trackr.app.domain.model.CastMember
import com.trackr.app.domain.model.MediaDetail
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.RelatedItem
import com.trackr.app.domain.model.Trailer
import com.trackr.app.domain.model.WatchOptions
import com.trackr.app.domain.model.WatchProvider

object AniListMapper {
    private val tagRegex = Regex("<[^>]*>")

    fun cleanDescription(raw: String?): String? =
        raw?.replace("<br>", "\n")?.replace("<br/>", "\n")?.replace(tagRegex, "")
            ?.replace("&quot;", "\"")?.replace("&amp;", "&")?.replace("&#039;", "'")
            ?.trim()?.takeIf { it.isNotEmpty() }

    private fun formatLabel(f: com.trackr.app.anilist.type.MediaFormat?): String? = when (f?.rawValue) {
        "TV" -> "TV"; "TV_SHORT" -> "TV Short"; "MOVIE" -> "Movie"; "OVA" -> "OVA"; "ONA" -> "ONA"
        "SPECIAL" -> "Special"; "MUSIC" -> "Music"; else -> null
    }

    fun toItem(m: MediaFields, airingEpisode: Int? = null, airingAt: Long? = null): MediaItem {
        val studio = m.studios?.nodes?.firstOrNull()?.name
        val episodes = m.episodes?.takeIf { it > 0 }
        return MediaItem(
            source = MediaSource.ANILIST,
            externalId = m.id.toString(),
            type = MediaType.ANIME,
            title = m.title?.english?.takeIf { it.isNotBlank() } ?: m.title?.romaji ?: "Untitled",
            posterUrl = m.coverImage?.extraLarge ?: m.coverImage?.large,
            backdropUrl = m.bannerImage,
            year = m.seasonYear ?: m.startDate?.year,
            score = m.averageScore?.takeIf { it > 0 }?.let { it / 10.0 },
            genres = m.genres.orEmpty().filterNotNull(),
            totalEpisodes = episodes,
            runtimeMinutes = m.duration?.takeIf { it > 0 },
            subtitle = listOfNotNull(studio, formatLabel(m.format)).takeIf { it.isNotEmpty() }?.joinToString(" · "),
            airingEpisode = airingEpisode ?: m.nextAiringEpisode?.episode,
            airingAtEpoch = airingAt ?: m.nextAiringEpisode?.airingAt?.toLong(),
        )
    }

    fun toDetail(m: AnimeDetailQuery.Media): MediaDetail {
        val base = toItem(m.mediaFields).copy(overview = cleanDescription(m.description))
        val cast = m.characters?.edges.orEmpty().filterNotNull().mapNotNull { e ->
            val name = e.node?.name?.full ?: return@mapNotNull null
            CastMember(name, e.role?.rawValue?.lowercase()?.replaceFirstChar { it.uppercase() }, e.node.image?.medium)
        }
        return MediaDetail(
            item = base,
            status = m.mediaFields.status?.rawValue?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() },
            cast = cast,
            studios = m.mediaFields.studios?.nodes.orEmpty().filterNotNull().map { it.name },
            watch = watchOptions(
                m.externalLinks.orEmpty().filterNotNull().map {
                    ExternalLink(it.site, it.url, it.type?.rawValue, it.icon, it.color, it.isDisabled == true)
                },
            ),
            trailer = m.trailer?.let { trailer(it.id, it.site, it.thumbnail) },
            related = relatedItems(
                m.relations?.edges.orEmpty().filterNotNull().mapNotNull { e ->
                    val node = e.node ?: return@mapNotNull null
                    // Relations also point at manga and novels, which can't be tracked here.
                    if (node.type?.rawValue != "ANIME" || node.mediaFields.isAdult == true) return@mapNotNull null
                    e.relationType?.rawValue to toItem(node.mediaFields)
                },
            ),
            recommendations = m.recommendations?.nodes.orEmpty().mapNotNull { it?.mediaRecommendation?.mediaFields }
                .filter { it.isAdult != true && it.id != m.mediaFields.id }
                .map { toItem(it) }
                .distinctBy { it.key },
        )
    }

    fun trailer(id: String?, site: String?, thumbnail: String?): Trailer? = when {
        id.isNullOrBlank() -> null
        site == "youtube" -> Trailer.youtube(id)
        site == "dailymotion" -> Trailer("https://www.dailymotion.com/video/$id", thumbnail)
        else -> null
    }

    /** Story order first, so prequels and sequels lead; anything unknown goes last. */
    private val relationLabels = linkedMapOf(
        "PREQUEL" to "Prequel", "SEQUEL" to "Sequel", "PARENT" to "Parent story", "SIDE_STORY" to "Side story",
        "SPIN_OFF" to "Spin-off", "ALTERNATIVE" to "Alternative", "SUMMARY" to "Summary", "COMPILATION" to "Compilation",
        "SOURCE" to "Source", "ADAPTATION" to "Adaptation", "CHARACTER" to "Shared characters", "OTHER" to "Other",
    )
    private val relationOrder = relationLabels.keys.toList()

    /** [edges] are (AniList relation type, item) pairs. */
    fun relatedItems(edges: List<Pair<String?, MediaItem>>): List<RelatedItem> =
        edges.distinctBy { it.second.key }
            .sortedBy { (r, _) -> relationOrder.indexOf(r).let { if (it < 0) relationOrder.size else it } }
            .map { (r, item) -> RelatedItem(relationLabels[r] ?: "Related", item) }

    /** Plain copy of AniList's MediaExternalLink so the mapping is testable without Apollo types. */
    data class ExternalLink(
        val site: String, val url: String?, val type: String?, val icon: String?, val color: String?, val disabled: Boolean,
    )

    /** AniList only lists streaming sites (no rent/buy, no region), one link each. */
    fun watchOptions(links: List<ExternalLink>) = WatchOptions(
        stream = links.filter { it.type == "STREAMING" && !it.disabled && !it.url.isNullOrBlank() }
            .distinctBy { it.site }
            .map { WatchProvider(it.site, it.icon, it.url, it.color) },
    )
}
