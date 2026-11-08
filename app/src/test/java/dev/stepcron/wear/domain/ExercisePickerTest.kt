package dev.stepcron.wear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ExercisePickerTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val count = 12

    private fun at(time: String): Long = LocalDateTime.parse("2026-11-09T$time").atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `index is stable and within range`() {
        val reminder = at("10:45")
        val index = ExercisePicker.indexFor(reminder, count)
        assertTrue(index in 0 until count)
        assertEquals(index, ExercisePicker.indexFor(reminder + 30_000L, count)) // gleiche Minute
        assertTrue(ExercisePicker.indexFor(-1L, count) in 0 until count)
    }

    @Test
    fun `reminders at a fixed interval get varied exercises`() {
        // Ein Arbeitstag mit festem Intervall: 12 Erinnerungen sollen viele verschiedene Übungen zeigen
        // (ein schlichtes minute % count träfe bei 45 min nur wenige).
        for (intervalMinutes in listOf(20, 30, 45, 60)) {
            val day = (0 until 12).map { ExercisePicker.indexFor(at("08:00") + it * intervalMinutes * 60_000L, count) }
            assertTrue("Intervall $intervalMinutes: nur ${day.toSet().size} verschiedene: $day", day.toSet().size >= 7)
        }
    }
}
