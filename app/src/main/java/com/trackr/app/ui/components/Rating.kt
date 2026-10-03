package com.trackr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trackr.app.ui.theme.PillShape
import com.trackr.app.ui.theme.RatingAmber
import java.util.Locale

fun formatScore(score: Double): String =
    if (score >= 10.0) "10" else String.format(Locale.US, "%.1f", score)

/** Amber star + score, used over posters (dark scrim) and inline. */
@Composable
fun RatingBadge(
    score: Double,
    modifier: Modifier = Modifier,
    outOfTen: Boolean = false,
    scrim: Boolean = true,
) {
    val bg = if (scrim) MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f)
    else RatingAmber.copy(alpha = 0.15f)
    Row(
        modifier.clip(MaterialTheme.shapes.small).background(bg).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(Icons.Filled.Star, null, Modifier.size(12.dp), tint = RatingAmber)
        Text(formatScore(score), style = MaterialTheme.typography.labelMedium, color = if (scrim) RatingAmber else RatingAmber)
        if (outOfTen) Text("/ 10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Pill used on detail header: bigger star, "8.6 / 10". */
@Composable
fun ScorePill(score: Double, modifier: Modifier = Modifier, tint: Color = RatingAmber) {
    Row(
        modifier.clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.Star, null, Modifier.size(16.dp), tint = tint)
        Text(formatScore(score), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("/ 10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
