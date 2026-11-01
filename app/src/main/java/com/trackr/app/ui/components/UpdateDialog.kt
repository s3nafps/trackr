package com.trackr.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.trackr.app.BuildConfig
import com.trackr.app.data.update.ApkInstaller
import com.trackr.app.data.update.UpdateInfo

/** "Trackr 1.4.0 is available": Download fetches the APK with the system downloader and opens the installer; the release page is the fallback. */
@Composable
fun UpdateDialog(info: UpdateInfo, onDownload: () -> Unit, onLater: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onLater,
        icon = { Icon(Icons.Outlined.SystemUpdate, null) },
        title = { Text("Trackr ${info.version} is available") },
        text = {
            // Every release since the installed one, newest first; scrolls when there's a lot.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You have version ${BuildConfig.VERSION_NAME}. The new version installs over it and keeps your list.")
                val changes = info.changes.filter { it.items.isNotEmpty() }
                changes.forEach { release ->
                    Text(
                        if (changes.size == 1) "What's new" else "What's new in ${release.version}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    release.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val url = info.apkUrl
                val sha256 = info.apkSha256
                val started = url != null && sha256 != null && ApkInstaller.download(context, url, info.version, sha256)
                if (!started) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl ?: info.pageUrl))) }
                onDownload()
            }) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onLater) { Text("Later") } },
    )
}
