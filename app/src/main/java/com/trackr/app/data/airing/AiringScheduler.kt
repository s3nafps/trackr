package com.trackr.app.data.airing

import com.trackr.app.data.local.AiringEntity

/** Mirrors the airing table into alarms. Idempotent: same title → same request code, so moved = replaced. */
class AiringScheduler(
    private val client: AlarmClient,
    private val fireNow: (AiringEntity) -> Unit,
) {
    fun apply(upserted: List<AiringEntity>, removed: List<AiringEntity>, now: Long = System.currentTimeMillis()) {
        removed.forEach { client.cancel(requestCode(it.source, it.externalId)) }
        for (row in upserted) {
            val code = requestCode(row.source, row.externalId)
            when {
                row.notified -> client.cancel(code)
                row.airAt > now -> client.set(code, row.airAt, client.canScheduleExact(), row.source, row.externalId)
                now - row.airAt <= LATE_WINDOW_MS -> fireNow(row)
            }
        }
    }

    fun cancelAll(rows: List<AiringEntity>) = rows.forEach { client.cancel(requestCode(it.source, it.externalId)) }

    companion object {
        private const val LATE_WINDOW_MS = 12 * 3_600_000L
        fun requestCode(source: String, externalId: String): Int = "$source:$externalId".hashCode()
    }
}
