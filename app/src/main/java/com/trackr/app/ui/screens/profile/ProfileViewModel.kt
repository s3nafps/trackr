package com.trackr.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.airing.AiringRefreshScheduler
import com.trackr.app.data.local.UserPrefs
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.data.repository.ProfileRepository
import com.trackr.app.domain.model.Profile
import com.trackr.app.domain.model.ProfileStats
import com.trackr.app.domain.model.StatsCalculator
import com.trackr.app.domain.util.GenreBreakdown
import com.trackr.app.domain.util.GenreStats
import com.trackr.app.domain.util.WatchTime
import com.trackr.app.data.meta.TitleMetaRepository
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
    val genres: GenreBreakdown = GenreBreakdown(),
    val theme: ThemeMode = ThemeMode.DARK,
    val editBusy: Boolean = false,
    val editError: String? = null,
    val editDone: Boolean = false,
    val airingEnabled: Boolean = true,
    val socialAlerts: Boolean = true,
    /** Time spent watching, from each title's runtime (typical ones where unknown). */
    val watchTime: WatchTime.Total = WatchTime.Total(0, 0),
    val syncing: Boolean = false,
    val syncMessage: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val lists: ListRepository,
    private val prefs: UserPrefs,
    private val airingRefresh: AiringRefreshScheduler,
    private val titleMeta: TitleMetaRepository,
) : ViewModel() {
    private val local = MutableStateFlow(ProfileUiState())

    val state: StateFlow<ProfileUiState> = combine(
        local, profiles.me, combine(lists.entries, titleMeta.all, ::Pair), prefs.themeMode,
        combine(prefs.airingEnabled, prefs.socialAlerts, ::Pair),
    ) { l, me, (entries, meta), theme, (airing, social) ->
        l.copy(
            me = me, stats = StatsCalculator.compute(entries), genres = GenreStats.breakdown(entries, meta),
            theme = theme, airingEnabled = airing, socialAlerts = social, watchTime = WatchTime.total(entries, meta),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { prefs.setThemeMode(mode) } }

    fun setAiringEnabled(on: Boolean) {
        viewModelScope.launch {
            prefs.setAiringEnabled(on)
            airingRefresh.refreshNow()
        }
    }

    fun setSocialAlerts(on: Boolean) { viewModelScope.launch { prefs.setSocialAlerts(on) } }

    fun editUsername(name: String) {
        viewModelScope.launch {
            local.update { it.copy(editBusy = true, editError = null, editDone = false) }
            try {
                // The own profile may not be loaded yet (offline at launch): load it rather than do nothing.
                val id = profiles.me.value?.id ?: profiles.loadMe().id
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
