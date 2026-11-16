package com.pranav.duebell

import android.app.AlarmManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class DueBellApplication : Application() {
    internal lateinit var bell: BellController
        private set

    override fun onCreate() {
        super.onCreate()
        bell = BellController(this)
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == Intent.ACTION_USER_PRESENT) bell.onUnlock()
                }
            },
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}

internal val Context.bell: BellController
    get() = (applicationContext as DueBellApplication).bell

class BellReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(BellAction.ITEM)
        val bell = context.bell
        when (intent.action) {
            BellAction.CLOCK, BellAction.NOTIFY -> bell.onTick()
            BellAction.DONE -> id?.let(bell::done)
            BellAction.AGAIN -> id?.let(bell::again)
            BellAction.SNOOZE -> id?.let(bell::snooze)
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> bell.restore(clockChanged = true)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> bell.restore()
        }
    }
}

internal class BellController(private val context: Context) {
    private val store = BellStore(context)
    private val timers = BellTimers(context)
    val alerts = BellAlerts(context, timers)
    val state = store.state
    private val current get() = state.value

    fun addReminder(previousId: String?, title: String, dueAt: Long, onUnlock: Boolean, failsafe: Int?) {
        val item = Reminder(UUID.randomUUID().toString(), title, dueAt, onUnlock, failsafe)
        save(
            current.copy(
                reminders = current.reminders.filterNot { it.id == previousId } + item,
                rings = current.rings.filterNot { it.itemId == previousId },
            ),
        )
    }

    fun addAlarm(previousId: String?, title: String, hour: Int, minute: Int, repeat: Repeat, walk: Boolean) {
        if (wakeAlarmIsRinging(previousId)) return
        val old = current.alarms.find { it.id == previousId }
        val next = if (old == null || enabled(old)) repeat.nextAt(hour, minute, now(), zone()) else null
        val alarm = WakeAlarm(UUID.randomUUID().toString(), title, hour, minute, repeat, walk, next)
        save(
            current.copy(
                alarms = current.alarms.filterNot { it.id == previousId } + alarm,
                rings = current.rings.filterNot { it.itemId == previousId },
            ),
        )
    }

    fun enabled(alarm: WakeAlarm): Boolean = alarm.nextAt != null || current.rings.any { it.itemId == alarm.id }

    fun toggleAlarm(id: String, enabled: Boolean) {
        if (wakeAlarmIsRinging(id)) return
        val alarm = current.alarms.find { it.id == id } ?: return
        if (enabled == enabled(alarm)) return
        val time = if (enabled) alarm.repeat.nextAt(alarm.hour, alarm.minute, now(), zone()) else null
        save(
            current.copy(
                alarms = current.alarms.map { if (it.id == id) it.copy(nextAt = time) else it },
                rings = current.rings.filterNot { !enabled && it.itemId == id },
            ),
        )
    }

    fun delete(id: String) {
        if (wakeAlarmIsRinging(id)) return
        save(
            current.copy(
                reminders = current.reminders.filterNot { it.id == id },
                alarms = current.alarms.filterNot { it.id == id },
                rings = current.rings.filterNot { it.itemId == id },
            ),
        )
    }

    private fun wakeAlarmIsRinging(id: String?): Boolean =
        current.rings.any { it.kind == RingKind.ALARM && it.itemId == id }

    fun done(id: String) = save(BellRules.complete(current, id, now()))

    fun again(id: String) {
        save(BellRules.remindAgain(current, id, now()))
        current.reminders.find { it.id == id && it.pending }?.let { alerts.reminder(it, audible = false) }
    }

    fun snooze(id: String) = save(BellRules.snooze(current, id, now()))

    fun solveMath(id: String, answer: Int?): Boolean {
        val next = BellRules.solveMath(current, id, answer, now(), zone())
        if (next == current) return false
        save(next)
        return true
    }

    fun countStep(id: String) = save(BellRules.step(current, id, now(), zone()))

    fun useMathOnly(id: String) = save(BellRules.useMathOnly(current, id))

    fun setRingtone(uri: Uri?) {
        store.save(current.copy(ringtone = uri?.toString()))
    }

    fun testReminder() {
        val due = now() + 30_000
        addReminder(null, "DueBell test reminder", due, onUnlock = true, failsafe = 1)
    }

    fun testAlarm() {
        val due = now() + 30_000
        val local = Instant.ofEpochMilli(due).atZone(zone())
        save(
            current.copy(
                alarms = current.alarms + WakeAlarm(
                    UUID.randomUUID().toString(), "DueBell test alarm", local.hour, local.minute,
                    Repeat.ONCE, walk = false, nextAt = due,
                ),
            ),
        )
    }

    fun onTick() {
        val before = current
        publish(BellRules.advance(current, now()))
        if (sameDeadlines(before, current)) timers.schedule(current)
        startRinging()
    }

    fun onUnlock() {
        val events = BellRules.onUnlock(current)
        if (events.notify.isNotEmpty()) publish(events)
    }

    fun onVisible() {
        onTick()
        if (alerts.access().notifications) alerts.restoreReminders(current)
    }

    fun restore(clockChanged: Boolean = false) {
        if (clockChanged) {
            val changedAt = now()
            val alarms = current.alarms.map {
                if (it.nextAt != null) {
                    it.copy(nextAt = it.repeat.nextAt(it.hour, it.minute, changedAt, zone()))
                } else {
                    it
                }
            }
            store.save(current.copy(alarms = alarms))
        }
        timers.schedule(current, restoreRinging = !RingService.running)
        if (alerts.access().notifications) alerts.restoreReminders(current)
    }

    private fun publish(events: DueEvents) {
        save(events.state)
        events.state.reminders.filter { it.id in events.notify }
            .forEach { alerts.reminder(it, audible = true) }
    }

    private fun save(next: BellState) {
        val previous = current
        val removedAlerts = previous.reminders.filter { it.pending }.map { it.id }.toSet() -
            next.reminders.filter { it.pending }.map { it.id }.toSet()
        store.save(next)
        if (!sameDeadlines(previous, next)) timers.schedule(next)
        removedAlerts.forEach(alerts::cancelReminder)
        if (previous.ringing?.id != next.ringing?.id) alerts.cancelBackupRing()
    }

    private fun sameDeadlines(previous: BellState, next: BellState): Boolean =
        previous.nextNotificationAt() == next.nextNotificationAt() && previous.nextRingAt() == next.nextRingAt()

    private fun startRinging() {
        val ring = current.ringing ?: return
        if (RingService.running) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, RingService::class.java))
            alerts.cancelBackupRing()
        } catch (error: RuntimeException) {
            Log.w("DueBell", "Could not start alarm audio in the background", error)
            alerts.backupRing(ring)
        }
    }

    private fun now() = System.currentTimeMillis()
    private fun zone() = ZoneId.systemDefault()
}
