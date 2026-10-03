package com.trackr.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Returns `ensure(then)`: runs the permission prompt when needed and reports the outcome to [onResult];
 * [then] runs only when notifications end up allowed. Below API 33 there is no prompt, it just reports
 * whether notifications are enabled.
 */
@Composable
fun rememberNotificationPermission(onResult: (granted: Boolean) -> Unit): (then: () -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResult(granted)
        if (granted) pending[0]?.invoke()
        pending[0] = null
    }
    return { then ->
        if (Build.VERSION.SDK_INT < 33) {
            val on = NotificationManagerCompat.from(context).areNotificationsEnabled()
            onResult(on)
            if (on) then()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            onResult(true)
            then()
        } else {
            pending[0] = then
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
