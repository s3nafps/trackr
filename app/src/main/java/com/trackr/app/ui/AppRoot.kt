package com.trackr.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.trackr.app.ui.components.LocalProfile
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackr.app.ui.navigation.TrackrNavHost
import com.trackr.app.ui.screens.auth.AppState
import com.trackr.app.ui.screens.auth.AuthViewModel
import com.trackr.app.ui.screens.auth.LoginScreen
import com.trackr.app.ui.screens.auth.UsernameScreen

@Composable
fun AppRoot(vm: AuthViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val app = state.app) {
        AppState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        AppState.SignedOut -> LoginScreen(state, onGoogle = vm::signInWithGoogle)
        is AppState.NeedsUsername -> UsernameScreen(state, app.profile.username, vm::submitUsername)
        is AppState.Ready -> CompositionLocalProvider(LocalProfile provides app.profile) {
            TrackrNavHost(onSignOut = vm::signOut)
        }
    }
}
