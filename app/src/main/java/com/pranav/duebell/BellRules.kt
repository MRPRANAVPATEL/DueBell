package com.pranav.duebell

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

internal const val MINUTE = 60_000L
internal const val UNLOCK_BACKUP_MINUTES = 15
internal const val SNOOZE_MINUTES = 60
internal const val STEP_GOAL = 10

internal enum class Repeat(val label: String) {
    ONCE("Once"),
    DAILY("Every day"),
    WEEKDAYS("Weekdays");

    fun nextAt(hour: Int, minute: Int, after: Long, zone: ZoneId): Long {
        val date = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        val time = LocalTime.of(hour, minute)
        return (0L..7L)
            .asSequence()
            .map(date::plusDays)
            .filter { this != WEEKDAYS || it.dayOfWeek <= DayOfWeek.FRIDAY }
            .map { it.atTime(time).atZone(zone).toInstant().toEpochMilli() }
            .first { it > after }
    }
}

internal data class Reminder(
    val id: String,
    val title: String,
    val dueAt: Long,
    val onUnlock: Boolean,
    val failsafeMinutes: Int?,
    val pending: Boolean = false,
    val doneAt: Long? = null,
    val nudgeAt: Long? = null,
    val ringAt: Long? = failsafeMinutes?.let { dueAt + it * MINUTE },
)

internal data class WakeAlarm(
    val id: String,
    val title: String,
    val hour: Int,
    val minute: Int,
    val repeat: Repeat,
    val walk: Boolean,
    val nextAt: Long?,
)

internal enum class RingKind { ALARM, REMINDER }

internal data class RingSession(
    val itemId: String,
    val title: String,
    val kind: RingKind,
    val dueAt: Long,
    val walk: Boolean = false,
    val mathSolved: Boolean = false,
    val steps: Int = 0,
) {
    val id: String get() = "$kind:$itemId:$dueAt"
    val firstNumber: Int get() = 2 + Math.floorMod(id.hashCode(), 8)
    val secondNumber: Int get() = 2 + Math.floorMod((id + "math").hashCode(), 8)
}

internal data class BellState(
    val reminders: List<Reminder> = emptyList(),
    val alarms: List<WakeAlarm> = emptyList(),
    val rings: List<RingSession> = emptyList(),
    val ringtone: String? = null,
) {
    val ringing: RingSession? get() = rings.firstOrNull()

    fun nextNotificationAt(): Long? = reminders.mapNotNull {
        when {
            it.doneAt != null -> null
            it.pending -> it.nudgeAt
            else -> it.dueAt
        }
    }.minOrNull()

    fun nextRingAt(): Long? =
        (alarms.mapNotNull { it.nextAt } + reminders.mapNotNull { it.ringAt }).minOrNull()
}

internal data class DueEvents(val state: BellState, val notify: Set<String>)

internal object BellRules {
    fun advance(state: BellState, now: Long): DueEvents {
        val toNotify = mutableSetOf<String>()
        val newlyRinging = mutableListOf<RingSession>()
        val reminders = state.reminders.map { reminder ->
            if (reminder.doneAt != null || (!reminder.pending && reminder.dueAt > now)) {
                return@map reminder
            }
            val nudge = reminder.nudgeAt?.let { it <= now } == true
            val ringAt = reminder.ringAt?.takeIf { it <= now }
            if (!reminder.pending || nudge) toNotify.add(reminder.id)
            if (ringAt != null) {
                newlyRinging.add(RingSession(reminder.id, reminder.title, RingKind.REMINDER, ringAt))
            }
            reminder.copy(
                pending = true,
                nudgeAt = if (nudge || ringAt != null) null else reminder.nudgeAt,
                ringAt = if (ringAt != null) null else reminder.ringAt,
            )
        }
        val alarms = state.alarms.map { alarm ->
            val scheduled = alarm.nextAt?.takeIf { it <= now } ?: return@map alarm
            newlyRinging.add(RingSession(alarm.id, alarm.title, RingKind.ALARM, scheduled, alarm.walk))
            alarm.copy(nextAt = null)
        }
        val rings = state.rings + newlyRinging.sortedWith(
            compareBy<RingSession> { it.dueAt }.thenBy { it.kind }
        )
        return DueEvents(state.copy(reminders = reminders, alarms = alarms, rings = rings), toNotify)
    }

