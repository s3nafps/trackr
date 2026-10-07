package com.trackr.app.data.cache

import android.content.Context
import com.trackr.app.domain.model.MediaDetail
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Title pages, newest 200 kept (see [JsonFileCache]). */
@Singleton
class DiskDetailStore @Inject constructor(@ApplicationContext ctx: Context, json: Json) : DetailStore {
    private val files = JsonFileCache(ctx, "media-details", MediaDetail.serializer(), json, MAX_FILES)

    override suspend fun get(key: String): MediaDetail? = files.get(key)

    override suspend fun put(key: String, detail: MediaDetail) = files.put(key, detail)

    private companion object {
        const val MAX_FILES = 200
    }
}
