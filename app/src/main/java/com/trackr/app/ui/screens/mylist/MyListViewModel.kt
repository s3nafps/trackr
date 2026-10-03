package com.trackr.app.ui.screens.mylist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ListSort(val label: String) {
    RECENT("Recently Updated"), TITLE("Title A–Z"), RATING("Highest Rated"), PROGRESS("Progress")
}

data class MyListUiState(
    val status: ListStatus = ListStatus.WATCHING,
    val type: MediaType? = null,
    val sort: ListSort = ListSort.RECENT,
    val grid: Boolean = false,
    val items: List<ListEntry> = emptyList(),
    val statusCounts: Map<ListStatus, Int> = emptyMap(),
    val typeCounts: Map<MediaType?, Int> = emptyMap(),
    val total: Int = 0,
    val refreshing: Boolean = false,
    val message: String? = null,
)

/** Pure filter/sort so it can be unit-tested. */
fun applyListView(all: List<ListEntry>, status: ListStatus, type: MediaType?, sort: ListSort): List<ListEntry> {
    val filtered = all.filter { it.status == status && (type == null || it.mediaType == type) }
    return when (sort) {
        ListSort.RECENT -> filtered.sortedByDescending { it.updatedAt }
        ListSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
        ListSort.RATING -> filtered.sortedWith(compareByDescending<ListEntry> { it.rating ?: 0 }.thenBy { it.title.lowercase() })
        ListSort.PROGRESS -> filtered.sortedByDescending { it.fraction }
    }
}

@HiltViewModel
class MyListViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val repo: ListRepository,
) : ViewModel() {
    private val status = MutableStateFlow(
        saved.get<String>("status")?.let { ListStatus.fromKey(it) } ?: ListStatus.WATCHING,
    )
    private val type = MutableStateFlow<MediaType?>(saved.get<String>("t")?.let { MediaType.fromKey(it) })
    private val sort = MutableStateFlow(saved.get<String>("s")?.let { runCatching { ListSort.valueOf(it) }.getOrNull() } ?: ListSort.RECENT)
    private val grid = MutableStateFlow(saved.get<Boolean>("g") ?: false)
    private val sync = MutableStateFlow<Pair<Boolean, String?>>(false to null)

    val state: StateFlow<MyListUiState> = combine(repo.entries, status, type, sort, combine(grid, sync) { g, s -> g to s }) { all, st, ty, so, (g, sy) ->
        val inStatus = all.filter { it.status == st }
        MyListUiState(
            status = st, type = ty, sort = so, grid = g,
            items = applyListView(all, st, ty, so),
            statusCounts = ListStatus.entries.associateWith { s -> all.count { it.status == s } },
            typeCounts = buildMap {
                put(null, inStatus.size)
                MediaType.entries.forEach { m -> put(m, inStatus.count { it.mediaType == m }) }
            },
            total = all.size, refreshing = sy.first, message = sy.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyListUiState(status = status.value))

    init { refresh(silent = true) }

    fun setStatus(s: ListStatus) { status.value = s; saved["status"] = s.key }
    fun setType(t: MediaType?) { type.value = t; saved["t"] = t?.key }
    fun setSort(s: ListSort) { sort.value = s; saved["s"] = s.name }
    fun toggleGrid() { grid.update { !it }; saved["g"] = grid.value }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) sync.value = true to null
            sync.value = try {
                repo.sync()
                false to null
            } catch (e: Exception) {
                false to if (silent) null else "Couldn't sync. Showing your saved list; changes will sync when you're online."
            }
        }
    }

    fun plusOne(e: ListEntry) { viewModelScope.launch { repo.incrementProgress(e) } }
    fun remove(e: ListEntry) { viewModelScope.launch { repo.remove(e) } }
    fun save(e: ListEntry, s: ListStatus, rating: Int?, progress: Int) { viewModelScope.launch { repo.update(e, s, rating, progress) } }
    fun dismissMessage() { sync.update { it.first to null } }
}
