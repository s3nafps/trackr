package com.trackr.app.data.util

import java.util.concurrent.ConcurrentHashMap

/** Tiny in-memory cache with per-entry expiry – "cache API responses briefly". */
class TtlCache<K : Any, V : Any>(private val ttlMillis: Long, private val clock: () -> Long = System::currentTimeMillis) {
    private data class Entry<V>(val value: V, val at: Long)

    private val map = ConcurrentHashMap<K, Entry<V>>()

    fun get(key: K): V? {
        val e = map[key] ?: return null
        return if (clock() - e.at <= ttlMillis) e.value else null
    }

    /** Returns even expired values (used as an offline fallback). */
    fun getStale(key: K): V? = map[key]?.value

    fun put(key: K, value: V) { map[key] = Entry(value, clock()) }

    fun invalidate(key: K) { map.remove(key) }

    fun clear() = map.clear()
}
