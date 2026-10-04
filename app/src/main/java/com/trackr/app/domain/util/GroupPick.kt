package com.trackr.app.domain.util

import kotlin.random.Random

/** "Pick for us": a random title from a shared list or a Watch Together overlap. */
object GroupPick {
    /** A random item, avoiding [previous] when anything else is left, so "pick again" always changes. */
    fun <T> pick(items: List<T>, previous: T? = null, random: Random = Random.Default): T? {
        if (items.isEmpty()) return null
        val pool = items.filter { it != previous }.ifEmpty { items }
        return pool[random.nextInt(pool.size)]
    }
}
