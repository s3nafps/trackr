package com.trackr.app.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {
    val source: MediaSource = MediaSource.fromKey(saved.get<String>("source").orEmpty())
    val type: MediaType = MediaType.fromKey(saved.get<String>("type").orEmpty())
    val id: String = saved.get<String>("id").orEmpty()

    private val detail = MutableStateFlow<Load<MediaDetail>>(Load.Loading)
    private val friends = MutableStateFlow<List<ActivityDto>>(emptyList())

    val state: StateFlow<DetailUiState> = combine(detail, lists.entry(source.key, id), friends) { d, e, f ->
        DetailUiState(d, e, f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    init { load() }

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
