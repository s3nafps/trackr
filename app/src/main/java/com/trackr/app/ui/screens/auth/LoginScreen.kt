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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trackr.app.ui.components.BrandLogo
import com.trackr.app.ui.theme.Violet
import com.trackr.app.ui.theme.TextSecondary

@Composable
fun LoginScreen(state: AuthUiState, onGoogle: (android.content.Context) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1633), MaterialTheme.colorScheme.background), endY = 1400f))
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        BrandLogo(104.dp)
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Trackr", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(".", style = MaterialTheme.typography.displayLarge, color = Violet)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Track everything you watch.\nShare it with friends.",
            style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1.4f))
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = { onGoogle(context) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2E2E8), contentColor = Color(0xFF1B1B1F)),
        ) {
            if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Violet)
            else Text("Continue with Google", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Data provided by TMDB & AniList.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}
