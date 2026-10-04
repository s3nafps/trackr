package com.trackr.app.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.local.UserPrefs
import com.trackr.app.data.remote.supabase.ActivityDto
import com.trackr.app.data.repository.FriendsRepository
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.MediaRepository
import com.trackr.app.data.repository.SharedListsRepository
import com.trackr.app.data.repository.SocialException
import com.trackr.app.data.repository.SocialRepository
import com.trackr.app.data.repository.userMessage
import com.trackr.app.domain.model.Comment
import com.trackr.app.domain.model.EntrySocial
import com.trackr.app.domain.model.ListEntry
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.MediaDetail
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.model.SharedList
import com.trackr.app.ui.screens.social.AddToSharedState
import com.trackr.app.ui.screens.social.CommentsController
import com.trackr.app.ui.screens.social.CommentsState
import com.trackr.app.ui.screens.social.RecommendState
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

/** Recommend sheet, plus friends' reactions and comments on your own entry for this title (once it has synced). */
data class DetailSocialState(
    val recommend: RecommendState? = null,
    val ownEntryId: String? = null,
    val ownSocial: EntrySocial? = null,
    val comments: CommentsState? = null,
    val addToShared: AddToSharedState? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val media: MediaRepository,
    private val lists: ListRepository,
    private val social: SocialRepository,
    prefs: UserPrefs,
    private val friendsRepo: FriendsRepository,
    private val sharedLists: SharedListsRepository,
) : ViewModel() {
    val source: MediaSource = MediaSource.fromKey(saved.get<String>("source").orEmpty())
    val type: MediaType = MediaType.fromKey(saved.get<String>("type").orEmpty())
    val id: String = saved.get<String>("id").orEmpty()

    private val detail = MutableStateFlow<Load<MediaDetail>>(Load.Loading)
    private val friends = MutableStateFlow<List<ActivityDto>>(emptyList())

    val state: StateFlow<DetailUiState> = combine(detail, lists.entry(source.key, id), friends) { d, e, f ->
        DetailUiState(d, e, f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    private val socialLocal = MutableStateFlow(DetailSocialState())

    private val commentsCtl = CommentsController(viewModelScope, social) { _, delta ->
        socialLocal.update { s -> s.copy(ownSocial = s.ownSocial?.let { it.copy(commentCount = (it.commentCount + delta).coerceAtLeast(0)) }) }
    }

    val socialState: StateFlow<DetailSocialState> = combine(socialLocal, commentsCtl.state) { s, c -> s.copy(comments = c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailSocialState())

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
        viewModelScope.launch {
            // Same for reactions on your own entry: only shown when there are some.
            if (lists.entry(source.key, id).first() == null) return@launch
            runCatching {
                val entryId = social.myEntryId(source.key, id) ?: return@runCatching
                val summary = social.socialFor(listOf(entryId))[entryId]
                socialLocal.update { it.copy(ownEntryId = entryId, ownSocial = summary) }
            }
        }
    }

    // ----- recommend to a friend -----

    fun openRecommend() {
        socialLocal.update { it.copy(recommend = RecommendState()) }
        viewModelScope.launch {
            val friends = try {
                Load.Success(friendsRepo.friends().map { it.profile })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failure(e.userMessage())
            }
            socialLocal.update { s -> s.copy(recommend = s.recommend?.copy(friends = friends)) }
        }
    }

    fun closeRecommend() = socialLocal.update { it.copy(recommend = null) }

    fun sendRecommendation(friendId: String, note: String) {
        val item = (detail.value as? Load.Success)?.data?.item ?: return
        if (socialLocal.value.recommend?.sending != null) return
        socialLocal.update { s -> s.copy(recommend = s.recommend?.copy(sending = friendId, error = null)) }
        viewModelScope.launch {
            try {
                social.recommend(friendId, item, note)
                socialLocal.update { s -> s.copy(recommend = s.recommend?.let { it.copy(sending = null, sent = it.sent + friendId) }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = (e as? SocialException)?.message ?: e.userMessage()
                socialLocal.update { s -> s.copy(recommend = s.recommend?.copy(sending = null, error = message)) }
            }
        }
    }

    // ----- add to a shared list -----

    fun openAddToShared() {
        val item = (detail.value as? Load.Success)?.data?.item ?: return
        socialLocal.update { it.copy(addToShared = AddToSharedState()) }
        viewModelScope.launch {
            val lists = try {
                Load.Success(sharedLists.lists())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failure(e.userMessage())
            }
            val containing = runCatching { sharedLists.listsContaining(item) }.getOrDefault(emptySet())
            socialLocal.update { s -> s.copy(addToShared = s.addToShared?.copy(lists = lists, containing = containing)) }
        }
    }

    fun closeAddToShared() = socialLocal.update { it.copy(addToShared = null) }

    fun addToShared(list: SharedList) {
        val item = (detail.value as? Load.Success)?.data?.item ?: return
        if (socialLocal.value.addToShared?.busy != null) return
        socialLocal.update { s -> s.copy(addToShared = s.addToShared?.copy(busy = list.id, error = null)) }
        viewModelScope.launch {
            try {
                sharedLists.addItem(list.id, item)
                socialLocal.update { s -> s.copy(addToShared = s.addToShared?.let { it.copy(busy = null, containing = it.containing + list.id) }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = (e as? SocialException)?.message ?: e.userMessage()
                socialLocal.update { s -> s.copy(addToShared = s.addToShared?.copy(busy = null, error = message)) }
            }
        }
    }

    // ----- comments on your own entry -----

    fun openOwnComments() {
        val entryId = socialLocal.value.ownEntryId ?: return
        val title = (detail.value as? Load.Success)?.data?.item?.title.orEmpty()
        commentsCtl.open(entryId, title, social.currentUserId)
    }

    fun postComment(text: String) = commentsCtl.post(text)
    fun deleteComment(comment: Comment) = commentsCtl.delete(comment)
    fun closeComments() = commentsCtl.close()

    fun save(status: ListStatus, rating: Int?, progress: Int) {
        val item = (detail.value as? Load.Success)?.data?.item ?: return
        viewModelScope.launch { lists.save(item, status, rating, progress) }
    }

    fun remove() {
        val e = state.value.entry ?: return
        viewModelScope.launch { lists.remove(e) }
    }
}
