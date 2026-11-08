package dev.stepcron.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ActiveWindowTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val window = ActiveWindow.ofHours(8, 17)

    private fun at(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `contains respects start inclusive and end exclusive`() {
        assertFalse(window.contains(at("2026-11-09T07:59"), zone))
        assertTrue(window.contains(at("2026-11-09T08:00"), zone))
        assertTrue(window.contains(at("2026-11-09T16:59"), zone))
        assertFalse(window.contains(at("2026-11-09T17:00"), zone))
    }

    @Test
    fun `minutes are respected`() {
        val quarterWindow = ActiveWindow(8 * 60 + 30, 17 * 60 + 15)
        assertFalse(quarterWindow.contains(at("2026-11-09T08:29"), zone))
        assertTrue(quarterWindow.contains(at("2026-11-09T08:30"), zone))
        assertTrue(quarterWindow.contains(at("2026-11-09T17:14"), zone))
        assertFalse(quarterWindow.contains(at("2026-11-09T17:15"), zone))
        assertEquals(at("2026-11-09T17:15"), quarterWindow.endOfDay(at("2026-11-09T12:00"), zone))
    }

    @Test
    fun `end minute 1440 means midnight of the next day`() {
        val lateWindow = ActiveWindow.ofHours(6, 24)
        assertEquals(at("2026-11-10T00:00"), lateWindow.endOfDay(at("2026-11-09T23:30"), zone))
        assertTrue(lateWindow.contains(at("2026-11-09T23:59"), zone))
        assertFalse(lateWindow.contains(at("2026-11-10T00:30"), zone))
    }

    @Test
    fun `start inside daylight saving gap is shifted forward`() {
        // 29.03.2026: In Berlin springt die Uhr von 02:00 auf 03:00.
        val nightWindow = ActiveWindow.ofHours(2, 10)
        assertEquals(at("2026-03-29T03:00"), nightWindow.startOn(LocalDate.parse("2026-03-29"), zone))
    }

    @Test
    fun `hours lists every started hour`() {
        assertEquals((8..16).toList(), window.hours.toList())
        assertEquals((8..17).toList(), ActiveWindow(8 * 60 + 30, 17 * 60 + 15).hours.toList())
        assertEquals((22..23).toList(), ActiveWindow.ofHours(22, 24).hours.toList())
    }

    @Test
    fun `sanitized clamps raw values into a valid window`() {
        assertEquals(ActiveWindow(480, 1020), ActiveWindow.sanitized(480, 1020))
        assertEquals(ActiveWindow(0, 15), ActiveWindow.sanitized(-30, 5))
        assertEquals(ActiveWindow(1425, 1440), ActiveWindow.sanitized(5000, 9000))
        // Ende vor dem Beginn → kürzestes gültiges Fenster ab dem Beginn.
        assertEquals(ActiveWindow(600, 615), ActiveWindow.sanitized(600, 300))
    }
}
