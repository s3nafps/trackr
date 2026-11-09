package com.trackr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import java.util.Locale

fun formatScore(score: Double): String =
    if (score >= 10.0) "10" else String.format(Locale.US, "%.1f", score)

/** Score chip. On a poster it sits on a solid surface so the artwork stays intact. */
@Composable
fun RatingBadge(
    score: Double,
    modifier: Modifier = Modifier,
    outOfTen: Boolean = false,
    scrim: Boolean = true,
) {
    val bg = if (scrim) MaterialTheme.colorScheme.surface.copy(alpha = 0.92f) else Color.Transparent
    Row(
        modifier.clip(MaterialTheme.shapes.extraSmall).background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(Icons.Filled.Star, null, Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurface)
        Text(formatScore(score), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        if (outOfTen) Text("/ 10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Detail header score: star and "8.6 / 10" inside a hairline. */
@Composable
fun ScorePill(score: Double, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    val star = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint
    Row(
        modifier.clip(PillShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, PillShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = star)
        Text(formatScore(score), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("/ 10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
