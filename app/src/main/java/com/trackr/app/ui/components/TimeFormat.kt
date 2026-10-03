package com.trackr.app.ui.components

import java.text.DateFormat
import java.util.Date

fun relativeTime(thenMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val d = (nowMillis - thenMillis).coerceAtLeast(0)
    return when {
        d < 60_000 -> "just now"
        d < 3_600_000 -> "${d / 60_000}m ago"
        d < 86_400_000 -> "${d / 3_600_000}h ago"
        d < 7 * 86_400_000L -> "${d / 86_400_000}d ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(thenMillis))
    }
}
