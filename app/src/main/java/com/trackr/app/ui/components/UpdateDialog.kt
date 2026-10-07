package com.trackr.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You have version ${BuildConfig.VERSION_NAME}. The new version installs over it and keeps your list.")
                if (info.highlights.isNotEmpty()) {
                    Text("What's new", style = MaterialTheme.typography.titleSmall)
                    info.highlights.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val started = info.apkUrl?.let { ApkInstaller.download(context, it, info.version) } == true
                if (!started) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl ?: info.pageUrl))) }
                onDownload()
            }) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onLater) { Text("Later") } },
    )
}
