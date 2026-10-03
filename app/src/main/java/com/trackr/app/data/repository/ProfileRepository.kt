package com.trackr.app.data.repository

import com.trackr.app.domain.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
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

    suspend fun getProfile(userId: String): Profile =
        supabase.from("profiles").select { filter { eq("id", userId) } }.decodeSingle()

    suspend fun findByUsernameOrCode(query: String): Profile? {
        val q = query.trim().removePrefix("@")
        if (q.isEmpty()) return null
        val byName = supabase.from("profiles").select { filter { ilike("username", q) } }
            .decodeList<Profile>().firstOrNull { it.username.equals(q, ignoreCase = true) }
        if (byName != null) return byName
        return supabase.from("profiles").select { filter { eq("invite_code", q.uppercase()) } }
            .decodeList<Profile>().firstOrNull()
    }

    suspend fun setUsername(userId: String, username: String): Profile {
        val name = username.trim()
        if (!usernameRegex.matches(name)) throw InvalidUsernameException()
        try {
            return supabase.from("profiles").update(
                buildJsonObject {
                    put("username", name)
                    put("username_set", true)
                },
            ) {
                filter { eq("id", userId) }
                select(Columns.ALL)
            }.decodeSingle()
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("duplicate", true) || msg.contains("23505")) throw UsernameTakenException()
            throw e
        }
    }
}
