package com.trackr.app.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import java.io.File
import java.security.MessageDigest

/**
 * Downloads a release APK with the system downloader (its progress shows in the notifications) and opens the installer
 * once it's done. The first time, Android asks the user to allow installs from Trackr.
 *
 * Only the download this started is installed, and only when the file matches the SHA-256 GitHub publishes for it.
 */
object ApkInstaller {
    private const val TITLE = "Trackr update"
    private const val APK_TYPE = "application/vnd.android.package-archive"
    private const val PREFS = "apk-update"
    private const val KEY_ID = "download_id"
    private const val KEY_SHA256 = "sha256"

    /** False when the download couldn't start, so the caller can fall back to the browser. */
    fun download(context: Context, url: String, version: String, sha256: String): Boolean = runCatching {
        val name = "trackr-$version.apk"
        // A finished download from an earlier try would otherwise block this one.
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name).delete()
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(TITLE)
            .setDescription("Trackr $version")
            .setMimeType(APK_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
        val id = context.getSystemService(DownloadManager::class.java).enqueue(request)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_ID, id)
            .putString(KEY_SHA256, sha256.lowercase())
            .apply()
        true
    }.getOrDefault(false)

    /** A download finished: installs it when it's the one this app started and its checksum matches. */
    fun onDownloadComplete(context: Context, id: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (id < 0 || id != prefs.getLong(KEY_ID, -1L)) return
        val expected = prefs.getString(KEY_SHA256, null) ?: return
        val dm = context.getSystemService(DownloadManager::class.java)
        val ready = dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            c.moveToFirst() &&
                c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) == TITLE &&
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
        } == true
        if (!ready) return
        val uri = dm.getUriForDownloadedFile(id) ?: return
        if (sha256Of(context, uri) != expected) {
            // Not the file GitHub published: drop it rather than offer it to the installer.
            dm.remove(id)
            prefs.edit().remove(KEY_ID).remove(KEY_SHA256).apply()
            return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            context.startActivity(settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        prefs.edit().remove(KEY_ID).remove(KEY_SHA256).apply()
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(install)
    }

    /** The file's SHA-256 in lowercase hex, or null when it can't be read. */
    private fun sha256Of(context: Context, uri: Uri): String? = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
