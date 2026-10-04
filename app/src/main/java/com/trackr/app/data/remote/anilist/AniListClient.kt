package com.trackr.app.data.remote.anilist

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.trackr.app.anilist.AiringThisWeekQuery
import com.trackr.app.anilist.AnimeByMalIdsQuery
import com.trackr.app.anilist.AnimeDetailQuery
import com.trackr.app.anilist.SearchAnimeQuery
import com.trackr.app.anilist.TrendingAnimeQuery
import com.trackr.app.anilist.UserAnimeListQuery
import com.trackr.app.anilist.fragment.MediaFields
import com.trackr.app.data.importer.fuzzyDateMillis
import com.trackr.app.data.mapper.AniListMapper
import com.trackr.app.domain.model.MediaDetail
import com.trackr.app.domain.model.MediaItem
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Thin wrapper over Apollo that unwraps GraphQL errors into IOExceptions and maps to domain models. */
@Singleton
class AniListClient @Inject constructor(private val apollo: ApolloClient) {

    private fun <D : com.apollographql.apollo.api.Operation.Data> com.apollographql.apollo.api.ApolloResponse<D>.dataOrThrow(): D {
        exception?.let { throw IOException(it.message ?: "AniList request failed", it) }
        val d = data
        if (d == null) throw IOException(errors?.firstOrNull()?.message ?: "AniList returned no data")
        return d
    }

    suspend fun trending(perPage: Int = 20): List<MediaItem> =
        apollo.query(TrendingAnimeQuery(perPage = Optional.present(perPage))).execute().dataOrThrow()
            .Page?.media.orEmpty().filterNotNull().filter { it.mediaFields.isAdult != true }
            .map { AniListMapper.toItem(it.mediaFields) }

    suspend fun airingThisWeek(fromEpoch: Long, toEpoch: Long): List<MediaItem> =
        apollo.query(AiringThisWeekQuery(fromEpoch.toInt(), toEpoch.toInt())).execute().dataOrThrow()
            .Page?.airingSchedules.orEmpty().filterNotNull()
            .mapNotNull { s -> s.media?.mediaFields?.takeIf { it.isAdult != true }?.let { AniListMapper.toItem(it, s.episode, s.airingAt.toLong()) } }
            .distinctBy { it.externalId }

    suspend fun search(query: String, perPage: Int = 20): List<MediaItem> =
        apollo.query(SearchAnimeQuery(search = query, perPage = Optional.present(perPage))).execute().dataOrThrow()
            .Page?.media.orEmpty().filterNotNull().filter { it.mediaFields.isAdult != true }
            .map { AniListMapper.toItem(it.mediaFields) }

    suspend fun detail(id: Int): MediaDetail {
        val media = apollo.query(AnimeDetailQuery(id)).execute().dataOrThrow().Media
            ?: throw IOException("Title not found")
        return AniListMapper.toDetail(media)
    }

    /** One entry of a user's list: raw AniList status, score on a 0–10 scale (0 = unrated), episodes seen, completion date. */
    data class ListRow(val item: MediaItem, val status: String?, val score: Double?, val progress: Int, val completedAt: Long? = null)

    /** A user's public anime list, all chunks. Custom lists repeat entries from the status lists, so they're skipped. */
    suspend fun userAnimeList(userName: String): List<ListRow> {
        val rows = ArrayList<ListRow>()
        var chunk = 1
        while (true) {
            val c = apollo.query(UserAnimeListQuery(userName, chunk)).execute().dataOrThrow().MediaListCollection
                ?: throw IOException("AniList user not found")
            c.lists.orEmpty().filterNotNull().filter { it.isCustomList != true }.forEach { list ->
                list.entries.orEmpty().filterNotNull().forEach { e ->
                    e.media?.mediaFields?.let {
                        val done = e.completedAt?.let { d -> fuzzyDateMillis(d.year, d.month, d.day) }
                        rows += ListRow(AniListMapper.toItem(it), e.status?.rawValue, e.score, e.progress ?: 0, done)
                    }
                }
            }
            if (c.hasNextChunk != true || chunk >= MAX_CHUNKS) break
            chunk++
        }
        return rows
    }

    /** AniList titles for MyAnimeList ids (50 per request); ids AniList doesn't know are absent from the map. */
    suspend fun byMalIds(ids: Collection<Int>): Map<Int, MediaItem> = buildMap {
        ids.distinct().chunked(50).forEach { batch ->
            apollo.query(AnimeByMalIdsQuery(batch)).execute().dataOrThrow().Page?.media.orEmpty().filterNotNull().forEach { m ->
                m.idMal?.let { put(it, AniListMapper.toItem(m.mediaFields)) }
            }
        }
    }

    private companion object {
        /** 500 per chunk; anything past 10,000 entries isn't a real list. */
        const val MAX_CHUNKS = 20
    }
}
