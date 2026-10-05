package com.trackr.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Runs [follow] (a view model's live-updates loop) only while the screen is visible, so the Realtime connection
 * closes when the app goes to the background and reopens when it comes back.
 */
@Composable
fun FollowLiveUpdates(follow: suspend () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { follow() } }
}
