package com.trackr.app.ui.screens.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.remote.GoogleSignIn
import com.trackr.app.data.remote.GoogleSignInError
import com.trackr.app.data.repository.AuthRepository
import com.trackr.app.data.repository.ProfileRepository
import com.trackr.app.domain.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppState {
    data object Loading : AppState
    data object SignedOut : AppState
    data class NeedsUsername(val profile: Profile) : AppState
    data class Ready(val profile: Profile?) : AppState
}

data class AuthUiState(
    val app: AppState = AppState.Loading,
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> loadProfile(status.session.user?.id ?: auth.currentUserId)
                    is SessionStatus.NotAuthenticated -> _state.update { it.copy(app = AppState.SignedOut) }
                    is SessionStatus.Initializing -> Unit
                    // Network trouble while refreshing: keep the user in the app (offline use).
                    else -> if (_state.value.app is AppState.Loading) {
                        _state.update { it.copy(app = AppState.Ready(null)) }
                    }
                }
            }
        }
    }

    private suspend fun loadProfile(userId: String?) {
        if (userId == null) {
            _state.update { it.copy(app = AppState.Ready(null)) }
            return
        }
        val next = try {
            val p = profiles.getProfile(userId)
            if (p.usernameSet) AppState.Ready(p) else AppState.NeedsUsername(p)
        } catch (e: Exception) {
            AppState.Ready(null) // offline / transient: don't block the app
        }
        _state.update { it.copy(app = next, busy = false) }
    }

    fun signInWithGoogle(activityContext: Context) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = GoogleSignIn.getIdToken(activityContext)
                auth.signInWithGoogle(result)
                _state.update { it.copy(busy = false) }
            } catch (e: GoogleSignInError.Cancelled) {
                _state.update { it.copy(busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Sign-in failed") }
            }
        }
    }

    fun submitUsername(username: String) {
        val current = _state.value.app as? AppState.NeedsUsername ?: return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val updated = profiles.setUsername(current.profile.id, username)
                _state.update { it.copy(app = AppState.Ready(updated), busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Could not save username") }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { runCatching { auth.signOut() } }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
