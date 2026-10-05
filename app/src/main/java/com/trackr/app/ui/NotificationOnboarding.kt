package com.trackr.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.local.UserPrefs
import com.trackr.app.ui.components.canScheduleExactAlarms
import com.trackr.app.ui.components.openExactAlarmSettings
import com.trackr.app.ui.components.rememberNotificationPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether the one-time notification prompt is still due; null while the stored answer loads. */
@HiltViewModel
class NotificationOnboardingViewModel @Inject constructor(private val prefs: UserPrefs) : ViewModel() {
    val due: StateFlow<Boolean?> = prefs.notificationsAsked.map { !it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun done() {
        viewModelScope.launch { prefs.setNotificationsAsked() }
    }
}

private enum class Step { INTRO, EXACT }

/**
 * First launch after sign-in: explains episode and release alerts, then asks Android for permission (13+), then for exact alarms
 * (12+, "Alarms & reminders") so episode alerts fire when the episode airs rather than minutes later. Shown once;
 * both can still be changed later in Settings.
 */
@Composable
fun NotificationOnboarding(onDone: () -> Unit) {
    val context = LocalContext.current
    val notificationsOn = remember { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    // Someone who already allowed everything (e.g. updating from an older version) isn't asked again.
    if (notificationsOn && canScheduleExactAlarms(context)) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    var step by remember { mutableStateOf(if (notificationsOn) Step.EXACT else Step.INTRO) }
    val askExactOrFinish = { if (canScheduleExactAlarms(context)) onDone() else step = Step.EXACT }
    // Whatever the answer, move on: a refusal is respected and Settings can change it later.
    val ensurePermission = rememberNotificationPermission { askExactOrFinish() }

    when (step) {
        Step.INTRO -> AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Outlined.NotificationsActive, null) },
            title = { Text("Stay in the loop") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Trackr can let you know when a new episode of something you're watching comes out, and when titles you've turned the bell on for are released.")
                }
            },
            confirmButton = { Button(onClick = { ensurePermission {} }) { Text("Allow notifications") } },
            dismissButton = { TextButton(onClick = onDone) { Text("Not now") } },
        )
        Step.EXACT -> AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Outlined.Alarm, null) },
            title = { Text("Alerts right on time") },
            text = {
                Text(
                    "To alert you the moment an episode airs, Android needs you to allow Trackr under " +
                        "\"Alarms & reminders\". Without it, alerts can arrive a few minutes late.",
                )
            },
            confirmButton = { Button(onClick = { openExactAlarmSettings(context); onDone() }) { Text("Open settings") } },
            dismissButton = { TextButton(onClick = onDone) { Text("Skip") } },
        )
    }
}
