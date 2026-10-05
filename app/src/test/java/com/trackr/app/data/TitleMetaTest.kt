package com.trackr.app.data

import com.trackr.app.data.local.TitleMetaDao
import com.trackr.app.data.local.TitleMetaEntity
import com.trackr.app.data.meta.TitleMetaRepository
import com.trackr.app.data.repository.MediaRepository
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaDetail
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.SeasonInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class TitleMetaTest {
    private class FakeDao : TitleMetaDao {
        val rows = MutableStateFlow<Map<Pair<String, String>, TitleMetaEntity>>(emptyMap())
        override fun observeAll() = rows.map { it.values.toList() }
        override suspend fun getAll() = rows.value.values.toList()
        override suspend fun get(source: String, id: String) = rows.value[source to id]
        override suspend fun upsert(entity: TitleMetaEntity) { rows.value = rows.value + ((entity.source to entity.externalId) to entity) }
        override suspend fun clear() { rows.value = emptyMap() }
    }

    private val dao = FakeDao()
    private val media = mockk<MediaRepository>()
    private val repo = TitleMetaRepository(dao, media)

    private fun entry(id: String, type: MediaType, status: ListStatus) =
        ListEntry(MediaSource.TMDB, id, type, "T$id", null, null, status, null, 0, null, 0)
    private fun detail(id: String, type: MediaType, genres: List<String>, seasons: List<SeasonInfo> = emptyList()) =
        MediaDetail(MediaItem(MediaSource.TMDB, id, type, "T$id", null, genres = genres), seasons = seasons)
    private fun season(n: Int, eps: Int) = SeasonInfo(n, "Season $n", eps, null, null)

    @Test fun `backfill stores genres and regular seasons, skipping specials and failures`() = runTest {
        coEvery { media.detail(MediaSource.TMDB, "1", MediaType.TV, any(), any()) } returns
            detail("1", MediaType.TV, listOf("Drama", "Sci-Fi & Fantasy"), listOf(season(0, 3), season(2, 8), season(1, 10)))
        coEvery { media.detail(MediaSource.TMDB, "2", MediaType.MOVIE, any(), any()) } throws IOException("offline")

        val remaining = repo.backfill(listOf(entry("1", MediaType.TV, ListStatus.WATCHING), entry("2", MediaType.MOVIE, ListStatus.COMPLETED)), now = 100)

        assertEquals(0, remaining)
        val meta = repo.all.first()
        assertEquals(listOf("Drama", "Sci-Fi & Fantasy"), meta["tmdb:1"]!!.genres)
        assertEquals(listOf(10, 8), meta["tmdb:1"]!!.seasonEpisodes)
        assertEquals(null, meta["tmdb:2"])
    }

    @Test fun `stored titles aren't fetched again, except watched TV shows after a week`() = runTest {
        coEvery { media.detail(any(), any(), any(), any(), any()) } answers {
            detail(secondArg(), thirdArg(), listOf("Drama"), listOf(season(1, 10)))
        }
        val entries = listOf(entry("1", MediaType.TV, ListStatus.WATCHING), entry("2", MediaType.TV, ListStatus.COMPLETED))
        repo.backfill(entries, now = 0)
        repo.backfill(entries, now = TitleMetaRepository.SEASON_REFRESH_MS - 1)
        coVerify(exactly = 1) { media.detail(MediaSource.TMDB, "1", any(), any(), any()) }

        repo.backfill(entries, now = TitleMetaRepository.SEASON_REFRESH_MS + 1)
        coVerify(exactly = 2) { media.detail(MediaSource.TMDB, "1", any(), any(), any()) }
        coVerify(exactly = 1) { media.detail(MediaSource.TMDB, "2", any(), any(), any()) }
    }

    @Test fun `a long list is done in batches`() = runTest {
        coEvery { media.detail(any(), any(), any(), any(), any()) } answers { detail(secondArg(), thirdArg(), listOf("Drama")) }
        val entries = (1..5).map { entry("$it", MediaType.MOVIE, ListStatus.COMPLETED) }
        assertEquals(3, repo.backfill(entries, now = 1, max = 2))
        assertEquals(1, repo.backfill(entries, now = 1, max = 2))
        assertEquals(0, repo.backfill(entries, now = 1, max = 2))
    }
}
