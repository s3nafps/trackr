package com.trackr.app.ui.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.realtime.LiveFollower
import com.trackr.app.data.realtime.LiveTables
import com.trackr.app.data.realtime.LiveUpdates
import com.trackr.app.data.repository.FriendsRepository
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.SocialRepository
import com.trackr.app.data.repository.userMessage
import com.trackr.app.domain.model.ActivityEntry
import com.trackr.app.domain.model.Comment
import com.trackr.app.domain.model.EntrySocial
import com.trackr.app.domain.model.Friend
import com.trackr.app.domain.model.FriendRequest
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.Profile
import com.trackr.app.domain.model.Recommendation
import com.trackr.app.ui.screens.social.CommentsController
import com.trackr.app.ui.screens.social.CommentsState
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
    /** Reactions and comment counts per activity entry id. */
    val social: Map<String, EntrySocial> = emptyMap(),
    /** Titles friends recommended to you, newest first. */
    val inbox: List<Recommendation> = emptyList(),
    val comments: CommentsState? = null,
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val repo: FriendsRepository,
    private val lists: ListRepository,
    private val social: SocialRepository,
    live: LiveUpdates,
) : ViewModel() {
    private val remote = MutableStateFlow(FriendsUiState())

    private val liveFollower = LiveFollower(
        live, LiveTables.LIST_ENTRIES, LiveTables.REACTIONS, LiveTables.COMMENTS, LiveTables.RECOMMENDATIONS, LiveTables.FRIENDSHIPS,
    )

    private val commentsCtl = CommentsController(viewModelScope, social) { id, delta ->
        remote.update { s ->
            val cur = s.social[id] ?: EntrySocial()
            s.copy(social = s.social + (id to cur.copy(commentCount = (cur.commentCount + delta).coerceAtLeast(0))))
        }
    }

    val state: StateFlow<FriendsUiState> = combine(remote, lists.entries, commentsCtl.state) { r, mine, c ->
        r.copy(myKeys = mine.map { it.key }.toSet(), comments = c)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendsUiState())

    init { refresh() }

    /** Pull to refresh (with the spinner). */
    fun refresh() = load(silent = false)

    /**
     * While the screen is visible: friends' new activity, reactions, comments, recommendations and requests appear
     * without a refresh. The open comments sheet reloads too.
     */
    suspend fun followLiveUpdates() = liveFollower.follow {
        load(silent = true)
        commentsCtl.reload()
    }

    /** A silent reload (live update) shows no spinner and keeps what's on screen if a request fails. */
    private fun load(silent: Boolean) {
        viewModelScope.launch {
            if (!silent) remote.update { it.copy(refreshing = true) }
            val a = launch {
                val activity = attempt { repo.activity() }
                remote.update { s -> s.copy(activity = keep(s.activity, activity, silent)) }
                // Reactions are extra: if they fail to load, the feed still shows (just without counts).
                (activity as? Load.Success)?.data?.let { entries ->
                    runCatching { social.socialFor(entries.map { it.id }) }.onSuccess { m -> remote.update { s -> s.copy(social = m) } }
                }
            }
            val f = launch {
                val friends = attempt { repo.friends() }
                remote.update { s -> s.copy(friends = keep(s.friends, friends, silent)) }
            }
            val p = launch { runCatching { repo.pendingRequests() }.onSuccess { r -> remote.update { s -> s.copy(pending = r) } } }
            val i = launch { runCatching { social.inbox() }.onSuccess { r -> remote.update { s -> s.copy(inbox = r) } } }
            a.join(); f.join(); p.join(); i.join()
            if (!silent) remote.update { it.copy(refreshing = false) }
        }
    }

    private fun <T> keep(current: Load<T>, new: Load<T>, silent: Boolean): Load<T> =
        if (silent && new is Load.Failure && current is Load.Success) current else new

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

    // ----- reactions & comments -----

    /** Optimistic: the pill flips at once and flips back if the server says no. */
    fun toggleReaction(entryId: String, emoji: String) {
        val on = emoji !in (remote.value.social[entryId] ?: EntrySocial()).myReactions
        val flip = { s: FriendsUiState -> s.copy(social = s.social + (entryId to (s.social[entryId] ?: EntrySocial()).toggled(emoji))) }
        remote.update(flip)
        viewModelScope.launch {
            try {
                social.setReaction(entryId, emoji, on)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                remote.update { flip(it).copy(toast = "Couldn't save your reaction. Try again.") }
            }
        }
    }

    fun openComments(entry: ActivityEntry) = commentsCtl.open(entry.id, entry.title, entry.userId)
    fun postComment(text: String) = commentsCtl.post(text)
    fun deleteComment(comment: Comment) = commentsCtl.delete(comment)
    fun closeComments() = commentsCtl.close()

    // ----- recommendations inbox -----

    /** Opening a recommendation marks it seen (best effort). */
    fun openRecommendation(r: Recommendation) {
        if (r.seen) return
        remote.update { s -> s.copy(inbox = s.inbox.map { if (it.id == r.id) it.copy(seen = true) else it }) }
        viewModelScope.launch { runCatching { social.markSeen(r.id) } }
    }

    fun dismissRecommendation(r: Recommendation) {
        remote.update { s -> s.copy(inbox = s.inbox.filterNot { it.id == r.id }) }
        viewModelScope.launch {
            try {
                social.deleteRecommendation(r.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                remote.update { s -> s.copy(inbox = (s.inbox + r).sortedByDescending { it.createdAt }, toast = e.userMessage()) }
            }
        }
    }

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
