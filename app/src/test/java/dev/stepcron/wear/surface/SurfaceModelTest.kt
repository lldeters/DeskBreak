package dev.stepcron.wear.surface

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
        val model = SurfaceModel.create(enabled, state, at("10:00", "2026-11-14"), zone) as SurfaceModel.Paused
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
    fun `a reset click is handled once even if the host repeats it`() {
        val first = DeskBreakTileService.RESET_CLICK_PREFIX + "1000"
        val second = DeskBreakTileService.RESET_CLICK_PREFIX + "2000"
        assertTrue(DeskBreakTileService.consumeResetClick(first))
        assertFalse(DeskBreakTileService.consumeResetClick(first))
        assertTrue(DeskBreakTileService.consumeResetClick(second))
        assertFalse(DeskBreakTileService.consumeResetClick(""))
        assertFalse(DeskBreakTileService.consumeResetClick("open"))
    }
}
