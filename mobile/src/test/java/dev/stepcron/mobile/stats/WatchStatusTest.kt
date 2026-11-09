package dev.stepcron.mobile.stats

import dev.stepcron.mobile.TestData
import dev.stepcron.mobile.TestData.at
import dev.stepcron.shared.sync.SyncDay
import dev.stepcron.shared.sync.SyncSettings
import dev.stepcron.shared.sync.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchStatusTest {

    // Montag, 9.11.2026; Zeitplan Mo–Fr 08–17 Uhr.
    private val monday10 = at("2026-11-09", 10)

    private fun status(
        now: Long,
        settings: SyncSettings = TestData.settings(),
        watch: SyncStatus = TestData.status(),
        generatedAt: Long = now,
    ): WatchStatusInfo = WatchStatusCalculator.compute(TestData.snapshot(generatedAt, settings = settings, status = watch), settings, now)

    @Test
    fun remindersOffHidesAnOpenReminder() {
        val info = status(monday10, TestData.settings(enabled = false), TestData.status(pendingReminderAtMillis = monday10 - 60_000))
        assertEquals(WatchStatus.RemindersOff, info.status)
        assertNull(info.pendingReminderAtMillis)
    }

    @Test
    fun noActiveDay() {
        val schedule = List(7) { SyncDay(false, 480, 1020) }
        assertEquals(WatchStatus.NoActiveDays, status(monday10, TestData.settings(schedule = schedule)).status)
    }

    @Test
    fun pauseResumesAtPauseEndInsideTheWindow() {
        val until = at("2026-11-09", 12)
        assertEquals(WatchStatus.Paused(until, until), status(monday10, TestData.settings(pausedUntilMillis = until)).status)
    }

    @Test
    fun pauseUntilEveningResumesNextMorning() {
        val until = at("2026-11-09", 18)
        val info = status(monday10, TestData.settings(pausedUntilMillis = until))
        assertEquals(WatchStatus.Paused(until, at("2026-11-10", 8)), info.status)
    }

    @Test
    fun expiredPauseIsIgnored() {
        val reminder = at("2026-11-09", 10, 45)
        val info = status(
            monday10,
            TestData.settings(pausedUntilMillis = monday10 - 1),
            TestData.status(nextAlarmAtMillis = reminder, nextAlarmKind = "REMINDER"),
        )
        assertEquals(WatchStatus.NextReminder(reminder, snoozed = false), info.status)
    }

    @Test
    fun beforeTheWindowIsOffHoursUntilItStarts() {
        assertEquals(WatchStatus.OffHours(at("2026-11-09", 8), freeToday = false), status(at("2026-11-09", 7)).status)
    }

    @Test
    fun fridayEveningAndWeekendResumeOnMonday() {
        assertEquals(WatchStatus.OffHours(at("2026-11-16", 8), freeToday = false), status(at("2026-11-13", 18)).status)
        assertEquals(WatchStatus.OffHours(at("2026-11-16", 8), freeToday = true), status(at("2026-11-14", 10)).status)
    }

    @Test
    fun nextReminderAndSnooze() {
        val at = at("2026-11-09", 10, 45)
        assertEquals(
            WatchStatus.NextReminder(at, snoozed = false),
            status(monday10, watch = TestData.status(nextAlarmAtMillis = at, nextAlarmKind = "REMINDER")).status,
        )
        assertEquals(
            WatchStatus.NextReminder(at, snoozed = true),
            status(monday10, watch = TestData.status(nextAlarmAtMillis = at, nextAlarmKind = "REMINDER", snoozeUntilMillis = at)).status,
        )
    }

    @Test
    fun reminderInThePastIsUnknown() {
        val info = status(monday10, watch = TestData.status(nextAlarmAtMillis = monday10 - 60_000, nextAlarmKind = "REMINDER"))
        assertEquals(WatchStatus.Unknown, info.status)
    }

    @Test
    fun windowEndMeansDoneForTodayUntilTheScheduledEnd() {
        val info = status(at("2026-11-09", 16, 40), watch = TestData.status(nextAlarmAtMillis = 1L, nextAlarmKind = "WINDOW_END"))
        assertEquals(WatchStatus.DayDone(at("2026-11-09", 17)), info.status)
    }

    @Test
    fun windowUntilMidnightContainsLateEvening() {
        val schedule = List(7) { SyncDay(true, 8 * 60, 24 * 60) }
        val reminder = at("2026-11-09", 23, 45)
        val info = status(
            at("2026-11-09", 23, 30),
            TestData.settings(schedule = schedule),
            TestData.status(nextAlarmAtMillis = reminder, nextAlarmKind = "REMINDER"),
        )
        assertEquals(WatchStatus.NextReminder(reminder, snoozed = false), info.status)
    }

    @Test
    fun pendingReminderAndOutdatedSnapshot() {
        val pending = monday10 - 5 * 60_000
        val fresh = status(monday10, watch = TestData.status(pendingReminderAtMillis = pending))
        assertEquals(pending, fresh.pendingReminderAtMillis)
        assertFalse(fresh.outdated)
        assertTrue(status(monday10, generatedAt = monday10 - 31 * 60_000).outdated)
    }
}
