package com.trackr.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.trackr.app.domain.model.ListStatus
import com.trackr.app.domain.model.MediaType

fun ListStatus.color(): Color = when (this) {
    ListStatus.WATCHING -> StatusWatching
    ListStatus.COMPLETED -> StatusCompleted
    ListStatus.PLAN_TO_WATCH -> StatusPlan
    ListStatus.DROPPED -> StatusDropped
}

fun MediaType.shortLabel(): String = when (this) {
    MediaType.MOVIE -> "Movie"
    MediaType.TV -> "TV"
    MediaType.ANIME -> "Anime"
}
