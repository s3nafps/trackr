package com.trackr.app.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Consecutive days (ending today, or yesterday if nothing yet today) with at least one list update. */
fun computeStreak(updatedAtMillis: Collection<Long>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Int {
    val days = updatedAtMillis.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet()
    var cursor = if (today in days) today else today.minusDays(1)
    var n = 0
    while (cursor in days) { n++; cursor = cursor.minusDays(1) }
    return n
}
