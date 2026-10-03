package com.trackr.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Stitch radii: sm 4, md/xl 12, lg 16 (posters & cards), xl 24 (bottom sheets), full = pill. */
val TrackrShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

val PillShape = RoundedCornerShape(percent = 50)
val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
