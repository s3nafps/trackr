package com.trackr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode { DARK, LIGHT, SYSTEM }

internal val DarkScheme = darkColorScheme(
    primary = StitchPrimary, onPrimary = StitchOnPrimary,
    primaryContainer = StitchPrimaryContainer, onPrimaryContainer = StitchOnPrimaryContainer,
    inversePrimary = StitchInversePrimary,
    secondary = StitchSecondary, onSecondary = StitchOnSecondary,
    secondaryContainer = StitchSecondaryContainer, onSecondaryContainer = StitchOnSecondaryContainer,
    tertiary = StitchTertiary, onTertiary = StitchOnTertiary,
    tertiaryContainer = StitchTertiaryContainer, onTertiaryContainer = StitchOnTertiaryContainer,
    error = StitchError, onError = StitchOnError,
    errorContainer = StitchErrorContainer, onErrorContainer = StitchOnErrorContainer,
    background = StitchSurface, onBackground = StitchOnSurface,
    surface = StitchSurface, onSurface = StitchOnSurface, onSurfaceVariant = StitchOnSurfaceVariant,
    surfaceVariant = StitchSurfaceHighest, surfaceTint = StitchPrimary, surfaceBright = StitchSurfaceBright,
    surfaceContainerLowest = StitchSurfaceLowest, surfaceContainerLow = StitchSurfaceLow,
    surfaceContainer = StitchSurfaceContainer, surfaceContainerHigh = StitchSurfaceHigh,
    surfaceContainerHighest = StitchSurfaceHighest,
    outline = StitchOutline, outlineVariant = StitchOutlineVariant,
)

internal val LightScheme = lightColorScheme(
    primary = LightPrimary, onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer, onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary, onSecondary = Color.White,
    tertiary = LightTertiary, onTertiary = Color.White,
    background = LightSurface, onBackground = LightOnSurface,
    surface = LightSurface, onSurface = LightOnSurface, onSurfaceVariant = LightOnSurfaceVariant,
    surfaceVariant = LightSurfaceHighest,
    surfaceContainerLowest = LightSurfaceLowest, surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurfaceContainer, surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    outline = LightOutline, outlineVariant = LightOutlineVariant,
)

@Composable
fun TrackrTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = TrackrTypography,
        shapes = TrackrShapes,
        content = content,
    )
}
