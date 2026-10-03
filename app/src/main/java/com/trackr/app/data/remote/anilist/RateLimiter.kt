package com.trackr.app.data.remote.anilist

import okhttp3.Interceptor
import okhttp3.Response
import java.util.ArrayDeque

/**
 * Sliding-window limiter for AniList (limit 90 req/min). We stay at [maxPerMinute] (default 80) and
 * transparently wait or retry once after a 429 (honouring Retry-After).
 */
class AniListRateLimiter(
    private val maxPerMinute: Int = 80,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleeper: (Long) -> Unit = Thread::sleep,
) : Interceptor {
    private val stamps = ArrayDeque<Long>()

    /** Blocks until a slot is free; returns how long it waited (ms). Visible for tests. */
    @Synchronized
    fun acquire(): Long {
        var waited = 0L
        while (true) {
            val now = clock()
            while (stamps.isNotEmpty() && now - stamps.first() >= 60_000) stamps.removeFirst()
            if (stamps.size < maxPerMinute) {
                stamps.addLast(now)
                return waited
            }
            val wait = 60_000 - (now - stamps.first()) + 1
            sleeper(wait)
            waited += wait
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        acquire()
        var response = chain.proceed(chain.request())
        if (response.code == 429) {
            val retryAfter = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 60) ?: 5
            response.close()
            sleeper(retryAfter * 1000)
            acquire()
            response = chain.proceed(chain.request())
        }
        return response
    }
}
