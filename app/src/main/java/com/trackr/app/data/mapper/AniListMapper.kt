package com.trackr.app.data.mapper

import com.trackr.app.anilist.AnimeDetailQuery
import com.trackr.app.anilist.fragment.MediaFields
import com.trackr.app.domain.model.CastMember
import com.trackr.app.domain.model.MediaDetail
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType

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
        )
    }
}
