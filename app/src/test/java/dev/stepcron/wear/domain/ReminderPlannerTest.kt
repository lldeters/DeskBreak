package dev.stepcron.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderPlannerTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val window = ActiveWindow(8, 17)
    private val interval = 45 * 60_000L

    private fun at(time: String, date: String = "2026-11-09"): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `reminder inside the window`() {
        val plan = ReminderPlanner.planInWindow(at("10:45"), at("10:00"), window, zone)
        assertEquals(PlannedAlarm(at("10:45"), AlarmKind.REMINDER), plan)
    }

    @Test
    fun `reminder after the window end becomes window end`() {
        val plan = ReminderPlanner.planInWindow(at("17:15"), at("16:30"), window, zone)
        assertEquals(PlannedAlarm(at("17:00"), AlarmKind.WINDOW_END), plan)
    }

    @Test
    fun `reminder exactly at the window end becomes window end`() {
        val plan = ReminderPlanner.planInWindow(at("17:00"), at("16:15"), window, zone)
        assertEquals(PlannedAlarm(at("17:00"), AlarmKind.WINDOW_END), plan)
    }

    @Test
    fun `outside the window the next start is planned`() {
        assertEquals(
            PlannedAlarm(at("08:00", "2026-11-10"), AlarmKind.CYCLE_START),
            ReminderPlanner.planOutsideWindow(at("18:00"), window, zone),
        )
        assertEquals(
            PlannedAlarm(at("08:00"), AlarmKind.CYCLE_START),
            ReminderPlanner.planOutsideWindow(at("06:00"), window, zone),
        )
    }

    @Test
    fun `plan next continues the running cycle`() {
        val plan = ReminderPlanner.planNext(at("10:10"), at("10:00"), 0L, interval, window, zone)
        assertEquals(PlannedAlarm(at("10:45"), AlarmKind.REMINDER), plan)
    }

    @Test
    fun `overdue reminder is planned with minimum lead`() {
        val now = at("11:00")
        val plan = ReminderPlanner.planNext(now, at("10:00"), 0L, interval, window, zone)
        assertEquals(PlannedAlarm(now + ReminderPlanner.MIN_LEAD_MILLIS, AlarmKind.REMINDER), plan)
    }

    @Test
    fun `upcoming reminder keeps its exact time when replanned shortly before`() {
        // Regression: Ein TIME_SET 30 s vor der Erinnerung darf sie nicht um eine Minute verschieben.
        val plan = ReminderPlanner.planNext(at("10:44:30"), at("10:00"), 0L, interval, window, zone)
        assertEquals(PlannedAlarm(at("10:45"), AlarmKind.REMINDER), plan)
    }

    @Test
    fun `active snooze wins over the cycle`() {
        val plan = ReminderPlanner.planNext(at("10:12"), at("09:15"), at("10:20"), interval, window, zone)
        assertEquals(PlannedAlarm(at("10:20"), AlarmKind.REMINDER), plan)
    }

    @Test
    fun `plan next outside the window plans cycle start`() {
        val plan = ReminderPlanner.planNext(at("20:00"), at("16:30"), 0L, interval, window, zone)
        assertEquals(PlannedAlarm(at("08:00", "2026-11-10"), AlarmKind.CYCLE_START), plan)
    }
}
