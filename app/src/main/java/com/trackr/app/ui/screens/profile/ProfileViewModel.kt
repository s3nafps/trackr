package com.trackr.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.local.UserPrefs
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.ProfileRepository
import com.trackr.app.domain.model.Profile
import com.trackr.app.domain.model.ProfileStats
import com.trackr.app.domain.model.StatsCalculator
import com.trackr.app.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val me: Profile? = null,
    val stats: ProfileStats = StatsCalculator.compute(emptyList()),
    val theme: ThemeMode = ThemeMode.DARK,
    val editBusy: Boolean = false,
    val editError: String? = null,
    val editDone: Boolean = false,
    val syncing: Boolean = false,
    val syncMessage: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val lists: ListRepository,
    private val prefs: UserPrefs,
) : ViewModel() {
    private val local = MutableStateFlow(ProfileUiState())

    val state: StateFlow<ProfileUiState> = combine(local, profiles.me, lists.entries, prefs.themeMode) { l, me, entries, theme ->
        l.copy(me = me, stats = StatsCalculator.compute(entries), theme = theme)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { prefs.setThemeMode(mode) } }

    fun editUsername(name: String) {
        val id = profiles.me.value?.id ?: return
        viewModelScope.launch {
            local.update { it.copy(editBusy = true, editError = null, editDone = false) }
            try {
                profiles.setUsername(id, name)
                local.update { it.copy(editBusy = false, editDone = true) }
            } catch (e: Exception) {
                local.update { it.copy(editBusy = false, editError = e.message ?: "Couldn't save") }
            }
        }
    }

    fun resetEdit() = local.update { it.copy(editBusy = false, editError = null, editDone = false) }

    fun syncNow() {
        viewModelScope.launch {
            local.update { it.copy(syncing = true, syncMessage = null) }
            val msg = try { if (lists.sync()) "Synced just now" else "Sign in to sync" } catch (e: Exception) { "Couldn't sync – will retry when online" }
            local.update { it.copy(syncing = false, syncMessage = msg) }
        }
    }
}
