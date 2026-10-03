package com.trackr.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trackr.app.ui.components.BrandLogo
import com.trackr.app.ui.theme.BrandViolet
import com.trackr.app.ui.theme.PillShape

@Composable
fun LoginScreen(state: AuthUiState, onGoogle: (android.content.Context) -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(cs.primaryContainer.copy(alpha = 0.22f), cs.background), endY = 1500f))
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        BrandLogo(104.dp)
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Trackr", style = MaterialTheme.typography.displayLarge)
            Text(".", style = MaterialTheme.typography.displayLarge, color = cs.primary)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Track everything you watch.\nShare it with friends.",
            style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("🍿 Movies", "📺 TV Series", "⛩ Anime").forEach {
                Text(
                    it, Modifier.clip(PillShape).background(cs.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Spacer(Modifier.weight(1.4f))
        state.error?.let {
            Text(it, color = cs.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = { onGoogle(context) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = cs.onSurface, contentColor = cs.surface),
        ) {
            if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = BrandViolet)
            else Text("Continue with Google", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Data provided by TMDB & AniList.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}
