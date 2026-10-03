package com.trackr.app.ui.screens.friends

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.repository.FriendsRepository
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.ProfileRepository
import com.trackr.app.data.repository.userMessage
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.Profile
import com.trackr.app.domain.model.ProfileStats
import com.trackr.app.domain.model.StatsCalculator
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

data class FriendData(val profile: Profile, val entries: List<ListEntry>, val friendshipId: String?)

data class FriendProfileUiState(
    val data: Load<FriendData> = Load.Loading,
    val stats: ProfileStats? = null,
    val together: List<ListEntry> = emptyList(),
    val removed: Boolean = false,
)

@HiltViewModel
class FriendProfileViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val friends: FriendsRepository,
    private val profiles: ProfileRepository,
    lists: ListRepository,
) : ViewModel() {
    private val userId: String = saved.get<String>("userId").orEmpty()
    private val data = MutableStateFlow<Load<FriendData>>(Load.Loading)
    private val removed = MutableStateFlow(false)

    val state: StateFlow<FriendProfileUiState> = combine(data, lists.entries, removed) { d, mine, rem ->
        val entries = (d as? Load.Success)?.data?.entries
        FriendProfileUiState(
            data = d,
            stats = entries?.let { StatsCalculator.compute(it) },
            together = entries?.let { StatsCalculator.overlap(mine, it) }.orEmpty(),
            removed = rem,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendProfileUiState())

    init { load() }

    fun load() {
        data.value = Load.Loading
        viewModelScope.launch {
            data.value = try {
                val profile = profiles.getProfiles(listOf(userId)).firstOrNull() ?: throw IllegalStateException("User not found")
                val entries = friends.entriesOf(userId)
                val friendshipId = friends.friends().firstOrNull { it.profile.id == userId }?.friendshipId
                Load.Success(FriendData(profile, entries, friendshipId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failure(e.userMessage())
            }
        }
    }

    fun removeFriend() {
        val id = (data.value as? Load.Success)?.data?.friendshipId ?: return
        viewModelScope.launch {
            runCatching { friends.delete(id) }.onSuccess { removed.update { true } }
        }
    }
}
