package com.trackr.app.data.repository

import com.trackr.app.domain.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

class UsernameTakenException : Exception("That username is already taken")
class InvalidUsernameException : Exception("Use 3–24 letters, numbers, dots or underscores")

@Singleton
class ProfileRepository @Inject constructor(private val supabase: SupabaseClient) {
    private val usernameRegex = Regex("^[A-Za-z0-9_.]{3,24}$")

    /** What any signed-in user may read of a profile. The invite code is left out: only its owner reads it. */
    private val publicColumns = Columns.list("id", "username", "username_set", "avatar_url")

    private val _me = MutableStateFlow<Profile?>(null)
    /** The signed-in user's profile; single source of truth for greeting, avatar and profile screens. */
    val me: StateFlow<Profile?> = _me.asStateFlow()

    fun clearMe() { _me.value = null }

    /** The signed-in user's own profile, with their invite code. */
    private suspend fun ownProfile(): Profile = supabase.postgrest.rpc("my_profile").decodeSingle<Profile>()

    suspend fun getProfile(userId: String): Profile =
        if (supabase.auth.currentUserOrNull()?.id == userId) ownProfile().also { _me.value = it }
        else supabase.from("profiles").select(publicColumns) { filter { eq("id", userId) } }.decodeSingle<Profile>()

    suspend fun getProfiles(ids: Collection<String>): List<Profile> =
        if (ids.isEmpty()) emptyList()
        else supabase.from("profiles").select(publicColumns) { filter { isIn("id", ids.toList()) } }.decodeList()

    suspend fun findByUsernameOrCode(query: String): Profile? {
        val q = query.trim().removePrefix("@")
        if (q.isEmpty()) return null
        // Escaped, so % and _ in the query can't match other names.
        val pattern = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val byName = supabase.from("profiles").select(publicColumns) { filter { ilike("username", pattern) } }
            .decodeList<Profile>().firstOrNull { it.username.equals(q, ignoreCase = true) }
        if (byName != null) return byName
        return supabase.postgrest.rpc("find_profile_by_invite", buildJsonObject { put("code", q.uppercase()) })
            .decodeList<Profile>().firstOrNull()
    }

    suspend fun setUsername(userId: String, username: String): Profile {
        val name = username.trim()
        if (!usernameRegex.matches(name)) throw InvalidUsernameException()
        try {
            supabase.from("profiles").update(
                buildJsonObject {
                    put("username", name)
                    put("username_set", true)
                },
            ) {
                filter { eq("id", userId) }
                select(publicColumns)
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("duplicate", true) || msg.contains("23505")) throw UsernameTakenException()
            throw e
        }
        return ownProfile().also { _me.value = it }
    }
}
