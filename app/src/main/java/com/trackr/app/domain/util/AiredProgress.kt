package com.trackr.app.domain.util

/** How far +1 may take a title: its episode total, and the episodes out so far (announced episodes aren't watchable yet). */
object AiredProgress {
    /** The highest progress allowed, or null when neither is known. */
    fun ceiling(total: Int?, aired: Int?): Int? = listOfNotNull(total, aired).minOrNull()
}
