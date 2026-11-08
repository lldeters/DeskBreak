package dev.stepcron.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PauseOptionsTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(dateTime: String): Long = LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `quick pauses count from now`() {
        assertEquals(at("2026-11-09T10:30"), PauseOptions.forMinutes(at("2026-11-09T10:00"), 30))
        assertEquals(at("2026-11-09T12:00"), PauseOptions.forMinutes(at("2026-11-09T10:00"), 120))
    }

    @Test
    fun `rest of the day ends at midnight`() {
        assertEquals(at("2026-11-10T00:00"), PauseOptions.restOfDay(at("2026-11-09T10:00"), zone))
        assertEquals(at("2026-11-10T00:00"), PauseOptions.restOfDay(at("2026-11-09T23:59"), zone))
    }

    @Test
    fun `a vacation lasts until the end of its last day, also across daylight saving time`() {
        assertEquals(at("2026-11-15T00:00"), PauseOptions.vacationUntil(LocalDate.of(2026, 11, 14), zone))
        // Zeitumstellung am 29.03.2026: Der Tag hat nur 23 Stunden.
        assertEquals(at("2026-03-30T00:00"), PauseOptions.vacationUntil(LocalDate.of(2026, 3, 29), zone))
    }

    @Test
    fun `vacation days start tomorrow`() {
        val days = PauseOptions.vacationDays(LocalDate.of(2026, 11, 9))
        assertEquals(LocalDate.of(2026, 11, 10), days.first())
        assertEquals(PauseOptions.MAX_VACATION_DAYS, days.size)
    }

    @Test
    fun `the next active moment is now inside a window, otherwise the next start`() {
        val schedule = WeeklySchedule.DEFAULT
        assertEquals(at("2026-11-09T10:00"), schedule.nextActiveAt(at("2026-11-09T10:00"), zone))
        assertEquals(at("2026-11-09T08:00"), schedule.nextActiveAt(at("2026-11-09T08:00"), zone))
        assertEquals(at("2026-11-10T08:00"), schedule.nextActiveAt(at("2026-11-09T17:00"), zone))
        assertEquals(at("2026-11-16T08:00"), schedule.nextActiveAt(at("2026-11-14T00:00"), zone))
    }
}
