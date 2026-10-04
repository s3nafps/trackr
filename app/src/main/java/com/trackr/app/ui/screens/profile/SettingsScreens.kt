package com.trackr.app.ui.screens.profile

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackr.app.BuildConfig
import com.trackr.app.ui.components.BrandLogo
import com.trackr.app.ui.components.LocalProfile
import com.trackr.app.ui.components.TrackrTopBar
import com.trackr.app.ui.theme.ThemeMode

const val TMDB_ATTRIBUTION = "This product uses the TMDB API but is not endorsed or certified by TMDB."

/** Account deletion state and actions; owned by AuthViewModel, which also wipes the device on success. */
data class AccountDeletion(
    val inProgress: Boolean = false,
    val error: String? = null,
    val onDelete: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onSignOut: () -> Unit,
    deletion: AccountDeletion = AccountDeletion(),
    onOpenImportExport: () -> Unit = {},
    vm: ProfileViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TrackrTopBar("Settings", LocalProfile.current?.avatarUrl, {}, onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Appearance", style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)) {
                listOf(ThemeMode.DARK to "Dark (Cinematic)", ThemeMode.LIGHT to "Light", ThemeMode.SYSTEM to "Follow system").forEach { (mode, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.setTheme(mode) }.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.theme == mode, onClick = { vm.setTheme(mode) })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Text("Notifications", style = MaterialTheme.typography.headlineSmall)
            NotificationSettings(state.airingEnabled, vm::setAiringEnabled)

            Text("Sync", style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your list works offline and syncs to the cloud automatically.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = vm::syncNow, enabled = !state.syncing) { Text(if (state.syncing) "Syncing…" else "Sync now") }
                state.syncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            Text("Your data", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onOpenImportExport, modifier = Modifier.fillMaxWidth()) { Text("Import & export") }

            DebugSection()

            Text("About", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) { Text("About & attribution") }

            Button(
                onClick = { confirm = true }, modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            ) { Text("Sign out") }
            TextButton(
                onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete account") }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, title = { Text("Sign out?") },
        text = { Text("You can sign back in anytime; your list is stored in the cloud.") },
        confirmButton = { TextButton(onClick = { confirm = false; onSignOut() }) { Text("Sign out", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
    if (confirmDelete) DeleteAccountDialog(deletion, onDismiss = { confirmDelete = false; deletion.onDismissError() })
}

private const val DELETE_CONFIRMATION = "DELETE"

@Composable
private fun DeleteAccountDialog(deletion: AccountDeletion, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!deletion.inProgress) onDismiss() },
        title = { Text("Delete account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This permanently deletes your profile, your list and your friendships. It can't be undone.")
                Text("Type $DELETE_CONFIRMATION to confirm.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(typed, { typed = it }, singleLine = true, enabled = !deletion.inProgress, modifier = Modifier.fillMaxWidth())
                deletion.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = deletion.onDelete,
                enabled = typed.trim().equals(DELETE_CONFIRMATION, ignoreCase = true) && !deletion.inProgress,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(if (deletion.inProgress) "Deleting…" else "Delete forever") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !deletion.inProgress) { Text("Cancel") } },
    )
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    Column(Modifier.fillMaxSize()) {
        TrackrTopBar("About", LocalProfile.current?.avatarUrl, {}, onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLogo(80.dp)
            Text("Trackr", style = MaterialTheme.typography.displayMedium)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Track movies, TV shows and anime with your friends.", style = MaterialTheme.typography.bodyMedium)

            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Data sources", style = MaterialTheme.typography.titleMedium)
                Text(TMDB_ATTRIBUTION, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { open("https://www.themoviedb.org") }) { Text("themoviedb.org") }
                Text("Streaming availability for movies and TV is provided by JustWatch.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { open("https://www.justwatch.com") }) { Text("justwatch.com") }
                Text("Anime data and cover art are provided by AniList (anilist.co) through its public GraphQL API.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { open("https://anilist.co") }) { Text("anilist.co") }
            }
        }
    }
}

@Composable
private fun NotificationSettings(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    var canExact by remember { mutableStateOf(canScheduleExact(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canExact = canScheduleExact(context) }
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("New episode alerts", style = MaterialTheme.typography.bodyLarge)
                Text("Get notified when a title you're watching drops", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        SettingsLink("System notification settings") {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
        if (!canExact) SettingsLink("Allow exact timing") {
            if (Build.VERSION.SDK_INT >= 31) {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }
        }
    }
}

@Composable
private fun SettingsLink(label: String, onClick: () -> Unit) {
    Text(
        label, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary,
    )
}

private fun canScheduleExact(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 || (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
