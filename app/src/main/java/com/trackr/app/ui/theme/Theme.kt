package com.trackr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode { DARK, LIGHT, SYSTEM }

internal val DarkScheme = darkColorScheme(
    primary = AccentDark, onPrimary = Color.White,
    primaryContainer = AccentSurfaceDark, onPrimaryContainer = TextDark,
    inversePrimary = AccentLight,
    secondary = TextMutedDark, onSecondary = CanvasDark,
    secondaryContainer = SurfaceOverlayDark, onSecondaryContainer = TextDark,
    tertiary = TextMutedDark, onTertiary = CanvasDark,
    tertiaryContainer = SurfaceOverlayDark, onTertiaryContainer = TextDark,
    error = ErrorDark, onError = Color.White,
    errorContainer = ErrorSurfaceDark, onErrorContainer = Color(0xFFFFD2D4),
    background = CanvasDark, onBackground = TextDark,
    surface = CanvasDark, onSurface = TextDark, onSurfaceVariant = TextMutedDark,
    surfaceVariant = SurfaceOverlayDark, surfaceTint = Color.Transparent, surfaceBright = SurfaceHighDark,
    surfaceContainerLowest = CanvasDark, surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceRaisedDark, surfaceContainerHigh = SurfaceOverlayDark,
    surfaceContainerHighest = SurfaceHighDark,
    outline = BorderDark, outlineVariant = BorderSubtleDark,
    scrim = Color.Black,
)

internal val LightScheme = lightColorScheme(
    primary = AccentLight, onPrimary = Color.White,
    primaryContainer = AccentSurfaceLight, onPrimaryContainer = TextLight,
    inversePrimary = AccentDark,
    secondary = TextMutedLight, onSecondary = Color.White,
    secondaryContainer = SurfaceOverlayLight, onSecondaryContainer = TextLight,
    tertiary = TextMutedLight, onTertiary = Color.White,
    tertiaryContainer = SurfaceOverlayLight, onTertiaryContainer = TextLight,
    error = ErrorLight, onError = Color.White,
    errorContainer = ErrorSurfaceLight, onErrorContainer = ErrorLight,
    background = CanvasLight, onBackground = TextLight,
    surface = CanvasLight, onSurface = TextLight, onSurfaceVariant = TextMutedLight,
    surfaceVariant = SurfaceOverlayLight, surfaceTint = Color.Transparent, surfaceBright = SurfaceLight,
    surfaceContainerLowest = SurfaceLight, surfaceContainerLow = SurfaceRaisedLight,
    surfaceContainer = SurfaceRaisedLight, surfaceContainerHigh = SurfaceOverlayLight,
    surfaceContainerHighest = SurfaceHighLight,
    outline = BorderLight, outlineVariant = BorderSubtleLight,
    scrim = Color.Black,
)

/**
 * Always the Trackr schemes. Android 12+ wallpaper / Material You dynamic color is not applied,
 * including when the system appearance is followed.
 */
@Composable
fun TrackrTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val scheme = if (dark) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = scheme,
        typography = TrackrTypography,
        shapes = TrackrShapes,
        content = content,
    )
}
