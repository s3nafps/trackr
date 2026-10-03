package com.trackr.app.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Dataset
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackr.app.domain.model.ProfileStats
import com.trackr.app.ui.components.RatingDistributionChart
import com.trackr.app.ui.components.StatusBreakdown
import com.trackr.app.ui.components.TrackrTopBar
import com.trackr.app.ui.components.UserAvatar
import com.trackr.app.ui.theme.PillShape
import com.trackr.app.ui.theme.RatingAmber
import com.trackr.app.ui.theme.StatusWatching
import java.util.Locale

fun copyToClipboard(context: Context, label: String, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

fun shareInvite(context: Context, username: String, code: String) {
    val text = "Join me on Trackr! Add me with username \"$username\" or invite code $code."
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Share invite"))
}

@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onSignOut: () -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    ProfileContent(state, onOpenSettings, onOpenAbout, onSignOut, vm::editUsername, vm::resetEdit)
}

@Composable
fun ProfileContent(
    state: ProfileUiState,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onSignOut: () -> Unit,
    onEditUsername: (String) -> Unit,
    onResetEdit: () -> Unit,
) {
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val me = state.me
    LaunchedEffect(state.editDone) { if (state.editDone) { editing = false; onResetEdit() } }

    Column(Modifier.fillMaxSize()) {
        TrackrTopBar("Profile", me?.avatarUrl, {})
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(me?.username ?: "Your profile", style = MaterialTheme.typography.displayMedium, maxLines = 1, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundIcon(Icons.Filled.Share, "Share invite") { me?.let { shareInvite(context, it.username, it.inviteCode) } }
                    RoundIcon(Icons.Filled.Settings, "Settings", onClick = onOpenSettings)
                }
            }

            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    UserAvatar(me?.avatarUrl, size = 80.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("@${me?.username.orEmpty()}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${state.stats.total} titles tracked", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MiniStat(state.stats.total.toString(), "Logs")
                    MiniStat(state.stats.ratedCount.toString(), "Ratings")
                    MiniStat((state.stats.statusCounts.values.sum() - (state.stats.statusCounts.entries.firstOrNull { it.key.key == "plan_to_watch" }?.value ?: 0)).toString(), "Started")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Media Vault", style = MaterialTheme.typography.headlineSmall)
                    Text("Lifetime Tracker", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                VaultGrid(state.stats)
            }

            Text("Analytics & Trends", style = MaterialTheme.typography.headlineSmall)
            Card("Status Breakdown", "${state.stats.total} cataloged titles") { StatusBreakdown(state.stats.statusCounts) }
            Card(
                "Rating Distribution",
                state.stats.averageRating?.let { "Mean score: ${String.format(Locale.US, "%.1f", it)} ★" } ?: "No ratings yet",
            ) { RatingDistributionChart(state.stats.ratingDistribution) }

            Text("Account & Preferences", style = MaterialTheme.typography.headlineSmall)
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                PrefRow(Icons.Outlined.Edit, "Edit Username", me?.username ?: "", onClick = { editing = true })
                PrefRow(Icons.Outlined.DarkMode, "Theme & Appearance", state.theme.name.lowercase().replaceFirstChar { it.uppercase() }, onClick = onOpenSettings)
                Row(
                    Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconBadge(Icons.Outlined.QrCode2)
                    Column(Modifier.weight(1f)) {
                        Text("My Invite Code", style = MaterialTheme.typography.titleSmall)
                        Text("Friends add you with this code or your username", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { me?.let { copyToClipboard(context, "Invite code", it.inviteCode) } }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(me?.inviteCode ?: "–", style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Filled.ContentCopy, "Copy invite code", Modifier.size(16.dp))
                    }
                }
                PrefRow(Icons.Outlined.Dataset, "Data Sources & Attribution", "TMDB · AniList", onClick = onOpenAbout)
                PrefRow(Icons.AutoMirrored.Filled.Logout, "Sign Out", "Your list stays safe in the cloud", tint = MaterialTheme.colorScheme.error, onClick = { confirmSignOut = true })
            }
        }
    }

    if (editing) {
        var name by rememberSaveable { mutableStateOf(me?.username.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = false; onResetEdit() },
            title = { Text("Edit username") },
            text = {
                OutlinedTextField(
                    name, { name = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(24) }, singleLine = true,
                    isError = state.editError != null, supportingText = { Text(state.editError ?: "3–24 letters, numbers, . or _") },
                    shape = MaterialTheme.shapes.medium,
                )
            },
            confirmButton = { Button(onClick = { onEditUsername(name) }, enabled = !state.editBusy && name.length >= 3) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false; onResetEdit() }) { Text("Cancel") } },
        )
    }
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        title = { Text("Sign out?") },
        text = { Text("Unsynced changes are pushed first when you're online. You can sign back in anytime.") },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Sign out", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
    )
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(20.dp))
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VaultGrid(s: ProfileStats) {
    @Composable
    fun Tile(value: String, label: String, tag: String, tagColor: Color, modifier: Modifier) {
        Column(modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tag, style = MaterialTheme.typography.labelSmall, color = tagColor)
            Text(value, style = MaterialTheme.typography.displayMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(s.moviesWatched.toString(), "Movies Watched", "COMPLETED", StatusWatching, Modifier.weight(1f))
            Tile(s.tvShows.toString(), "TV Shows (${s.tvEpisodes} eps)", "TRACKED", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(s.animeTitles.toString(), "Anime (${s.animeEpisodes} eps)", "TRACKED", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            Tile("${s.hours}h", "Est. Screen Time", "ESTIMATE", RatingAmber, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Card(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint)
    }
}

@Composable
private fun PrefRow(icon: ImageVector, title: String, subtitle: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon, if (tint == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.onSurfaceVariant else tint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
