package com.trackr.app.ui.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.repository.FriendsRepository
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.userMessage
import com.trackr.app.domain.model.ActivityEntry
import com.trackr.app.domain.model.Friend
import com.trackr.app.domain.model.FriendRequest
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.Profile
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

data class AddFriendState(
    val open: Boolean = false,
    val query: String = "",
    val busy: Boolean = false,
    val result: Profile? = null,
    val message: String? = null,
    val sent: Boolean = false,
)

data class FriendsUiState(
    val activity: Load<List<ActivityEntry>> = Load.Loading,
    val friends: Load<List<Friend>> = Load.Loading,
    val pending: List<FriendRequest> = emptyList(),
    val myKeys: Set<String> = emptySet(),
    val add: AddFriendState = AddFriendState(),
    val refreshing: Boolean = false,
    val toast: String? = null,
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val repo: FriendsRepository,
    private val lists: ListRepository,
) : ViewModel() {
    private val remote = MutableStateFlow(FriendsUiState())

    val state: StateFlow<FriendsUiState> = combine(remote, lists.entries) { r, mine ->
        r.copy(myKeys = mine.map { it.key }.toSet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendsUiState())

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            remote.update { it.copy(refreshing = true) }
            val a = launch { remote.update { s -> s.copy(activity = attempt { repo.activity() }) } }
            val f = launch { remote.update { s -> s.copy(friends = attempt { repo.friends() }) } }
            val p = launch { runCatching { repo.pendingRequests() }.onSuccess { r -> remote.update { s -> s.copy(pending = r) } } }
            a.join(); f.join(); p.join()
            remote.update { it.copy(refreshing = false) }
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Load<T> = try {
        Load.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Load.Failure(e.userMessage())
    }

    fun respond(req: FriendRequest, accept: Boolean) {
        viewModelScope.launch {
            try {
                if (accept) repo.accept(req.friendshipId) else repo.delete(req.friendshipId)
                remote.update { it.copy(pending = it.pending - req, toast = if (accept) "You're now friends with ${req.from.username}" else null) }
                if (accept) refresh()
            } catch (e: Exception) {
                remote.update { it.copy(toast = e.userMessage()) }
            }
        }
    }

    fun planToWatch(a: ActivityEntry) {
        viewModelScope.launch {
            lists.save(a.toMediaItem(), ListStatus.PLAN_TO_WATCH, null, 0)
            remote.update { it.copy(toast = "Added ${a.title} to Plan to Watch") }
        }
    }

    fun toastShown() = remote.update { it.copy(toast = null) }

    // ----- add friend dialog -----
    fun openAdd() = remote.update { it.copy(add = AddFriendState(open = true)) }
    fun closeAdd() = remote.update { it.copy(add = AddFriendState()) }
    fun setAddQuery(q: String) = remote.update { it.copy(add = it.add.copy(query = q, result = null, message = null, sent = false)) }

    fun search() {
        val q = remote.value.add.query
        if (q.isBlank()) return
        viewModelScope.launch {
            remote.update { it.copy(add = it.add.copy(busy = true, message = null, result = null)) }
            try {
                val p = repo.find(q)
                remote.update { it.copy(add = it.add.copy(busy = false, result = p, message = if (p == null) "No one found with that username or invite code." else null)) }
            } catch (e: Exception) {
                remote.update { it.copy(add = it.add.copy(busy = false, message = e.userMessage())) }
            }
        }
    }

    fun sendRequest() {
        val target = remote.value.add.result ?: return
        viewModelScope.launch {
            remote.update { it.copy(add = it.add.copy(busy = true)) }
            try {
                repo.sendRequest(target)
                remote.update { it.copy(add = it.add.copy(busy = false, sent = true, message = "Request sent to ${target.username}!")) }
            } catch (e: Exception) {
                remote.update { it.copy(add = it.add.copy(busy = false, message = e.message ?: "Couldn't send request")) }
            }
        }
    }
}
