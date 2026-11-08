package dev.stepcron.wear.domain

import dev.stepcron.wear.data.db.MovementEvent
import dev.stepcron.wear.data.db.MovementEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class StatsCalculatorTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val window = ActiveWindow.ofHours(8, 17)

    private fun at(time: String): Long =
        LocalDateTime.parse("2026-11-09T$time").atZone(zone).toInstant().toEpochMilli()

    private fun event(time: String, type: MovementEventType) =
        MovementEvent(timestamp = at(time), eventType = type, intervalMinutes = 45)

    @Test
    fun `regularity is successful intervals divided by due intervals`() {
        assertEquals(75, StatsCalculator.regularityPercent(movements = 3, missed = 1))
        assertEquals(100, StatsCalculator.regularityPercent(movements = 4, missed = 0))
        assertEquals(0, StatsCalculator.regularityPercent(movements = 0, missed = 2))
        assertNull(StatsCalculator.regularityPercent(movements = 0, missed = 0))
    }

    @Test
    fun `snoozes do not count as due intervals`() {
        val summary = StatsCalculator.summarize(
            listOf(
                event("09:00", MovementEventType.SNOOZED),
                event("09:11", MovementEventType.COMPLETED_REMINDER),
                event("10:00", MovementEventType.AUTO_STEP_RESET),
                event("10:45", MovementEventType.MISSED),
            ),
        )
        assertEquals(2, summary.movements)
        assertEquals(1, summary.snoozes)
        assertEquals(1, summary.missed)
        assertEquals(67, summary.percent)
    }

    @Test
    fun `hour slots use movement over missed over snooze`() {
        val events = listOf(
            event("09:10", MovementEventType.SNOOZED),
            event("09:25", MovementEventType.COMPLETED_REMINDER),
            event("10:30", MovementEventType.SNOOZED),
            event("10:40", MovementEventType.MISSED),
            event("11:05", MovementEventType.SNOOZED),
            event("08:20", MovementEventType.MANUAL_RESET),
            event("08:50", MovementEventType.AUTO_STEP_RESET),
        )
        val slots = StatsCalculator.hourSlots(events, window, at("12:30"), zone).associateBy { it.hour }

        assertEquals(9, slots.size)
        assertEquals(SlotStatus.DONE, slots.getValue(8).status)
        assertEquals(2, slots.getValue(8).movementCount)
        assertEquals(SlotStatus.DONE, slots.getValue(9).status)
        assertEquals(SlotStatus.MISSED, slots.getValue(10).status)
        assertEquals(SlotStatus.SNOOZED, slots.getValue(11).status)
        assertEquals(SlotStatus.NONE, slots.getValue(12).status)
        assertTrue(slots.getValue(12).isCurrent)
        assertFalse(slots.getValue(12).isFuture)
        assertTrue(slots.getValue(13).isFuture)
    }

    @Test
    fun `hour slots cover started hours of a window with minutes`() {
        val events = listOf(event("17:05", MovementEventType.MANUAL_RESET))
        val slots = StatsCalculator.hourSlots(events, ActiveWindow(8 * 60 + 30, 17 * 60 + 15), at("12:30"), zone)
        assertEquals((8..17).toList(), slots.map { it.hour })
        assertEquals(SlotStatus.DONE, slots.last().status)
    }

    @Test
    fun `last seven days fill gaps and mark today`() {
        val today = LocalDate.parse("2026-11-09")
        val days = StatsCalculator.lastSevenDays(
            listOf(
                DailyCounts(today, movements = 6, snoozes = 1, missed = 2),
                DailyCounts(today.minusDays(2), movements = 9, snoozes = 0, missed = 0),
                DailyCounts(today.minusDays(10), movements = 1, snoozes = 0, missed = 0),
            ),
            today,
        )
        assertEquals(7, days.size)
        assertEquals(today.minusDays(6), days.first().date)
        assertEquals(today, days.last().date)
        assertTrue(days.last().isToday)
        assertEquals(75, days.last().percent)
        assertEquals(100, days[4].percent)
        assertNull(days[0].percent)
        assertEquals(1, days.count { it.isToday })
    }

    @Test
    fun `best and worst hour need enough data and a difference`() {
        val hourly = listOf(
            HourlyCounts(hour = 9, movements = 9, missed = 1), // 90 %
            HourlyCounts(hour = 10, movements = 4, missed = 0), // 100 %
            HourlyCounts(hour = 14, movements = 2, missed = 2), // 50 %
            HourlyCounts(hour = 16, movements = 1, missed = 1), // zu wenig Daten
        )
        val insight = StatsCalculator.timeOfDay(hourly)!!
        assertEquals(HourRate(hour = 10, percent = 100, due = 4), insight.best)
        assertEquals(HourRate(hour = 14, percent = 50, due = 4), insight.worst)

        assertNull(StatsCalculator.timeOfDay(hourly.take(1)))
        // Überall gleich gut: keine Aussage.
        assertNull(StatsCalculator.timeOfDay(listOf(HourlyCounts(9, 3, 0), HourlyCounts(10, 5, 0))))
    }

    @Test
    fun `ties prefer the hour with more data`() {
        val hourly = listOf(
            HourlyCounts(hour = 8, movements = 3, missed = 0),
            HourlyCounts(hour = 9, movements = 6, missed = 0),
            HourlyCounts(hour = 13, movements = 3, missed = 3),
            HourlyCounts(hour = 15, movements = 1, missed = 1 + 1),
        )
        val insight = StatsCalculator.timeOfDay(hourly)!!
        assertEquals(9, insight.best.hour)
        assertEquals(15, insight.worst.hour) // 33 % < 50 %
    }

    @Test
    fun `calendar weeks run from monday to sunday and end with this week`() {
        val today = LocalDate.of(2026, 11, 11) // Mittwoch
        val daily = listOf(
            DailyCounts(LocalDate.of(2026, 11, 9), movements = 8, snoozes = 0, missed = 1),
            DailyCounts(LocalDate.of(2026, 10, 12), movements = 2, snoozes = 1, missed = 0),
        )
        val weeks = StatsCalculator.calendarWeeks(daily, today)

        assertEquals(StatsCalculator.CALENDAR_WEEKS, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(LocalDate.of(2026, 10, 12), weeks.first().first()!!.date)
        assertEquals(LocalDate.of(2026, 10, 12), StatsCalculator.calendarStart(today))
        assertEquals(2, weeks.first().first()!!.movements)
        val thisWeek = weeks.last()
        assertEquals(8, thisWeek[0]!!.movements)
        assertTrue(thisWeek[2]!!.isToday)
        // Donnerstag bis Sonntag liegen in der Zukunft.
        assertTrue(thisWeek.drop(3).all { it == null })
    }
}
