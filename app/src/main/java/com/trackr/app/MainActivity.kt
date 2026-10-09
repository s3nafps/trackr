package com.trackr.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackr.app.ui.ThemeViewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.trackr.app.data.airing.AiringNotifier
import com.trackr.app.ui.AppRoot
import com.trackr.app.ui.navigation.DeepLinks
import com.trackr.app.ui.navigation.DetailTarget
import com.trackr.app.ui.theme.TrackrTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var openTarget by mutableStateOf<DetailTarget?>(null)

    /** Notification/widget extras, or a shared title link (trackr://, TMDB or AniList page). */
    private fun readTarget(intent: Intent?): DetailTarget? {
        if (intent?.action == Intent.ACTION_VIEW) DeepLinks.parse(intent.dataString)?.let { return it }
        // The extras come from any app that starts this activity, so they're checked like a link.
        return DeepLinks.target(
            intent?.getStringExtra(AiringNotifier.EXTRA_OPEN_SOURCE),
            intent?.getStringExtra(AiringNotifier.EXTRA_OPEN_TYPE),
            intent?.getStringExtra(AiringNotifier.EXTRA_OPEN_ID),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readTarget(intent)?.let { openTarget = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openTarget = readTarget(intent)
        setContent {
            val theme: ThemeViewModel = hiltViewModel()
            val mode by theme.mode.collectAsStateWithLifecycle()
            TrackrTheme(mode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AppRoot(openTarget = openTarget, onTargetOpened = { openTarget = null }) }
            }
        }
    }
}
