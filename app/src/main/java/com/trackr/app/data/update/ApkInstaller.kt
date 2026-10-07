package com.trackr.app.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * Downloads a release APK with the system downloader (its progress shows in the notifications) and opens the installer
 * once it's done. The first time, Android asks the user to allow installs from Trackr.
 */
object ApkInstaller {
    private const val TITLE = "Trackr update"
    private const val APK_TYPE = "application/vnd.android.package-archive"

    /** False when the download couldn't start, so the caller can fall back to the browser. */
    fun download(context: Context, url: String, version: String): Boolean = runCatching {
        val name = "trackr-$version.apk"
        // A finished download from an earlier try would otherwise block this one.
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name).delete()
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(TITLE)
            .setDescription("Trackr $version")
            .setMimeType(APK_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
        context.getSystemService(DownloadManager::class.java).enqueue(request)
        true
    }.getOrDefault(false)

    /** A download finished: installs it when it's ours, after sending the user to the install setting if needed. */
    fun onDownloadComplete(context: Context, id: Long) {
        if (id < 0) return
        val dm = context.getSystemService(DownloadManager::class.java)
        val ready = dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            c.moveToFirst() &&
                c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) == TITLE &&
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
        } == true
        if (!ready) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            context.startActivity(settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = dm.getUriForDownloadedFile(id) ?: return
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(install)
    }
}
