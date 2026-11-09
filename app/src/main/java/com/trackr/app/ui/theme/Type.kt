package com.trackr.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.trackr.app.R

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight) = Font(
    resId = R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val InterFamily = FontFamily(
    inter(FontWeight.Normal), inter(FontWeight.Medium), inter(FontWeight.SemiBold),
    inter(FontWeight.Bold), inter(FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double) = TextStyle(
    fontFamily = InterFamily, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = weight, letterSpacing = tracking.em,
)

val TrackrTypography = Typography(
    displayLarge = style(40, 48, FontWeight.SemiBold, -0.04),
    displayMedium = style(28, 34, FontWeight.SemiBold, -0.03),
    headlineLarge = style(22, 28, FontWeight.SemiBold, -0.02),
    headlineMedium = style(18, 24, FontWeight.Medium, -0.015),
    headlineSmall = style(16, 22, FontWeight.Medium, -0.01),
    titleLarge = style(16, 22, FontWeight.SemiBold, -0.01),
    titleMedium = style(14, 20, FontWeight.Medium, 0.0),
    titleSmall = style(13, 18, FontWeight.Medium, 0.0),
    bodyLarge = style(15, 22, FontWeight.Normal, 0.0),
    bodyMedium = style(13, 18, FontWeight.Normal, 0.0),
    bodySmall = style(12, 16, FontWeight.Normal, 0.005),
    labelLarge = style(13, 16, FontWeight.Medium, 0.01),
    labelMedium = style(12, 16, FontWeight.Medium, 0.01),
    labelSmall = style(11, 14, FontWeight.Medium, 0.04),
)
