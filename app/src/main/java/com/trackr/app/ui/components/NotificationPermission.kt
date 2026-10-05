package com.trackr.app.ui.components

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

/** Android 12+ asks the user before an app may fire alarms at an exact time (episode alerts); older versions always allow it. */
fun canScheduleExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 || (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

/** Opens Trackr's "Alarms & reminders" page in system settings (Android 12+). */
fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < 31) return
    runCatching {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
    }
}
