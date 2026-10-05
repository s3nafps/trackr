package com.trackr.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.repository.userMessage
import com.trackr.app.data.update.UpdateCheck
import com.trackr.app.data.update.UpdateInfo
import com.trackr.app.data.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    /** A newer release to offer; the dialog shows while this is set. */
    val available: UpdateInfo? = null,
    val checking: Boolean = false,
    /** The result of a check started from About ("up to date", or why it failed). */
    val message: String? = null,
)

/** Activity-scoped: checks on launch, and About reaches the same instance so its result opens the same dialog. */
@HiltViewModel
class UpdateViewModel @Inject constructor(private val updates: UpdateRepository) : ViewModel() {
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    init { check(manual = false) }

    fun checkNow() = check(manual = true)

    private fun check(manual: Boolean) {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, message = null) }
        viewModelScope.launch {
            val result = try {
                updates.check(manual)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed automatic check stays silent; it is tried again on a later launch.
                _state.update { it.copy(checking = false, message = if (manual) "Couldn't check for updates: ${e.userMessage()}" else null) }
                return@launch
            }
            _state.update {
                when (result) {
                    is UpdateCheck.Available -> it.copy(checking = false, available = result.info)
                    UpdateCheck.UpToDate -> it.copy(checking = false, message = if (manual) "You have the latest version." else null)
                    UpdateCheck.Skipped -> it.copy(checking = false)
                }
            }
        }
    }

    /** "Later": hides the dialog, and automatic checks won't offer this version again. */
    fun later() {
        val info = _state.value.available ?: return
        _state.update { it.copy(available = null) }
        viewModelScope.launch { updates.dismiss(info.version) }
    }

    /** "Download": the browser takes over; the next automatic check reminds the user if it isn't installed. */
    fun downloadStarted() = _state.update { it.copy(available = null) }
}
