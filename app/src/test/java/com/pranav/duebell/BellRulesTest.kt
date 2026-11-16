package com.pranav.duebell

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BellRulesTest {
    private val at = Instant.parse("2026-11-16T01:30:00Z").toEpochMilli()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val reminder = Reminder("reminder", "Send a report", at, onUnlock = true, failsafeMinutes = 30)

    @Test
    fun reminderBecomesPendingOnce() {
        val state = BellState(reminders = listOf(reminder))
        assertTrue(BellRules.advance(state, at - 1).notify.isEmpty())
        val due = BellRules.advance(state, at)
        assertEquals(setOf(reminder.id), due.notify)
        assertTrue(due.state.reminders.single().pending)
        assertTrue(BellRules.advance(due.state, at + 1).notify.isEmpty())
    }

    @Test
    fun unlockConsumesOneFollowUpAndCancelsItsTimedBackup() {
        val due = BellRules.advance(BellState(reminders = listOf(reminder)), at).state
        val armed = BellRules.remindAgain(due, reminder.id, at)
        assertEquals(at + 15 * MINUTE, armed.nextNotificationAt())
        val unlock = BellRules.onUnlock(armed)
        assertEquals(setOf(reminder.id), unlock.notify)
        assertNull(unlock.state.nextNotificationAt())
        assertTrue(BellRules.onUnlock(unlock.state).notify.isEmpty())
    }

    @Test
    fun timedBackupFiresWhenThereIsNoUnlock() {
        val due = BellRules.advance(BellState(reminders = listOf(reminder)), at).state
        val armed = BellRules.remindAgain(due, reminder.id, at)
        val backup = BellRules.advance(armed, at + 15 * MINUTE)
        assertEquals(setOf(reminder.id), backup.notify)
        assertNull(backup.state.nextNotificationAt())
        assertTrue(BellRules.onUnlock(backup.state).notify.isEmpty())
    }

    @Test
    fun doneCancelsTheRingingSessionAndFutureNudges() {
        val ringing = BellRules.advance(BellState(reminders = listOf(reminder)), at + 30 * MINUTE).state
        assertEquals(RingKind.REMINDER, ringing.ringing?.kind)
        val done = BellRules.complete(ringing, reminder.id, at + 31 * MINUTE)
        assertNull(done.ringing)
        assertNull(done.nextRingAt())
        assertNull(done.nextNotificationAt())
        assertFalse(done.reminders.single().pending)
        assertTrue(BellRules.advance(done, at + 120 * MINUTE).notify.isEmpty())
    }

    @Test
    fun snoozeRingsAgainAnHourAfterTheActionAndIgnoresOldActions() {
        val ringAt = at + 30 * MINUTE
        val ringing = BellRules.advance(BellState(reminders = listOf(reminder)), ringAt).state
        val session = requireNotNull(ringing.ringing)
        val snoozed = BellRules.snooze(ringing, session.id, ringAt + MINUTE)
        val next = ringAt + 61 * MINUTE
        assertNull(snoozed.ringing)
        assertEquals(next, snoozed.nextRingAt())
        assertTrue(snoozed.reminders.single().pending)
        assertNull(BellRules.advance(snoozed, next - 1).state.ringing)
        val recurring = BellRules.advance(snoozed, next).state
        assertEquals(next, recurring.ringing?.dueAt)
        assertEquals(recurring, BellRules.snooze(recurring, session.id, next))
    }

    @Test
    fun mathOnlyAlarmStopsOnTheCorrectAnswer() {
        val state = ringingAlarm(walk = false)
        val session = requireNotNull(state.ringing)
        assertEquals(state, BellRules.solveMath(state, session.id, 0, at, zone))
        val finished = BellRules.solveMath(state, session.id, session.firstNumber + session.secondNumber, at, zone)
        assertNull(finished.ringing)
        assertNull(finished.nextRingAt())
    }

    @Test
    fun walkingAlarmRequiresTheMathBeforeTenStepsAndRepeatsTomorrow() {
        val state = ringingAlarm(walk = true, repeat = Repeat.DAILY)
        val session = requireNotNull(state.ringing)
        assertEquals(state, BellRules.step(state, session.id, at, zone))
        var progress = BellRules.solveMath(state, session.id, session.firstNumber + session.secondNumber, at, zone)
        assertTrue(requireNotNull(progress.ringing).mathSolved)
        for (count in 1..9) {
            progress = BellRules.step(progress, session.id, at, zone)
            assertEquals(count, progress.ringing?.steps)
        }
        val done = BellRules.step(progress, session.id, at, zone)
        assertNull(done.ringing)
        assertEquals(at + 24 * 60 * MINUTE, done.nextRingAt())
    }

    @Test
    fun overlappingAlertsAreQueuedWithoutResettingTheCurrentChallenge() {
        val first = ringingAlarm(walk = true)
        val session = requireNotNull(first.ringing)
        val state = BellRules.solveMath(first, session.id, session.firstNumber + session.secondNumber, at, zone)
        val withReminder = state.copy(reminders = listOf(reminder))
        val second = BellRules.advance(withReminder, at + 30 * MINUTE).state
        assertEquals(2, second.rings.size)
        assertEquals(session.id, second.ringing?.id)
        assertTrue(requireNotNull(second.ringing).mathSolved)
    }

    @Test
    fun weekdayScheduleSkipsSaturdayAndSunday() {
        val friday = Instant.parse("2026-11-20T03:00:00Z").toEpochMilli()
        assertEquals(at + 7 * 24 * 60 * MINUTE, Repeat.WEEKDAYS.nextAt(7, 0, friday, zone))
    }

    private fun ringingAlarm(walk: Boolean, repeat: Repeat = Repeat.ONCE): BellState =
        BellRules.advance(
            BellState(alarms = listOf(WakeAlarm("alarm", "Wake up", 7, 0, repeat, walk, at))),
            at,
        ).state
}
