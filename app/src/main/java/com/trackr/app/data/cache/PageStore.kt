package com.trackr.app.data.cache

import com.trackr.app.domain.model.MediaPage

/** Browse and search pages saved on the device, so Home and Search still show something offline. */
interface PageStore {
    suspend fun get(key: String): MediaPage?
    suspend fun put(key: String, page: MediaPage)

    /** Saves nothing (tests, and a repository built without one). */
    object None : PageStore {
        override suspend fun get(key: String): MediaPage? = null
        override suspend fun put(key: String, page: MediaPage) = Unit
    }
}
