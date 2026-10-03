package com.trackr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

enum class ThemeMode { DARK, LIGHT, SYSTEM }

private val DarkScheme = darkColorScheme(
    primary = Violet, onPrimary = TextPrimary,
    primaryContainer = Color(0xFF2A2350), onPrimaryContainer = VioletSoft,
    secondary = Amber, onSecondary = Color(0xFF452B00),
    tertiary = StatusWatching, onTertiary = Color(0xFF00354A),
    background = Canvas, onBackground = TextPrimary,
    surface = Canvas, onSurface = TextPrimary, onSurfaceVariant = TextSecondary,
    surfaceVariant = SurfaceHigh,
    surfaceContainerLowest = Canvas, surfaceContainerLow = SurfaceBase,
    surfaceContainer = SurfaceBase, surfaceContainerHigh = SurfaceHigh, surfaceContainerHighest = SurfaceHigh,
    outline = TextSubdued, outlineVariant = Outline,
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A),
)

private val LightScheme = lightColorScheme(
    primary = LightViolet, onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF), onPrimaryContainer = Color(0xFF1C0062),
    secondary = Color(0xFFB7740B), onSecondary = Color.White,
    tertiary = Color(0xFF0B7FB0),
    background = LightCanvas, onBackground = LightText,
    surface = LightCanvas, onSurface = LightText, onSurfaceVariant = LightTextSecondary,
    surfaceVariant = LightSurfaceHigh,
    surfaceContainerLowest = LightSurface, surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface, surfaceContainerHigh = LightSurfaceHigh, surfaceContainerHighest = LightSurfaceHigh,
    outline = Color(0xFF7B8194), outlineVariant = LightOutline,
)

val TrackrShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
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
