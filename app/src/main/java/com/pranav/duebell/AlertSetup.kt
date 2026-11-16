package com.pranav.duebell

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun AlertSetup(
    bell: BellController,
    state: BellState,
    access: AlertAccess,
    refresh: () -> Unit,
    message: (String) -> Unit,
) {
    val context = LocalContext.current
    val packageUri = Uri.fromParts("package", context.packageName, null)
    var requestedNotifications by rememberSaveable { mutableStateOf(false) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val pickRingtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            bell.setRingtone(result.data?.pickedRingtone())
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SetupCard(
            "Notifications",
            if (access.notifications) "Allowed. You can adjust sound and banner visibility in system settings."
            else "Allow notifications for reminders and alarm controls.",
            if (access.notifications || requestedNotifications) "Notification settings" else "Allow notifications",
        ) {
            if (Build.VERSION.SDK_INT >= 33 && !access.notifications && !requestedNotifications) {
                requestedNotifications = true
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openSettings(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            SetupCard(
                "Exact alarms",
                if (access.precise) "Allowed. DueBell can schedule precise alarm times."
                else "Allow Alarms & reminders for wake-ups and failsafes to ring on time.",
                if (access.precise) "Alarm settings" else "Allow exact alarms",
            ) { context.openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)) }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            SetupCard(
                "Full-screen alerts",
                if (access.fullScreen) "Allowed. A ringing alert can open on the lock screen."
                else "Allow DueBell to open the ringing screen while your phone is locked.",
                if (access.fullScreen) "Full-screen settings" else "Allow full-screen alerts",
            ) { context.openSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)) }
        }
        SetupCard(
            "Do Not Disturb",
            "On Samsung, add DueBell under App notifications and allow alarms under Alarms and sounds. Test both with DND on.",
            "Open Do Not Disturb settings",
        ) { context.openSettings(Intent("android.settings.ZEN_MODE_SETTINGS")) }
        SetupCard(
            "Failsafe ringtone",
            if (state.ringtone == null || state.ringtone == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE).toString()) {
                "Use your phone's default call ringtone."
            } else {
                "A custom ringtone is selected."
            },
            "Choose ringtone",
        ) {
            try {
                pickRingtone.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(
                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            state.ringtone?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                        ),
                )
            } catch (_: ActivityNotFoundException) {
                message("This phone uses its default ringtone for failsafes.")
            }
        }
        SectionTitle("Test on this phone")
        Text("Each test starts in 30 seconds. Turn on DND and lock your phone after scheduling.")
        Button(onClick = {
            bell.testReminder()
            message("Test reminder scheduled. Its failsafe rings one minute later.")
        }, modifier = Modifier.fillMaxWidth()) { Text("Test reminder and failsafe") }
        OutlinedButton(onClick = {
            bell.testAlarm()
            message("Test alarm scheduled for 30 seconds from now.")
        }, modifier = Modifier.fillMaxWidth()) { Text("Test math alarm") }
    }
}

@Composable
private fun SetupCard(title: String, description: String, action: String, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}

private fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

@Suppress("DEPRECATION")
private fun Intent.pickedRingtone(): Uri? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    } else {
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }
