package dev.stepcron.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderPlannerTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val everyDay = WeeklySchedule.everyDay(ActiveWindow.ofHours(8, 17))

    /** Montag bis Freitag 08–17 Uhr. */
    private val workdays = WeeklySchedule.DEFAULT
    private val noDays = WeeklySchedule(workdays.days.map { it.copy(enabled = false) })
    private val interval = 45 * 60_000L
    private val grace = ReminderPlanner.WINDOW_END_GRACE_MILLIS

    /** Standarddatum 2026-11-09 ist ein Montag; 2026-11-12 Donnerstag, 2026-11-13 Freitag. */
    private fun at(time: String, date: String = "2026-11-09"): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `reminder inside the window`() {
        val plan = ReminderPlanner.planInWindow(at("10:45"), at("10:00"), everyDay, zone)
        assertEquals(PlannedAlarm(at("10:45"), AlarmKind.REMINDER), plan)
    }

    @Test
    fun `reminder after the window end becomes window end`() {
        val plan = ReminderPlanner.planInWindow(at("17:15"), at("16:30"), everyDay, zone)
        assertEquals(PlannedAlarm(at("17:00"), AlarmKind.WINDOW_END), plan)
    }

    @Test
    fun `reminder exactly at the window end becomes window end`() {
        val plan = ReminderPlanner.planInWindow(at("17:00"), at("16:15"), everyDay, zone)
        assertEquals(PlannedAlarm(at("17:00"), AlarmKind.WINDOW_END), plan)
    }

    @Test
    fun `window end respects minutes`() {
        val schedule = WeeklySchedule.everyDay(ActiveWindow(8 * 60, 16 * 60 + 30))
        val plan = ReminderPlanner.planInWindow(at("16:45"), at("16:00"), schedule, zone)
        assertEquals(PlannedAlarm(at("16:30"), AlarmKind.WINDOW_END), plan)
    }

    @Test
    fun `outside the window the next start is planned`() {
        assertEquals(
            PlannedAlarm(at("08:00", "2026-11-10"), AlarmKind.CYCLE_START),
            ReminderPlanner.planOutsideWindow(at("18:00"), everyDay, zone, pendingReminderAt = 0L),
        )
        assertEquals(
            PlannedAlarm(at("08:00"), AlarmKind.CYCLE_START),
            ReminderPlanner.planOutsideWindow(at("06:00"), everyDay, zone, pendingReminderAt = 0L),
        )
    }

    @Test
    fun `outside the window free days are skipped`() {
        assertEquals(
            PlannedAlarm(at("08:00", "2026-11-16"), AlarmKind.CYCLE_START),
            ReminderPlanner.planOutsideWindow(at("17:00", "2026-11-13"), workdays, zone, pendingReminderAt = 0L),
        )
    }

    @Test
    fun `nothing is planned without an active day`() {
        assertNull(ReminderPlanner.planOutsideWindow(at("10:00"), noDays, zone, pendingReminderAt = 0L))
        assertNull(ReminderPlanner.planNext(at("10:00"), at("09:00"), 0L, interval, noDays, zone, pendingReminderAt = 0L))
    }

    @Test
    fun `pending reminder stays answerable overnight before the next work day`() {
        // Do 16:55 erinnert, Fr 08:00 ist der nächste Fensterbeginn: bis dahin beantwortbar.
        val plan = ReminderPlanner.planOutsideWindow(
            at("17:00", "2026-11-12"), workdays, zone, pendingReminderAt = at("16:55", "2026-11-12"),
        )
        assertEquals(PlannedAlarm(at("08:00", "2026-11-13"), AlarmKind.CYCLE_START), plan)
    }

    @Test
    fun `pending reminder before free days is closed after the grace period`() {
        // Fr 16:55 erinnert, nächster Fensterbeginn erst Mo: nach 15 min als verpasst abschließen.
        val pending = at("16:55", "2026-11-13")
        val plan = ReminderPlanner.planOutsideWindow(at("17:00", "2026-11-13"), workdays, zone, pending)
        assertEquals(PlannedAlarm(pending + grace, AlarmKind.WINDOW_END), plan)
    }

    @Test
    fun `stale pending reminder on a free day is closed right away`() {
        val now = at("10:00", "2026-11-14")
        val plan = ReminderPlanner.planOutsideWindow(now, workdays, zone, pendingReminderAt = at("16:55", "2026-11-13"))
        assertEquals(PlannedAlarm(now, AlarmKind.WINDOW_END), plan)
    }

    @Test
    fun `pending reminder without any active day is closed after the grace period`() {
        val pending = at("16:55")
        assertEquals(
            PlannedAlarm(pending + grace, AlarmKind.WINDOW_END),
            ReminderPlanner.planOutsideWindow(at("17:00"), noDays, zone, pending),
        )
    }

    @Test
    fun `plan next continues the running cycle`() {
        val plan = ReminderPlanner.planNext(at("10:10"), at("10:00"), 0L, interval, everyDay, zone, pendingReminderAt = 0L)
        assertEquals(PlannedAlarm(at("10:45"), AlarmKind.REMINDER), plan)
    }

    @Test
    fun `overdue reminder is planned with minimum lead`() {
        val now = at("11:00")
        val plan = ReminderPlanner.planNext(now, at("10:00"), 0L, interval, everyDay, zone, pendingReminderAt = 0L)
        assertEquals(PlannedAlarm(now + ReminderPlanner.MIN_LEAD_MILLIS, AlarmKind.REMINDER), plan)
    }

    @Test
    fun `upcoming reminder keeps its exact time when replanned shortly before`() {
        // Regression: Ein TIME_SET 30 s vor der Erinnerung darf sie nicht um eine Minute verschieben.
        val plan = ReminderPlanner.planNext(
            at("10:44:30"), at("10:00"), 0L, interval, everyDay, zone, pendingReminderAt = 0L,
        )
        assertEquals(PlannedAlarm(at("10:45"), AlarmKind.REMINDER), plan)
    }

    @Test
    fun `active snooze wins over the cycle`() {
        val plan = ReminderPlanner.planNext(
            at("10:12"), at("09:15"), at("10:20"), interval, everyDay, zone, pendingReminderAt = 0L,
        )
        assertEquals(PlannedAlarm(at("10:20"), AlarmKind.REMINDER), plan)
    }

    @Test
    fun `plan next outside the window plans cycle start`() {
        val plan = ReminderPlanner.planNext(
            at("20:00"), at("16:30"), 0L, interval, everyDay, zone, pendingReminderAt = 0L,
        )
        assertEquals(PlannedAlarm(at("08:00", "2026-11-10"), AlarmKind.CYCLE_START), plan)
    }

    @Test
    fun `plan next on a free day with a pending reminder closes it`() {
        val now = at("09:00", "2026-11-14")
        val plan = ReminderPlanner.planNext(
            now, at("16:10", "2026-11-13"), 0L, interval, workdays, zone, pendingReminderAt = at("16:55", "2026-11-13"),
        )
        assertEquals(PlannedAlarm(now, AlarmKind.WINDOW_END), plan)
    }

    @Test
    fun `schedule change classifies leaving and entering today's window`() {
        val now = at("10:00")
        val mondayOff = workdays.with(DayOfWeek.MONDAY) { it.copy(enabled = false) }
        val mondayMorningOnly = workdays.with(DayOfWeek.MONDAY) { it.copy(window = ActiveWindow.ofHours(6, 9)) }

        assertEquals(ScheduleChange.TODAY_OFF, ReminderPlanner.scheduleChange(workdays, mondayOff, now, zone))
        assertEquals(ScheduleChange.WINDOW_LEFT, ReminderPlanner.scheduleChange(workdays, mondayMorningOnly, now, zone))
        assertEquals(ScheduleChange.WINDOW_ENTERED, ReminderPlanner.scheduleChange(mondayOff, workdays, now, zone))
        assertEquals(ScheduleChange.NONE, ReminderPlanner.scheduleChange(workdays, workdays.withAllWindows(ActiveWindow.ofHours(7, 18)), now, zone))
        // Heute frei zu stellen zählt auch vor und nach dem Fenster; andere Tage ändern am heutigen nichts.
        assertEquals(ScheduleChange.TODAY_OFF, ReminderPlanner.scheduleChange(workdays, mondayOff, at("18:00"), zone))
        assertEquals(ScheduleChange.TODAY_OFF, ReminderPlanner.scheduleChange(workdays, mondayOff, at("07:00"), zone))
        val tuesdayOff = workdays.with(DayOfWeek.TUESDAY) { it.copy(enabled = false) }
        assertEquals(ScheduleChange.NONE, ReminderPlanner.scheduleChange(workdays, tuesdayOff, at("18:00"), zone))
        assertEquals(ScheduleChange.NONE, ReminderPlanner.scheduleChange(workdays, tuesdayOff, now, zone))
    }
}
