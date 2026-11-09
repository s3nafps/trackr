package com.trackr.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Near-monochrome palette modelled on T3 Code's unthemed UI (canvas, one blue
 * accent, zinc text, hairline borders). Posters carry the color; chrome does not.
 */

val CanvasDark = Color(0xFF0A0A0A)
val SurfaceDark = Color(0xFF111111)
val SurfaceRaisedDark = Color(0xFF141414)
val SurfaceOverlayDark = Color(0xFF191919)
val SurfaceHighDark = Color(0xFF222222)
val TextDark = Color(0xFFF5F5F5)
val TextMutedDark = Color(0xFF8F8F8F)
val BorderDark = Color(0xFF2E2E2E)
val BorderSubtleDark = Color(0xFF242424)
val AccentDark = Color(0xFF366FFB)
val AccentSurfaceDark = Color(0xFF121C35)

val CanvasLight = Color(0xFFFCFCFC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceRaisedLight = Color(0xFFF7F7F8)
val SurfaceOverlayLight = Color(0xFFF4F4F5)
val SurfaceHighLight = Color(0xFFE4E4E7)
val TextLight = Color(0xFF27272A)
val TextMutedLight = Color(0xFF71717B)
val BorderLight = Color(0xFFE4E4E7)
val BorderSubtleLight = Color(0xFFEEEEF0)
val AccentLight = Color(0xFF1B4ED8)
val AccentSurfaceLight = Color(0xFFE8EEFC)

val ErrorDark = Color(0xFFFB414A)
val ErrorLight = Color(0xFFC10007)
val ErrorSurfaceDark = Color(0xFF301214)
val ErrorSurfaceLight = Color(0xFFFCEBEC)

/** Status is functional, so the hues stay, but they are muted — not neon. */
val StatusWatching = AccentDark
val StatusCompleted = Color(0xFF3F8F6B)
val StatusPlan = Color(0xFF8A8A93)
val StatusDropped = Color(0xFFD4535A)
