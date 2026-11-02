package com.trackr.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.trackr.app.ui.components.LocalProfile
import com.trackr.app.ui.components.UpdateDialog
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackr.app.ui.navigation.DetailTarget
import com.trackr.app.ui.navigation.TrackrNavHost
import com.trackr.app.ui.screens.auth.AppState
import com.trackr.app.ui.screens.auth.AuthViewModel
import com.trackr.app.ui.screens.auth.LoginScreen
import com.trackr.app.ui.screens.auth.UsernameScreen
import com.trackr.app.ui.screens.profile.AccountDeletion

@Composable
fun AppRoot(
    openTarget: DetailTarget? = null,
    onTargetOpened: () -> Unit = {},
    openFriends: Boolean = false,
    onFriendsOpened: () -> Unit = {},
    vm: AuthViewModel = hiltViewModel(),
    updates: UpdateViewModel = hiltViewModel(),
    onboarding: NotificationOnboardingViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val update by updates.state.collectAsStateWithLifecycle()
    val onboardingDue by onboarding.due.collectAsStateWithLifecycle()
    val signedIn = state.app is AppState.Ready
    if (signedIn && onboardingDue == true) {
        NotificationOnboarding(onDone = onboarding::done)
    } else {
        // One dialog at a time: an update offer waits until the notification prompt is answered.
        update.available?.let { UpdateDialog(it, onDownload = updates::downloadStarted, onLater = updates::later) }
    }
    state.unsyncedOnSignOut?.let { pending ->
        UnsyncedSignOutDialog(pending, onSignOutAnyway = { vm.signOut(force = true) }, onStay = vm::keepSignedIn)
    }
    when (val app = state.app) {
        AppState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        AppState.SignedOut -> LoginScreen(state, onGoogle = vm::signInWithGoogle)
        is AppState.NeedsUsername -> UsernameScreen(state, app.profile.username, vm::submitUsername)
        is AppState.Ready -> CompositionLocalProvider(LocalProfile provides (me ?: app.profile)) {
            TrackrNavHost(
                onSignOut = { vm.signOut() },
                deletion = AccountDeletion(state.deletingAccount, state.deleteError, vm::deleteAccount, vm::dismissDeleteError),
                openTarget = openTarget,
                onTargetOpened = onTargetOpened,
                openFriends = openFriends,
                onFriendsOpened = onFriendsOpened,
            )
        }
    }
}

/** Sign-out couldn't save the latest edits (no connection); signing out now would lose them. */
@Composable
private fun UnsyncedSignOutDialog(pending: Int, onSignOutAnyway: () -> Unit, onStay: () -> Unit) {
    val changes = if (pending == 1) "1 change" else "$pending changes"
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text("Changes not saved yet") },
        text = { Text("$changes to your list couldn't be saved to your account. Check your connection and try again, or sign out and lose them.") },
        confirmButton = { TextButton(onClick = onStay) { Text("Stay signed in") } },
        dismissButton = {
            TextButton(onClick = onSignOutAnyway) { Text("Sign out anyway", color = MaterialTheme.colorScheme.error) }
        },
    )
}
