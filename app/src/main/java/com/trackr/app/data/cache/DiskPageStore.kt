package com.trackr.app.data.cache

import android.content.Context
import com.trackr.app.domain.model.MediaPage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One JSON file per page in the app's cache directory (Android may clear it when space runs low, which only means a
 * miss). Only the [MAX_FILES] most recently written pages are kept.
 */
@Singleton
class DiskPageStore @Inject constructor(@ApplicationContext ctx: Context, private val json: Json) : PageStore {
    private val dir = File(ctx.cacheDir, "media-pages")

    override suspend fun get(key: String): MediaPage? = withContext(Dispatchers.IO) {
        val f = file(key)
        if (!f.exists()) return@withContext null
        runCatching { json.decodeFromString(MediaPage.serializer(), f.readText()) }.getOrNull()
    }

    override suspend fun put(key: String, page: MediaPage) = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            // Write then rename, so a page being read is never half written.
            val tmp = File(dir, "${file(key).name}.tmp")
            tmp.writeText(json.encodeToString(MediaPage.serializer(), page))
            tmp.renameTo(file(key))
            prune()
        }
        Unit
    }

    private fun prune() {
        val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return
        if (files.size <= MAX_FILES) return
        files.sortedByDescending { it.lastModified() }.drop(MAX_FILES).forEach { it.delete() }
    }

    private fun file(key: String) = File(dir, sha1(key) + ".json")

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_FILES = 400
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PageStoreModule {
    @Binds abstract fun pageStore(store: DiskPageStore): PageStore
}
