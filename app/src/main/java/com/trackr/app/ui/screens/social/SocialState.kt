package com.trackr.app.ui.screens.social

import com.trackr.app.data.repository.SocialException
import com.trackr.app.data.repository.SocialRepository
import com.trackr.app.data.repository.userMessage
import com.trackr.app.domain.model.Comment
import com.trackr.app.domain.model.Load
import com.trackr.app.domain.model.Profile
import com.trackr.app.domain.model.SharedList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The open comments sheet for one list entry. [posted] counts successful posts so the sheet can clear its draft. */
data class CommentsState(
    val entryId: String,
    val title: String,
    val entryOwner: String?,
    val comments: Load<List<Comment>> = Load.Loading,
    val posting: Boolean = false,
    val posted: Int = 0,
    val error: String? = null,
)

/** The open "Recommend to a friend" sheet: who you can send to, who you already sent to, and who's in flight. */
data class RecommendState(
    val friends: Load<List<Profile>> = Load.Loading,
    val sending: String? = null,
    val sent: Set<String> = emptySet(),
    val error: String? = null,
)

/**
 * Comments-sheet logic shared by the friends feed and the Detail screen. [onCountChange] reports added (+1) and
 * removed (-1) comments so the caller can keep its counts in step without refetching.
 */
class CommentsController(
    private val scope: CoroutineScope,
    private val social: SocialRepository,
    private val onCountChange: (entryId: String, delta: Int) -> Unit = { _, _ -> },
) {
    private val _state = MutableStateFlow<CommentsState?>(null)
    val state: StateFlow<CommentsState?> = _state.asStateFlow()

    fun open(entryId: String, title: String, entryOwner: String?) {
        _state.value = CommentsState(entryId, title, entryOwner)
        load(entryId, entryOwner)
    }

    fun close() {
        _state.value = null
    }

    /** Live update: fetches the open sheet's comments again, keeping the current ones if that fails. */
    fun reload() {
        val s = _state.value ?: return
        scope.launch {
            val fresh = runCatching { social.comments(s.entryId, s.entryOwner) }.getOrNull() ?: return@launch
            updateIfOpen(s.entryId) { it.copy(comments = Load.Success(fresh)) }
        }
    }

    fun post(text: String) {
        val s = _state.value ?: return
        if (s.posting || text.isBlank()) return
        _state.update { it?.copy(posting = true, error = null) }
        scope.launch {
            try {
                social.addComment(s.entryId, text)
                onCountChange(s.entryId, 1)
                updateIfOpen(s.entryId) { it.copy(posting = false, posted = it.posted + 1) }
                load(s.entryId, s.entryOwner)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = (e as? SocialException)?.message ?: e.userMessage()
                updateIfOpen(s.entryId) { it.copy(posting = false, error = message) }
            }
        }
    }

    fun delete(comment: Comment) {
        val s = _state.value ?: return
        scope.launch {
            try {
                social.deleteComment(comment.id)
                onCountChange(s.entryId, -1)
                updateIfOpen(s.entryId) { c ->
                    val list = (c.comments as? Load.Success)?.data ?: return@updateIfOpen c
                    c.copy(comments = Load.Success(list.filterNot { it.id == comment.id }))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateIfOpen(s.entryId) { it.copy(error = e.userMessage()) }
            }
        }
    }

    private fun load(entryId: String, entryOwner: String?) {
        scope.launch {
            val result = try {
                Load.Success(social.comments(entryId, entryOwner))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failure(e.userMessage())
            }
            updateIfOpen(entryId) { it.copy(comments = result) }
        }
    }

    /** The sheet may have been closed or switched to another entry meanwhile; late results must leave it alone. */
    private inline fun updateIfOpen(entryId: String, crossinline change: (CommentsState) -> CommentsState) {
        _state.update { c -> if (c != null && c.entryId == entryId) change(c) else c }
    }
}

/** The Detail screen's "Add to a shared list" sheet: your lists, which already have the title, and the one saving. */
data class AddToSharedState(
    val lists: Load<List<SharedList>> = Load.Loading,
    val containing: Set<String> = emptySet(),
    val busy: String? = null,
    val error: String? = null,
)
