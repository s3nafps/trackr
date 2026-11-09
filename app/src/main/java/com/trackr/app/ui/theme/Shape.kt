package com.trackr.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Small radii. Sheets get a slightly larger top corner; nothing is a full pill. */
val TrackrShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/** Kept as a name so existing call sites stay put. It is a 6dp corner, not a stadium. */
val PillShape = RoundedCornerShape(6.dp)
val SheetShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
