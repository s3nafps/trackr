package com.trackr.app.data.cache

import com.trackr.app.domain.model.MediaDetail

/** Title pages saved on the device, so a title you opened once still opens offline. */
interface DetailStore {
    suspend fun get(key: String): MediaDetail?
    suspend fun put(key: String, detail: MediaDetail)

    /** Saves nothing (tests, and a repository built without one). */
    object None : DetailStore {
        override suspend fun get(key: String): MediaDetail? = null
        override suspend fun put(key: String, detail: MediaDetail) = Unit
    }
}
