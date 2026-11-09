package com.trackr.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trackr.app.ui.components.BrandLogo

@Composable
fun LoginScreen(state: AuthUiState, onGoogle: (android.content.Context) -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(cs.background)
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        BrandLogo(72.dp)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Trackr", style = MaterialTheme.typography.displayLarge)
            Text(".", style = MaterialTheme.typography.displayLarge, color = cs.primary)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Track everything you watch.\nShare it with friends.",
            style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Text("Movies  ·  TV  ·  Anime", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.weight(1.4f))
        state.error?.let {
            Text(it, color = cs.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = { onGoogle(context) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = cs.onSurface, contentColor = cs.surface),
        ) {
            if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = cs.surface)
            else Text("Continue with Google", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Data provided by TMDB & AniList.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}
