package com.trackr.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trackr.app.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("trackr_prefs")

@Singleton
class UserPrefs @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val recentKey = stringPreferencesKey("recent_searches")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val airingKey = booleanPreferencesKey("airing_enabled")
    private val updateCheckedKey = longPreferencesKey("update_last_check")
    private val updateDismissedKey = stringPreferencesKey("update_dismissed")
    private val sep = "\u001F"

    val recentSearches: Flow<List<String>> = ctx.dataStore.data.map { p ->
        p[recentKey]?.split(sep)?.filter { it.isNotBlank() }.orEmpty()
    }

    val themeMode: Flow<ThemeMode> = ctx.dataStore.data.map { p ->
        p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK
    }

    /** Master switch for new-episode alerts; on by default. */
    val airingEnabled: Flow<Boolean> = ctx.dataStore.data.map { it[airingKey] ?: true }

    suspend fun setAiringEnabled(on: Boolean) = ctx.dataStore.edit { it[airingKey] = on }

    suspend fun addRecent(query: String, max: Int = 8) {
        val q = query.trim().takeIf { it.length >= 2 } ?: return
        ctx.dataStore.edit { p ->
            val cur = p[recentKey]?.split(sep).orEmpty().filter { it.isNotBlank() && !it.equals(q, true) }
            p[recentKey] = (listOf(q) + cur).take(max).joinToString(sep)
        }
    }

    suspend fun removeRecent(query: String) {
        ctx.dataStore.edit { p ->
            p[recentKey] = p[recentKey]?.split(sep).orEmpty().filter { it != query }.joinToString(sep)
        }
    }

    suspend fun clearRecent() = ctx.dataStore.edit { it.remove(recentKey) }

    suspend fun setThemeMode(mode: ThemeMode) = ctx.dataStore.edit { it[themeKey] = mode.name }

    /** When GitHub was last asked for a newer release (epoch millis, 0 = never). */
    val lastUpdateCheck: Flow<Long> = ctx.dataStore.data.map { it[updateCheckedKey] ?: 0L }

    suspend fun setLastUpdateCheck(at: Long) = ctx.dataStore.edit { it[updateCheckedKey] = at }

    /** The release version the user chose "Later" for. */
    val dismissedUpdate: Flow<String?> = ctx.dataStore.data.map { it[updateDismissedKey] }

    suspend fun setDismissedUpdate(version: String) = ctx.dataStore.edit { it[updateDismissedKey] = version }
}
