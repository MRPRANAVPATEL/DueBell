package com.pranav.duebell

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal object BellAction {
    const val CLOCK = "com.pranav.duebell.CLOCK"
    const val NOTIFY = "com.pranav.duebell.NOTIFY"
    const val DONE = "com.pranav.duebell.DONE"
    const val AGAIN = "com.pranav.duebell.AGAIN"
    const val SNOOZE = "com.pranav.duebell.SNOOZE"
    const val ITEM = "item"
}

internal fun Context.stepDetector(): Sensor? =
    getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

internal fun Context.hasMotionAccess(): Boolean =
    Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED

internal data class AlertAccess(val notifications: Boolean, val precise: Boolean, val fullScreen: Boolean)

internal class BellTimers(private val context: Context) {
    private val manager = checkNotNull(context.getSystemService(AlarmManager::class.java))
    private val clock = bellAction(context, BellAction.CLOCK)
    private val notify = bellAction(context, BellAction.NOTIFY)

    fun precise(): Boolean = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    fun schedule(state: BellState, restoreRinging: Boolean = false) {
        val now = System.currentTimeMillis()
        val ringAt = if (restoreRinging && state.ringing != null) now + 500 else state.nextRingAt()
        arm(clock, ringAt, now, audible = true)
        arm(notify, state.nextNotificationAt(), now, audible = false)
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun arm(intent: PendingIntent, time: Long?, now: Long, audible: Boolean) {
        if (time == null) {
            manager.cancel(intent)
            return
        }
        val whenToFire = maxOf(time, now + 250)
        if (precise()) {
            try {
                if (audible) {
                    val show = PendingIntent.getActivity(
                        context, 1, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    manager.setAlarmClock(AlarmManager.AlarmClockInfo(whenToFire, show), intent)
                } else {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenToFire, intent)
                }
                return
            } catch (_: SecurityException) {
                // The user can revoke exact alarm access between the check and scheduling.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenToFire, intent)
    }
}

internal fun bellAction(context: Context, action: String, id: String = ""): PendingIntent {
    val uri = Uri.Builder().scheme("duebell").authority("action").appendPath(action).appendPath(id).build()
    val intent = Intent(context, BellReceiver::class.java).setAction(action).setData(uri)
        .putExtra(BellAction.ITEM, id)
    return PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

internal class BellAlerts(private val context: Context, private val timers: BellTimers) {
    private val manager = checkNotNull(context.getSystemService(NotificationManager::class.java))

    init {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(REMINDERS, "Task reminders", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = "Due reminders and follow-ups"
                        enableVibration(true)
                    },
                NotificationChannel(RINGING, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = "Wake-up alarms and call-style reminders"
                        setSound(null, null)
                    },
                NotificationChannel(BACKUP, "Backup alarm alerts", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = "Alerts when the ringing screen cannot start"
                        setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                        )
                        enableVibration(true)
                    },
            ),
        )
    }

    fun access(): AlertAccess = AlertAccess(
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        precise = timers.precise(),
        fullScreen = Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent(),
    )

    @SuppressLint("MissingPermission")
    fun reminder(item: Reminder, audible: Boolean) {
        if (!access().notifications) return
        val notification = NotificationCompat.Builder(context, REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle(item.title)
            .setContentText("Reminder • Pending")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Reminder • Pending"))
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(!audible)
            .setSilent(!audible)
            .setWhen(item.dueAt)
            .addAction(0, "Done", bellAction(context, BellAction.DONE, item.id))
            .addAction(0, "Remind again", bellAction(context, BellAction.AGAIN, item.id))
            .build()
        if (audible) cancelReminder(item.id)
        manager.notify(item.id, REMINDER_ID, notification)
    }

    fun restoreReminders(state: BellState) {
        val shown = manager.activeNotifications.mapNotNull { it.tag }.toSet()
        state.reminders.filter { it.pending && it.doneAt == null && it.id !in shown }
            .forEach { reminder(it, audible = false) }
    }

    fun cancelReminder(id: String) = manager.cancel(id, REMINDER_ID)

    fun ring(session: RingSession, backup: Boolean = false): Notification {
        val intent = Intent(context, RingActivity::class.java)
            .setData(Uri.Builder().scheme("duebell").authority("ring").appendPath(session.id).build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = if (session.kind == RingKind.ALARM) "Wake-up alarm" else "Failsafe reminder"
        val notification = NotificationCompat.Builder(context, if (backup) BACKUP else RINGING)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle(session.title)
            .setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (access().fullScreen) notification.setFullScreenIntent(open, true)
        if (session.kind == RingKind.REMINDER) {
            notification
                .addAction(0, "Done", bellAction(context, BellAction.DONE, session.itemId))
                .addAction(0, "Snooze 1 hour", bellAction(context, BellAction.SNOOZE, session.id))
        } else {
            notification.addAction(0, "Open challenge", open)
        }
        return notification.build()
    }

    @SuppressLint("MissingPermission")
    fun backupRing(session: RingSession) {
        if (access().notifications) manager.notify(BACKUP_ID, ring(session, backup = true))
    }

    fun cancelBackupRing() = manager.cancel(BACKUP_ID)

    fun endedRing(): Notification = NotificationCompat.Builder(context, RINGING)
        .setSmallIcon(R.drawable.ic_stat_bell)
        .setContentTitle(context.getString(R.string.app_name))
        .setSilent(true)
        .build()

    companion object {
        const val FOREGROUND_RING_ID = 7401
        private const val REMINDER_ID = 7402
        private const val BACKUP_ID = 7403
        private const val REMINDERS = "reminders"
        private const val RINGING = "ringing"
        private const val BACKUP = "alarm_backup"
    }
}
