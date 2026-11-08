package dev.stepcron.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WeeklyScheduleTest {

    private val zone = ZoneId.of("Europe/Berlin")

    /** Montag bis Freitag 08–17 Uhr, Wochenende frei. 2026-11-09 ist ein Montag. */
    private val workdays = WeeklySchedule.DEFAULT

    private fun at(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    private fun only(day: DayOfWeek, window: ActiveWindow = WeeklySchedule.DEFAULT_WINDOW) =
        WeeklySchedule(DayOfWeek.entries.map { DaySchedule(enabled = it == day, window = window) })

    @Test
    fun `default is monday to friday eight to five`() {
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            workdays.activeDays,
        )
        assertEquals(ActiveWindow.ofHours(8, 17), workdays[DayOfWeek.SATURDAY].window)
    }

    @Test
    fun `contains is false on free days`() {
        assertTrue(workdays.contains(at("2026-11-13T10:00"), zone)) // Freitag
        assertFalse(workdays.contains(at("2026-11-14T10:00"), zone)) // Samstag
        assertNull(workdays.windowAt(at("2026-11-15T10:00"), zone)) // Sonntag
        assertNull(workdays.endOfDay(at("2026-11-14T10:00"), zone))
    }

    @Test
    fun `next start before today's window is today`() {
        assertEquals(at("2026-11-09T08:00"), workdays.nextStart(at("2026-11-09T06:30"), zone))
    }

    @Test
    fun `next start after the window is the following active day`() {
        assertEquals(at("2026-11-10T08:00"), workdays.nextStart(at("2026-11-09T17:00"), zone))
        assertEquals(at("2026-11-10T08:00"), workdays.nextStart(at("2026-11-09T23:59"), zone))
    }

    @Test
    fun `next start skips free days`() {
        assertEquals(at("2026-11-16T08:00"), workdays.nextStart(at("2026-11-13T17:00"), zone)) // Fr → Mo
        assertEquals(at("2026-11-16T08:00"), workdays.nextStart(at("2026-11-14T09:00"), zone)) // Sa → Mo
    }

    @Test
    fun `next start uses each day's own window`() {
        val schedule = workdays.with(DayOfWeek.TUESDAY) { it.copy(window = ActiveWindow(9 * 60 + 30, 12 * 60)) }
        assertEquals(at("2026-11-10T09:30"), schedule.nextStart(at("2026-11-09T18:00"), zone))
    }

    @Test
    fun `only today active and its window over means next week`() {
        val mondays = only(DayOfWeek.MONDAY)
        assertEquals(at("2026-11-16T08:00"), mondays.nextStart(at("2026-11-09T18:00"), zone))
        assertEquals(at("2026-11-09T08:00"), mondays.nextStart(at("2026-11-09T07:00"), zone))
    }

    @Test
    fun `no active day has no next start`() {
        val none = WeeklySchedule(workdays.days.map { it.copy(enabled = false) })
        assertFalse(none.hasActiveDay)
        assertNull(none.nextStart(at("2026-11-09T06:00"), zone))
        assertFalse(none.contains(at("2026-11-09T10:00"), zone))
    }

    @Test
    fun `next start across the daylight saving change keeps local time`() {
        // Sonntag 25.10.2026 endet die Sommerzeit: Fr 08:00 = 06:00Z, Mo 08:00 = 07:00Z.
        val nextStart = workdays.nextStart(at("2026-10-23T17:00"), zone)
        assertEquals(Instant.parse("2026-10-26T07:00:00Z").toEpochMilli(), nextStart)
    }

    @Test
    fun `end at midnight belongs to the same day`() {
        val lateSaturday = only(DayOfWeek.SATURDAY, ActiveWindow.ofHours(18, 24))
        assertTrue(lateSaturday.contains(at("2026-11-14T23:59"), zone))
        assertFalse(lateSaturday.contains(at("2026-11-15T00:00"), zone))
        assertEquals(at("2026-11-15T00:00"), lateSaturday.endOfDay(at("2026-11-14T20:00"), zone))
    }

    @Test
    fun `sunday is the last entry`() {
        val schedule = only(DayOfWeek.SUNDAY)
        assertTrue(schedule.days[6].enabled)
        assertEquals(listOf(DayOfWeek.SUNDAY), schedule.activeDays)
        assertEquals(schedule.days[6], schedule[DayOfWeek.SUNDAY])
    }

    @Test
    fun `with and withAllWindows keep the enabled flags`() {
        val window = ActiveWindow(7 * 60, 15 * 60)
        val copied = workdays.withAllWindows(window)
        assertEquals(workdays.activeDays, copied.activeDays)
        assertTrue(copied.days.all { it.window == window })

        val changed = workdays.with(DayOfWeek.SATURDAY) { it.copy(enabled = true) }
        assertTrue(changed[DayOfWeek.SATURDAY].enabled)
        assertEquals(workdays[DayOfWeek.SATURDAY].window, changed[DayOfWeek.SATURDAY].window)
        assertEquals(workdays.days.filterIndexed { i, _ -> i != 5 }, changed.days.filterIndexed { i, _ -> i != 5 })
    }

    @Test
    fun `window on uses the calendar date`() {
        assertEquals(WeeklySchedule.DEFAULT_WINDOW, workdays.windowOn(LocalDate.parse("2026-11-09")))
        assertNull(workdays.windowOn(LocalDate.parse("2026-11-14")))
    }
}
