package com.trackr.app.domain.util

import com.trackr.app.domain.model.ListStatus

object AiringPolicy {
    /** Watching titles are always eligible; Plan to Watch only with the bell on. */
    fun isEligible(status: ListStatus, notify: Boolean): Boolean = when (status) {
        ListStatus.WATCHING -> true
        ListStatus.PLAN_TO_WATCH -> notify
        else -> false
    }
}
