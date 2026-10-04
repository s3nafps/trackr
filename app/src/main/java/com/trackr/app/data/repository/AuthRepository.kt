package com.trackr.app.data.repository

import com.trackr.app.data.remote.GoogleIdResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(private val supabase: SupabaseClient) {
    val sessionStatus: Flow<SessionStatus> get() = supabase.auth.sessionStatus

    val currentUserId: String? get() = supabase.auth.currentUserOrNull()?.id

    suspend fun signInWithGoogle(result: GoogleIdResult) {
        supabase.auth.signInWith(IDToken) {
            idToken = result.idToken
            provider = Google
            nonce = result.rawNonce
        }
    }

    suspend fun signOut() = supabase.auth.signOut()

    /** Deletes the account server-side (cascades to profile, list and friendships), then drops the local session. */
    suspend fun deleteAccount() {
        supabase.postgrest.rpc("delete_account")
        // The server sessions died with the user, so /logout can fail; clear the stored session regardless.
        runCatching { supabase.auth.signOut() }.onFailure { supabase.auth.clearSession() }
    }
}
