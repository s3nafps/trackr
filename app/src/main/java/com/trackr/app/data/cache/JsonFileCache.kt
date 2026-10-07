package com.trackr.app.data.cache

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * One JSON file per key in a folder of the app's cache directory (Android may clear it when space runs low, which only
 * means a miss). Only the [maxFiles] most recently written entries are kept.
 */
class JsonFileCache<T>(
    ctx: Context,
    folder: String,
    private val serializer: KSerializer<T>,
    private val json: Json,
    private val maxFiles: Int,
) {
    private val dir = File(ctx.cacheDir, folder)

    suspend fun get(key: String): T? = withContext(Dispatchers.IO) {
        val f = file(key)
        if (!f.exists()) return@withContext null
        runCatching { json.decodeFromString(serializer, f.readText()) }.getOrNull()
    }

    suspend fun put(key: String, value: T) = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            // Write then rename, so an entry being read is never half written.
            val tmp = File(dir, "${file(key).name}.tmp")
            tmp.writeText(json.encodeToString(serializer, value))
            tmp.renameTo(file(key))
            prune()
        }
        Unit
    }

    private fun prune() {
        val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return
        if (files.size <= maxFiles) return
        files.sortedByDescending { it.lastModified() }.drop(maxFiles).forEach { it.delete() }
    }

    private fun file(key: String) = File(dir, sha1(key) + ".json")

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