    fun remindAgain(state: BellState, id: String, now: Long): BellState = state.copy(
        reminders = state.reminders.map {
            if (it.id == id && it.pending && it.doneAt == null && it.onUnlock &&
                state.rings.none { ring -> ring.itemId == id }
            ) {
                it.copy(nudgeAt = now + UNLOCK_BACKUP_MINUTES * MINUTE)
            } else {
                it
            }
        },
    )

    fun onUnlock(state: BellState): DueEvents {
        val due = state.reminders.filter { it.pending && it.doneAt == null && it.nudgeAt != null }
            .mapTo(mutableSetOf()) { it.id }
        return DueEvents(
            state.copy(reminders = state.reminders.map { if (it.id in due) it.copy(nudgeAt = null) else it }),
            due,
        )
    }

    fun complete(state: BellState, itemId: String, now: Long): BellState = state.copy(
        reminders = state.reminders.map {
            if (it.id == itemId && it.doneAt == null) {
                it.copy(pending = false, doneAt = now, nudgeAt = null, ringAt = null)
            } else {
                it
            }
        },
        rings = state.rings.filterNot { it.kind == RingKind.REMINDER && it.itemId == itemId },
    )

    fun snooze(state: BellState, sessionId: String, now: Long): BellState {
        val ring = state.ringing?.takeIf { it.id == sessionId && it.kind == RingKind.REMINDER }
            ?: return state
        return state.copy(
            reminders = state.reminders.map {
                if (it.id == ring.itemId) {
                    it.copy(nudgeAt = null, ringAt = now + SNOOZE_MINUTES * MINUTE)
                } else {
                    it
                }
            },
            rings = state.rings.drop(1),
        )
    }

    fun solveMath(state: BellState, sessionId: String, answer: Int?, now: Long, zone: ZoneId): BellState {
        val ring = state.ringing?.takeIf { it.id == sessionId && it.kind == RingKind.ALARM }
            ?: return state
        if (ring.mathSolved || answer != ring.firstNumber + ring.secondNumber) return state
        if (ring.walk) return state.copy(rings = listOf(ring.copy(mathSolved = true)) + state.rings.drop(1))
        return stopAlarm(state, ring, now, zone)
    }

    fun step(state: BellState, sessionId: String, now: Long, zone: ZoneId): BellState {
        val ring = state.ringing?.takeIf {
            it.id == sessionId && it.kind == RingKind.ALARM && it.walk && it.mathSolved
        } ?: return state
        val progress = ring.steps + 1
        if (progress >= STEP_GOAL) return stopAlarm(state, ring, now, zone)
        return state.copy(rings = listOf(ring.copy(steps = progress)) + state.rings.drop(1))
    }

    fun useMathOnly(state: BellState, sessionId: String): BellState {
        val ring = state.ringing?.takeIf { it.id == sessionId && it.walk } ?: return state
        return state.copy(
            rings = listOf(ring.copy(walk = false, mathSolved = false, steps = 0)) + state.rings.drop(1),
        )
    }

    private fun stopAlarm(state: BellState, ring: RingSession, now: Long, zone: ZoneId): BellState =
        state.copy(
            alarms = state.alarms.map { alarm ->
                if (alarm.id == ring.itemId && alarm.repeat != Repeat.ONCE) {
                    alarm.copy(nextAt = alarm.repeat.nextAt(alarm.hour, alarm.minute, now, zone))
                } else {
                    alarm
                }
            },
            rings = state.rings.drop(1),
        )
}
