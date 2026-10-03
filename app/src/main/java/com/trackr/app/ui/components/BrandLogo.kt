package com.trackr.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trackr.app.R
import com.trackr.app.ui.theme.BrandViolet
import com.trackr.app.ui.theme.StitchSurfaceLow

@Composable
fun BrandLogo(size: Dp = 96.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier.size(size).clip(shape).background(StitchSurfaceLow)
            .border(1.dp, BrandViolet.copy(alpha = 0.3f), shape),
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(size))
    }
}
