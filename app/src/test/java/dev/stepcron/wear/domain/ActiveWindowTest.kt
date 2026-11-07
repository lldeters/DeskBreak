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
    private val window = ActiveWindow(8, 17)

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
    fun `next start before the window is today`() {
        assertEquals(at("2026-11-09T08:00"), window.nextStart(at("2026-11-09T06:30"), zone))
    }

    @Test
    fun `next start after the window is the following day`() {
        assertEquals(at("2026-11-10T08:00"), window.nextStart(at("2026-11-09T17:00"), zone))
        assertEquals(at("2026-11-10T08:00"), window.nextStart(at("2026-11-09T23:59"), zone))
    }

    @Test
    fun `end hour 24 means midnight of the next day`() {
        val lateWindow = ActiveWindow(6, 24)
        assertEquals(at("2026-11-10T00:00"), lateWindow.endOfDay(at("2026-11-09T23:30"), zone))
        assertTrue(lateWindow.contains(at("2026-11-09T23:59"), zone))
        assertFalse(lateWindow.contains(at("2026-11-10T00:30"), zone))
    }

    @Test
    fun `start inside daylight saving gap is shifted forward`() {
        // 29.03.2026: In Berlin springt die Uhr von 02:00 auf 03:00.
        val nightWindow = ActiveWindow(2, 10)
        assertEquals(at("2026-03-29T03:00"), nightWindow.startOn(LocalDate.parse("2026-03-29"), zone))
    }

    @Test
    fun `hours lists every active hour`() {
        assertEquals((8..16).toList(), window.hours.toList())
    }
}
