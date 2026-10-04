package com.trackr.app.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Reads files picked with the system file picker (unpacking zip/gzip) and writes exports to a chosen location. */
@Singleton
class DocumentStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    suspend fun read(uri: Uri): Map<String, String> = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: uri.lastPathSegment ?: "file"
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { ImportFiles.readCapped(it) }
            ?: throw ImportException("Couldn't open that file.")
        ImportFiles.unpack(name, bytes)
    }

    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("Couldn't save the file.")
    }

    private fun displayName(uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
