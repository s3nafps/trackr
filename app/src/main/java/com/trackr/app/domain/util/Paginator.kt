package com.trackr.app.domain.util

import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a paged list shows: the titles so far, and whether the next page is loading, failed or doesn't exist. */
data class PageState(
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = false,
    /** Why the last page failed; loading stops until [Paginator.retry]. */
    val error: String? = null,
    val endReached: Boolean = false,
    /** Pages loaded since the last reset; 0 until the first one arrives. */
    val pages: Int = 0,
) {
    /** Nothing to show yet because the first page hasn't arrived. */
    val firstLoad: Boolean get() = items.isEmpty() && pages == 0 && error == null && !endReached

    /** Page 1 is reloading while the previous titles stay on screen. */
    val refreshing: Boolean get() = loading && pages == 0 && items.isNotEmpty()

    val canLoadMore: Boolean get() = !loading && error == null && !endReached

    companion object {
        fun of(items: List<MediaItem>, hasMore: Boolean = false) = PageState(items, pages = 1, endReached = !hasMore)
    }
}

/**
 * Loads numbered pages one at a time and appends them, skipping titles already shown: trending lists shift between
 * requests, and lazy lists need unique keys. [reset] starts again from page 1, e.g. for a new query or a refresh.
 */
class Paginator(
    private val scope: CoroutineScope,
    private val errorMessage: (Throwable) -> String,
    private var fetch: suspend (page: Int, force: Boolean) -> MediaPage,
) {
    private val _state = MutableStateFlow(PageState())
    val state: StateFlow<PageState> = _state.asStateFlow()

    private var job: Job? = null
    private var force = false
    /** Bumped by [reset]/[clear] so a superseded request can't write its result. */
    private var generation = 0

    /** Loads the next page, unless one is already loading, the last one failed (see [retry]) or there are no more. */
    fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        val page = current.pages + 1
        val gen = generation
        _state.update { it.copy(loading = true) }
        job = scope.launch {
            val next = try {
                fetch(page, force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generation) _state.update { it.copy(loading = false, error = errorMessage(e)) }
                return@launch
            }
            if (gen != generation) return@launch
            _state.update { s ->
                // Page 1 replaces whatever a refresh kept on screen.
                val base = if (page == 1) emptyList() else s.items
                val seen = base.mapTo(HashSet()) { it.key }
                s.copy(items = base + next.items.filter { seen.add(it.key) }, loading = false, pages = page, endReached = !next.hasMore)
            }
        }
    }

    /** Tries the failed page again; does nothing if nothing failed. */
    fun retry() {
        if (_state.value.error == null) return
        _state.update { it.copy(error = null) }
        loadMore()
    }

    /**
     * Starts again from page 1, with [newFetch] as the source when given. [force] bypasses caches for every page of
     * this run; with [keepItems] the current titles stay visible until page 1 replaces them (pull to refresh).
     */
    fun reset(force: Boolean = false, keepItems: Boolean = false, newFetch: (suspend (page: Int, force: Boolean) -> MediaPage)? = null) {
        job?.cancel()
        generation++
        newFetch?.let { fetch = it }
        this.force = force
        _state.value = PageState(items = if (keepItems) _state.value.items else emptyList())
        loadMore()
    }

    /** Empties the list without loading anything (e.g. the search box was cleared). */
    fun clear() {
        job?.cancel()
        generation++
        _state.value = PageState.of(emptyList())
    }
}
