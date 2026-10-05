package com.trackr.app.domain.util

/** A dotted release version such as "1.3.0" (or the tag "v1.3.0"), compared part by part so 1.10.0 is newer than 1.9.0. */
data class AppVersion(val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val c = parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    override fun toString() = parts.joinToString(".")

    companion object {
        /** Null for anything that isn't a version, e.g. "nightly". A "-beta"/"+build" suffix is ignored. */
        fun parse(text: String): AppVersion? {
            val core = text.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
            if (core.isEmpty()) return null
            return AppVersion(core.split('.').map { it.toIntOrNull() ?: return null })
        }
    }
}
