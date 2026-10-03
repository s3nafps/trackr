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
    displayLarge = style(36, 44, FontWeight.ExtraBold, -0.03),
    displayMedium = style(28, 36, FontWeight.Bold, -0.025),
    headlineLarge = style(24, 32, FontWeight.Bold, -0.02),
    headlineMedium = style(20, 28, FontWeight.SemiBold, -0.015),
    headlineSmall = style(18, 24, FontWeight.SemiBold, -0.01),
    titleLarge = style(20, 28, FontWeight.SemiBold, -0.015),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.0),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.01),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.0),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.0),
    bodySmall = style(12, 16, FontWeight.Normal, 0.01),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.01),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.02),
    labelSmall = style(11, 14, FontWeight.SemiBold, 0.03),
)
