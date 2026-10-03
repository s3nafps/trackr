package com.trackr.app.data.remote.anilist

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.trackr.app.anilist.AiringThisWeekQuery
import com.trackr.app.anilist.AnimeDetailQuery
import com.trackr.app.anilist.SearchAnimeQuery
import com.trackr.app.anilist.TrendingAnimeQuery
import com.trackr.app.anilist.fragment.MediaFields
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
}
