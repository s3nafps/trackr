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
import com.trackr.app.ui.navigation.DetailTarget
import com.trackr.app.ui.theme.TrackrTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var openTarget by mutableStateOf<DetailTarget?>(null)

    private fun readTarget(intent: Intent?): DetailTarget? {
        val source = intent?.getStringExtra(AiringNotifier.EXTRA_OPEN_SOURCE) ?: return null
        val id = intent.getStringExtra(AiringNotifier.EXTRA_OPEN_ID) ?: return null
        val type = intent.getStringExtra(AiringNotifier.EXTRA_OPEN_TYPE) ?: return null
        return DetailTarget(source, type, id)
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
