package com.trackr.app.data.cache

import android.content.Context
import com.trackr.app.domain.model.MediaPage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Browse and search pages, newest 400 kept (see [JsonFileCache]). */
@Singleton
class DiskPageStore @Inject constructor(@ApplicationContext ctx: Context, json: Json) : PageStore {
    private val files = JsonFileCache(ctx, "media-pages", MediaPage.serializer(), json, MAX_FILES)

    override suspend fun get(key: String): MediaPage? = files.get(key)

    override suspend fun put(key: String, page: MediaPage) = files.put(key, page)

    private companion object {
        const val MAX_FILES = 400
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PageStoreModule {
    @Binds abstract fun pageStore(store: DiskPageStore): PageStore

    @Binds abstract fun detailStore(store: DiskDetailStore): DetailStore
}
