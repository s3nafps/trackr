package com.trackr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trackr.app.ui.theme.PillShape
import com.trackr.app.ui.theme.StatusWatching

/** 4dp track in outline-variant with a coloured fill. */
@Composable
fun TrackrProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = StatusWatching,
) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(PillShape).background(MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(PillShape).background(color))
    }
}
