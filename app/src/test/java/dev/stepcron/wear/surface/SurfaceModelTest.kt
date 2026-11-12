package dev.stepcron.wear.surface

import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.shared.license.Unlock
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.ReminderState
import dev.stepcron.wear.domain.AlarmKind
import dev.stepcron.wear.domain.WeeklySchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SurfaceModelTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val enabled = ReminderSettings(enabled = true, schedule = WeeklySchedule.DEFAULT)

    /** 2026-11-09 ist ein Montag, 2026-11-14 ein Samstag. */
    private fun at(time: String, date: String = "2026-11-09"): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `disabled reminders are off`() {
        assertEquals(SurfaceModel.Off, SurfaceModel.create(ReminderSettings(enabled = false), ReminderState(), at("10:00"), zone))
    }

    @Test
    fun `no active day`() {
        val settings = enabled.copy(schedule = WeeklySchedule(WeeklySchedule.DEFAULT.days.map { it.copy(enabled = false) }))
        assertEquals(SurfaceModel.NoActiveDays, SurfaceModel.create(settings, ReminderState(), at("10:00"), zone))
    }

    @Test
    fun `scheduled reminder becomes a countdown with progress`() {
        val state = ReminderState(nextAlarmAt = at("10:45"), nextAlarmKind = AlarmKind.REMINDER, cycleStartAt = at("10:00"))
        val model = SurfaceModel.create(enabled, state, at("10:15"), zone) as SurfaceModel.Countdown
        assertEquals(at("10:45"), model.reminderAtMillis)
        assertEquals(at("10:00"), model.cycleStartMillis)
        assertEquals(1f / 3f, model.progressAt(at("10:15")), 0.001f)
        assertEquals(30L, model.remainingMinutesAt(at("10:15")))
        assertEquals(1L, model.remainingMinutesAt(at("10:44:30")))
        assertEquals(0L, model.remainingMinutesAt(at("10:46")))
        assertFalse(model.reminderPending)
    }

    @Test
    fun `implausible cycle start counts as just started`() {
        val state = ReminderState(nextAlarmAt = at("10:45"), nextAlarmKind = AlarmKind.REMINDER, cycleStartAt = 0L)
        val model = SurfaceModel.create(enabled, state, at("10:15"), zone) as SurfaceModel.Countdown
        assertEquals(at("10:15"), model.cycleStartMillis)
        assertEquals(0f, model.progressAt(at("10:15")), 0f)
    }

    @Test
    fun `free day is paused until the next work day`() {
        val state = ReminderState(nextAlarmAt = at("08:00", "2026-11-16"), nextAlarmKind = AlarmKind.CYCLE_START)
        val model = SurfaceModel.create(enabled, state, at("10:00", "2026-11-14"), zone) as SurfaceModel.OffHours
        assertTrue(model.freeToday)
        assertEquals(at("08:00", "2026-11-16"), model.resumeAtMillis)
        assertEquals(2, model.daysUntilResume)
    }

    @Test
    fun `pending reminder is reported`() {
        val state = ReminderState(
            nextAlarmAt = at("11:30"),
            nextAlarmKind = AlarmKind.REMINDER,
            cycleStartAt = at("10:45"),
            pendingReminderAt = at("10:45"),
        )
        assertTrue(SurfaceModel.create(enabled, state, at("10:50"), zone).reminderPending)
    }

    @Test
    fun `day done after the last reminder of the day`() {
        val state = ReminderState(nextAlarmAt = at("17:00"), nextAlarmKind = AlarmKind.WINDOW_END, cycleStartAt = at("16:30"))
        assertEquals(SurfaceModel.DayDone(at("17:00"), reminderPending = false), SurfaceModel.create(enabled, state, at("16:40"), zone))
    }

    @Test
    fun `a tile click is handled once even if the host repeats it`() {
        val reset = DeskBreakTileService.RESET_CLICK_PREFIX
        val resume = DeskBreakTileService.RESUME_CLICK_PREFIX
        assertTrue(DeskBreakTileService.consumeClick(reset + "1000", reset))
        assertFalse(DeskBreakTileService.consumeClick(reset + "1000", reset))
        assertTrue(DeskBreakTileService.consumeClick(reset + "2000", reset))
        // Eine andere Aktion zählt nicht als „Jetzt bewegt", wird aber selbst genau einmal behandelt.
        assertFalse(DeskBreakTileService.consumeClick(resume + "3000", reset))
        assertTrue(DeskBreakTileService.consumeClick(resume + "3000", resume))
        assertFalse(DeskBreakTileService.consumeClick(resume + "3000", resume))
        assertFalse(DeskBreakTileService.consumeClick("", reset))
        assertFalse(DeskBreakTileService.consumeClick("open", reset))
    }

    @Test
    fun `a user pause wins over the off hours and names the first active moment after it`() {
        // Pause bis Freitag 18:00 → weiter am Montag um 08:00.
        val paused = enabled.copy(pausedUntil = at("18:00", "2026-11-13"))
        val model = SurfaceModel.create(paused, ReminderState(), at("10:00"), zone) as SurfaceModel.PausedByUser
        assertEquals(at("08:00", "2026-11-16"), model.resumeAtMillis)
        assertEquals(7, model.daysUntilResume)
        // Eine abgelaufene Pause spielt keine Rolle mehr.
        val over = enabled.copy(pausedUntil = at("09:00"))
        assertFalse(SurfaceModel.create(over, ReminderState(), at("10:00"), zone) is SurfaceModel.PausedByUser)
    }

    @Test
    fun `after the trial the surfaces are locked instead of counting down`() {
        val state = ReminderState(nextAlarmAt = at("10:45"), nextAlarmKind = AlarmKind.REMINDER, cycleStartAt = at("10:00"))
        val expired = LicenseStatus.Expired(endedAtMillis = at("09:00"))

        assertEquals(SurfaceModel.Locked, SurfaceModel.create(enabled, state, at("10:15"), zone, expired))
        // Auch eine noch offene Erinnerung oder ein freier Tag ändert daran nichts.
        val pending = state.copy(pendingReminderAt = at("10:00"))
        val locked = SurfaceModel.create(enabled, pending, at("10:15"), zone, expired)
        assertEquals(SurfaceModel.Locked, locked)
        assertFalse(locked.reminderPending)
        assertEquals(SurfaceModel.Locked, SurfaceModel.create(enabled, ReminderState(), at("10:00", "2026-11-14"), zone, expired))
    }

    @Test
    fun `switched off reminders stay off after the trial`() {
        val expired = LicenseStatus.Expired(endedAtMillis = at("09:00"))
        assertEquals(SurfaceModel.Off, SurfaceModel.create(ReminderSettings(enabled = false), ReminderState(), at("10:00"), zone, expired))
    }

    @Test
    fun `during the trial and when unlocked the surfaces count down as usual`() {
        val state = ReminderState(nextAlarmAt = at("10:45"), nextAlarmKind = AlarmKind.REMINDER, cycleStartAt = at("10:00"))
        listOf(
            null,
            LicenseStatus.Trial(endsAtMillis = at("12:00", "2026-11-11"), daysLeft = 3),
            LicenseStatus.Unlocked(Unlock.LIFETIME),
            LicenseStatus.Unlocked(Unlock.SUBSCRIPTION),
        ).forEach { license ->
            assertTrue("$license", SurfaceModel.create(enabled, state, at("10:15"), zone, license) is SurfaceModel.Countdown)
        }
    }
}
