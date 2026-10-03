package com.trackr.app.data.remote

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.trackr.app.BuildConfig
import java.security.MessageDigest
import java.util.UUID

/** Result of the Credential Manager Google flow. [rawNonce] must be passed to Supabase along with the token. */
data class GoogleIdResult(val idToken: String, val rawNonce: String)

sealed class GoogleSignInError(message: String) : Exception(message) {
    object Cancelled : GoogleSignInError("Sign-in cancelled")
    object NotConfigured : GoogleSignInError("Google sign-in is not configured (missing web client ID)")
    class Failed(message: String) : GoogleSignInError(message)
}

object GoogleSignIn {
    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** [activityContext] must be an Activity context so the account sheet can be shown. */
    suspend fun getIdToken(activityContext: Context): GoogleIdResult {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) throw GoogleSignInError.NotConfigured
        val rawNonce = UUID.randomUUID().toString()
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setNonce(sha256Hex(rawNonce))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        try {
            val response = CredentialManager.create(activityContext).getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                return GoogleIdResult(token, rawNonce)
            }
            throw GoogleSignInError.Failed("Unexpected credential type")
        } catch (e: GetCredentialCancellationException) {
            throw GoogleSignInError.Cancelled
        } catch (e: GetCredentialException) {
            throw GoogleSignInError.Failed(e.message ?: "Google sign-in failed")
        }
    }
}
