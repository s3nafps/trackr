package com.trackr.app.data.repository

import com.trackr.app.data.remote.GoogleIdResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
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
}
