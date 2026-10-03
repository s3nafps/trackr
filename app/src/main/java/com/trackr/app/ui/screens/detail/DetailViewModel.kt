package com.trackr.app.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.local.UserPrefs
import com.trackr.app.data.remote.supabase.ActivityDto
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.MediaRepository
import com.trackr.app.data.repository.SocialRepository
import com.trackr.app.data.repository.userMessage
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.MediaDetail
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val detail: Load<MediaDetail> = Load.Loading,
    val entry: ListEntry? = null,
    val friends: List<ActivityDto> = emptyList(),
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val media: MediaRepository,
    private val lists: ListRepository,
    private val social: SocialRepository,
    prefs: UserPrefs,
) : ViewModel() {
    val source: MediaSource = MediaSource.fromKey(saved.get<String>("source").orEmpty())
    val type: MediaType = MediaType.fromKey(saved.get<String>("type").orEmpty())
    val id: String = saved.get<String>("id").orEmpty()

    private val detail = MutableStateFlow<Load<MediaDetail>>(Load.Loading)
    private val friends = MutableStateFlow<List<ActivityDto>>(emptyList())

    val state: StateFlow<DetailUiState> = combine(detail, lists.entry(source.key, id), friends) { d, e, f ->
        DetailUiState(d, e, f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    private val permissionDenied = MutableStateFlow(false)

    /** Watching titles always notify; Plan to Watch needs the bell. Hidden when it cannot work. */
    val bellState: StateFlow<BellState> = combine(lists.entry(source.key, id), prefs.airingEnabled, permissionDenied) { e, enabled, denied ->
        when {
            !enabled || denied || e == null -> BellState.Hidden
            e.status == ListStatus.WATCHING -> BellState.On
            e.status == ListStatus.PLAN_TO_WATCH -> if (e.notify) BellState.On else BellState.Off
            else -> BellState.Hidden
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BellState.Hidden)

    init { load() }

    fun onPermissionResult(granted: Boolean) { permissionDenied.value = !granted }

    /** Only Plan to Watch is toggleable; Watching is always on. */
    fun toggleBell() {
        viewModelScope.launch {
            val e = lists.entry(source.key, id).first() ?: return@launch
            if (e.status == ListStatus.PLAN_TO_WATCH) lists.setNotify(e, !e.notify)
        }
    }

    fun load(force: Boolean = false) {
        detail.value = Load.Loading
        viewModelScope.launch {
            detail.value = try {
                Load.Success(media.detail(source, id, type, force))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failure(e.userMessage())
            }
        }
        viewModelScope.launch {
            // Friends row is a nice-to-have: failures (offline, signed out) just hide it.
            friends.update { runCatching { social.friendsWhoTracked(source.key, id) }.getOrDefault(emptyList()) }
        }
    }

    fun save(status: ListStatus, rating: Int?, progress: Int) {
        val item = (detail.value as? Load.Success)?.data?.item ?: return
        viewModelScope.launch { lists.save(item, status, rating, progress) }
    }

    fun remove() {
        val e = state.value.entry ?: return
        viewModelScope.launch { lists.remove(e) }
    }
}
