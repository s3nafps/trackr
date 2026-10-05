package com.trackr.app.ui.screens.social

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.realtime.LiveFollower
import com.trackr.app.data.realtime.LiveTables
import com.trackr.app.data.realtime.LiveUpdates
import com.trackr.app.data.repository.FriendsRepository
import com.trackr.app.data.repository.SharedListsRepository
import com.trackr.app.data.repository.SocialException
import com.trackr.app.data.repository.userMessage
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.Profile
import com.trackr.app.domain.model.SharedList
import com.trackr.app.domain.model.SharedListItem
import com.trackr.app.domain.util.GroupPick
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private suspend fun <T> attempt(block: suspend () -> T): Load<T> = try {
    Load.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Load.Failure(e.userMessage())
}

private fun Exception.friendlyMessage() = (this as? SocialException)?.message ?: userMessage()

// ---------------------------------------------------------------- all lists

/** The "New shared list" dialog: friends to pick from, and whether it's saving. */
data class CreateListState(val friends: Load<List<Profile>> = Load.Loading, val busy: Boolean = false, val error: String? = null)

data class SharedListsUiState(
    val lists: Load<List<SharedList>> = Load.Loading,
    val refreshing: Boolean = false,
    val creating: CreateListState? = null,
    /** Set right after creating a list so the screen opens it. */
    val openList: String? = null,
    val toast: String? = null,
)

@HiltViewModel
class SharedListsViewModel @Inject constructor(
    private val repo: SharedListsRepository,
    private val friends: FriendsRepository,
    live: LiveUpdates,
) : ViewModel() {
    private val _state = MutableStateFlow(SharedListsUiState())
    val state: StateFlow<SharedListsUiState> = _state.asStateFlow()

    private val liveFollower = LiveFollower(live, LiveTables.SHARED_LIST_MEMBERS, LiveTables.SHARED_LIST_ITEMS)

    init { refresh() }

    fun refresh() = load(silent = false)

    /** While visible: a list a friend adds you to, or new titles in your lists, show up without a refresh. */
    suspend fun followLiveUpdates() = liveFollower.follow { load(silent = true) }

    private fun load(silent: Boolean) {
        viewModelScope.launch {
            if (!silent) _state.update { it.copy(refreshing = true) }
            val lists = attempt { repo.lists() }
            _state.update { s -> s.copy(lists = if (silent && lists is Load.Failure && s.lists is Load.Success) s.lists else lists, refreshing = false) }
        }
    }

    fun openCreate() {
        _state.update { it.copy(creating = CreateListState()) }
        viewModelScope.launch {
            val list = attempt { friends.friends().map { it.profile } }
            _state.update { s -> s.copy(creating = s.creating?.copy(friends = list)) }
        }
    }

    fun closeCreate() = _state.update { it.copy(creating = null) }

    fun create(name: String, friendIds: Set<String>) {
        if (_state.value.creating?.busy == true) return
        _state.update { s -> s.copy(creating = s.creating?.copy(busy = true, error = null)) }
        viewModelScope.launch {
            try {
                val (id, failed) = repo.create(name, friendIds)
                _state.update {
                    it.copy(
                        creating = null, openList = id,
                        toast = if (failed.isEmpty()) null else "Couldn't add ${failed.size} of the friends you picked.",
                    )
                }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s -> s.copy(creating = s.creating?.copy(busy = false, error = e.friendlyMessage())) }
            }
        }
    }

    fun listOpened() = _state.update { it.copy(openList = null) }
    fun toastShown() = _state.update { it.copy(toast = null) }
}

// ---------------------------------------------------------------- one list

/** The members sheet: who's in, and (for the owner) friends to add. */
data class MembersState(val friends: Load<List<Profile>> = Load.Loading, val busy: String? = null)

data class SharedListUiState(
    val list: Load<SharedList> = Load.Loading,
    val items: Load<List<SharedListItem>> = Load.Loading,
    val me: String? = null,
    val pick: SharedListItem? = null,
    val members: MembersState? = null,
    val toast: String? = null,
    /** Left or deleted: the screen should close. */
    val closed: Boolean = false,
) {
    val isOwner: Boolean get() = (list as? Load.Success)?.data?.ownerId == me && me != null
}

@HiltViewModel
class SharedListViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val repo: SharedListsRepository,
    private val friends: FriendsRepository,
    live: LiveUpdates,
) : ViewModel() {
    val listId: String = saved.get<String>("id").orEmpty()

    private val _state = MutableStateFlow(SharedListUiState(me = repo.currentUserId))
    val state: StateFlow<SharedListUiState> = _state.asStateFlow()

    private val liveFollower = LiveFollower(live, LiveTables.SHARED_LIST_ITEMS, LiveTables.SHARED_LIST_MEMBERS)

    init { refresh() }

    fun refresh() = load(silent = false)

    /** While visible: titles and members other people add or remove appear without a refresh. */
    suspend fun followLiveUpdates() = liveFollower.follow { load(silent = true) }

    /** A silent reload keeps what's on screen if a request fails. */
    private fun load(silent: Boolean) {
        viewModelScope.launch {
            val list = attempt { repo.list(listId) ?: throw SocialException("This list no longer exists, or you're not in it.") }
            val items = attempt { repo.items(listId) }
            _state.update {
                if (silent && (list is Load.Failure || items is Load.Failure)) it else it.copy(list = list, items = items)
            }
        }
    }

    fun removeItem(item: SharedListItem) {
        val before = _state.value.items
        _state.update { s -> s.copy(items = (s.items as? Load.Success)?.let { Load.Success(it.data - item) } ?: s.items) }
        viewModelScope.launch {
            try {
                repo.removeItem(listId, item.item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(items = before, toast = e.friendlyMessage()) }
            }
        }
    }

    // ----- pick for us -----

    fun pickForUs() {
        val items = (_state.value.items as? Load.Success)?.data ?: return
        _state.update { it.copy(pick = GroupPick.pick(items, previous = it.pick)) }
    }

    fun closePick() = _state.update { it.copy(pick = null) }

    // ----- members -----

    fun openMembers() {
        _state.update { it.copy(members = MembersState()) }
        viewModelScope.launch {
            val list = attempt { friends.friends().map { it.profile } }
            _state.update { s -> s.copy(members = s.members?.copy(friends = list)) }
        }
    }

    fun closeMembers() = _state.update { it.copy(members = null) }

    fun addMember(p: Profile) = changeMembers(p) { repo.addMember(listId, p.id) }

    fun removeMember(p: Profile) = changeMembers(p) { repo.removeMember(listId, p.id) }

    private fun changeMembers(p: Profile, action: suspend () -> Unit) {
        if (_state.value.members?.busy != null) return
        _state.update { s -> s.copy(members = s.members?.copy(busy = p.id)) }
        viewModelScope.launch {
            val error = try {
                action(); null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.friendlyMessage()
            }
            val list = attempt { repo.list(listId) ?: throw SocialException("This list no longer exists.") }
            _state.update { s -> s.copy(list = list, members = s.members?.copy(busy = null), toast = error) }
        }
    }

    // ----- leave / delete -----

    fun leave() = close { repo.leave(listId) }

    fun delete() = close { repo.delete(listId) }

    private fun close(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                _state.update { it.copy(closed = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(toast = e.friendlyMessage()) }
            }
        }
    }

    fun toastShown() = _state.update { it.copy(toast = null) }
}
