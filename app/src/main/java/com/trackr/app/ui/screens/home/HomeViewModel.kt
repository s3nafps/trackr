package com.trackr.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.MediaRepository
import com.trackr.app.data.repository.userMessage
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.util.computeStreak
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeSection { MOVIES, TV, ANIME, AIRING }

data class HomeUiState(
    val sections: Map<HomeSection, Load<List<MediaItem>>> = HomeSection.entries.associateWith { Load.Loading },
    val continueWatching: List<ListEntry> = emptyList(),
    val listKeys: Set<String> = emptySet(),
    val streak: Int = 0,
    val refreshing: Boolean = false,
) {
    fun section(s: HomeSection): Load<List<MediaItem>> = sections[s] ?: Load.Loading
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val media: MediaRepository,
    private val lists: ListRepository,
) : ViewModel() {
    private data class Remote(
        val sections: Map<HomeSection, Load<List<MediaItem>>> = HomeSection.entries.associateWith { Load.Loading },
        val refreshing: Boolean = false,
    )

    private val remote = MutableStateFlow(Remote())

    val state: StateFlow<HomeUiState> = combine(remote, lists.entries) { r, entries ->
        HomeUiState(
            sections = r.sections,
            continueWatching = entries.filter { it.status == ListStatus.WATCHING }.sortedByDescending { it.updatedAt },
            listKeys = entries.map { it.key }.toSet(),
            streak = computeStreak(entries.map { it.updatedAt }),
            refreshing = r.refreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init { loadAll(force = false) }

    fun refresh() = loadAll(force = true)

    fun retry(section: HomeSection) {
        setSection(section, Load.Loading)
        viewModelScope.launch { loadSection(section, force = true) }
    }

    private fun setSection(s: HomeSection, v: Load<List<MediaItem>>) =
        remote.update { it.copy(sections = it.sections + (s to v)) }

    private suspend fun loadSection(s: HomeSection, force: Boolean) {
        val result = try {
            Load.Success(
                when (s) {
                    HomeSection.MOVIES -> media.trendingMovies(force)
                    HomeSection.TV -> media.trendingTv(force)
                    HomeSection.ANIME -> media.trendingAnime(force)
                    HomeSection.AIRING -> media.airingThisWeek(force)
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Failure(e.userMessage())
        }
        setSection(s, result)
    }

    private fun loadAll(force: Boolean) {
        viewModelScope.launch {
            remote.update { it.copy(refreshing = force) }
            // Sections load in parallel and each one appears as soon as it is ready.
            val jobs: List<Job> = HomeSection.entries.map { s -> launch { loadSection(s, force) } }
            jobs.joinAll()
            remote.update { it.copy(refreshing = false) }
        }
    }

    fun quickAdd(item: MediaItem) {
        viewModelScope.launch { lists.save(item, ListStatus.PLAN_TO_WATCH, null, 0) }
    }

    fun plusOne(entry: ListEntry) {
        viewModelScope.launch { lists.incrementProgress(entry) }
    }
}
