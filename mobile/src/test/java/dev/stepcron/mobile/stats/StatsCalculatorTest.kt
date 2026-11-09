package dev.stepcron.mobile.stats

import dev.stepcron.mobile.TestData
import dev.stepcron.mobile.TestData.at
import dev.stepcron.mobile.TestData.auto
import dev.stepcron.mobile.TestData.done
import dev.stepcron.mobile.TestData.event
import dev.stepcron.mobile.TestData.manual
import dev.stepcron.mobile.TestData.missed
import dev.stepcron.mobile.TestData.snoozed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class StatsCalculatorTest {

    @Test
    fun regularityIsRoundedAndNullWithoutDueIntervals() {
        assertNull(StatsCalculator.regularityPercent(0, 0))
        assertEquals(100, StatsCalculator.regularityPercent(3, 0))
        assertEquals(0, StatsCalculator.regularityPercent(0, 2))
        assertEquals(67, StatsCalculator.regularityPercent(2, 1))
        assertEquals(33, StatsCalculator.regularityPercent(1, 2))
        // 12,5 % wird wie auf der Uhr (roundToInt) aufgerundet.
        assertEquals(13, StatsCalculator.regularityPercent(1, 7))
    }

    @Test
    fun streakCountsMovementsSinceLastMissed() {
        val events = listOf(
            done(at("2026-11-02", 9)),
            missed(at("2026-11-02", 10)),
            auto(at("2026-11-02", 11)),
            snoozed(at("2026-11-02", 11, 30)),
            manual(at("2026-11-03", 9)),
            done(at("2026-11-03", 10)),
        )
        assertEquals(3, StatsCalculator.streak(events))
        assertEquals(0, StatsCalculator.streak(events + missed(at("2026-11-03", 11))))
    }

    @Test
    fun streakWithoutMissedCountsAllMovements() {
        assertEquals(2, StatsCalculator.streak(listOf(done(1_000L), auto(2_000L), snoozed(3_000L))))
        assertEquals(0, StatsCalculator.streak(emptyList()))
    }

    @Test
    fun snoozesAndUnknownTypesCountNeitherWay() {
        val today = at("2026-11-09", 9)
        val snapshot = TestData.snapshot(
            generatedAtMillis = today,
            events = listOf(done(today), snoozed(today + 1), snoozed(today + 2), event("FUTURE_TYPE", today + 3), missed(today + 4)),
        )
        val summary = StatsCalculator.dashboard(snapshot, at("2026-11-09", 12)).summary
        assertEquals(1, summary.movements)
        assertEquals(2, summary.snoozes)
        assertEquals(1, summary.missed)
        assertEquals(50, summary.percent)
    }

    @Test
    fun daysAreEvaluatedInTheSnapshotZone() {
        // 23:30 UTC am 8.11. ist in Berlin schon 00:30 am 9.11.
        val millis = LocalDate.parse("2026-11-08").atTime(23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        val berlin = StatsCalculator.dailyCounts(listOf(done(millis)), TestData.zone)
        val utc = StatsCalculator.dailyCounts(listOf(done(millis)), ZoneOffset.UTC)
        assertEquals(1, berlin[LocalDate.parse("2026-11-09")]?.movements)
        assertNull(berlin[LocalDate.parse("2026-11-08")])
        assertEquals(1, utc[LocalDate.parse("2026-11-08")]?.movements)
    }

    @Test
    fun unknownZoneFallsBackInsteadOfCrashing() {
        val snapshot = TestData.snapshot(generatedAtMillis = 0L, zoneId = "Not/AZone")
        assertNotNull(StatsCalculator.zoneOf(snapshot))
    }

    @Test
    fun timeOfDayNeedsThreeDueIntervalsAndTwoDifferentRates() {
        assertNull(StatsCalculator.timeOfDay(listOf(HourlyCounts(9, 3, 0), HourlyCounts(10, 1, 1))))
        val insight = StatsCalculator.timeOfDay(listOf(HourlyCounts(9, 3, 0), HourlyCounts(10, 1, 2), HourlyCounts(11, 2, 1)))
        assertEquals(HourRate(9, 100, 3), insight?.best)
        assertEquals(HourRate(10, 33, 3), insight?.worst)
        // Alle gleich gut: keine Aussage.
        assertNull(StatsCalculator.timeOfDay(listOf(HourlyCounts(9, 3, 0), HourlyCounts(10, 4, 0))))
    }

    @Test
    fun timeOfDayTiesPreferMoreData() {
        val insight = StatsCalculator.timeOfDay(
            listOf(HourlyCounts(8, 3, 0), HourlyCounts(9, 6, 0), HourlyCounts(14, 1, 2), HourlyCounts(15, 2, 4)),
        )!!
        assertEquals(9, insight.best.hour)
        assertEquals(15, insight.worst.hour)
    }

    @Test
    fun hourlyCountsCoverOnlyTheLastThirtyDays() {
        val now = at("2026-11-09", 12)
        val snapshot = TestData.snapshot(
            generatedAtMillis = now,
            events = listOf(
                // 30 Tage zurück (10.10.) liegt außerhalb, 29 Tage zurück (11.10.) innerhalb.
                missed(at("2026-10-10", 9)), missed(at("2026-10-10", 9, 10)), missed(at("2026-10-10", 9, 20)),
                done(at("2026-10-11", 10)), done(at("2026-10-12", 10)), done(at("2026-10-13", 10)),
                missed(at("2026-10-14", 15)), missed(at("2026-10-15", 15)), done(at("2026-10-16", 15)),
            ),
        )
        val insight = StatsCalculator.dashboard(snapshot, now).timeOfDay!!
        assertEquals(HourRate(10, 100, 3), insight.best)
        assertEquals(HourRate(15, 33, 3), insight.worst)
    }

    @Test
    fun calendarHasFiveMondayToSundayWeeksWithFutureDaysEmpty() {
        val today = LocalDate.parse("2026-11-11") // Mittwoch
        assertEquals(DayOfWeek.WEDNESDAY, today.dayOfWeek)
        val calendar = StatsCalculator.calendar(emptyMap(), today, goal = 8)
        assertEquals(5, calendar.weeks.size)
        assertTrue(calendar.weeks.all { it.size == 7 })
        assertEquals(LocalDate.parse("2026-10-12"), calendar.weeks.first().first()?.date)
        val lastWeek = calendar.weeks.last()
        assertEquals(today, lastWeek[2]?.date)
        assertTrue(lastWeek[2]!!.isToday)
        assertTrue(lastWeek.drop(3).all { it == null })
    }

    @Test
    fun calendarCountsGoalDaysAndDaysWithData() {
        val today = LocalDate.parse("2026-11-11")
        val daily = mapOf(
            LocalDate.parse("2026-11-09") to DayCounts(movements = 8),
            LocalDate.parse("2026-11-10") to DayCounts(movements = 3, missed = 1),
            // Nur Snoozes: kein fälliges Intervall, zählt nicht als Tag mit Daten.
            LocalDate.parse("2026-11-11") to DayCounts(snoozes = 2),
            // Vor der Monatsansicht.
            LocalDate.parse("2026-10-01") to DayCounts(movements = 9),
        )
        val calendar = StatsCalculator.calendar(daily, today, goal = 8)
        assertEquals(1, calendar.goalDays)
        assertEquals(2, calendar.daysWithData)
    }

    @Test
    fun goalShareIsClampedBetweenZeroAndOne() {
        assertEquals(0f, StatsCalculator.goalShare(0, 8), 0f)
        assertEquals(0.5f, StatsCalculator.goalShare(4, 8), 0f)
        assertEquals(1f, StatsCalculator.goalShare(12, 8), 0f)
        assertEquals(1f, StatsCalculator.goalShare(1, 0), 0f)
    }

    @Test
    fun dashboardSummarizesTodayWeekAndLastDays() {
        val now = at("2026-11-09", 15)
        val snapshot = TestData.snapshot(
            generatedAtMillis = now,
            settings = TestData.settings(dailyGoal = 4),
            events = listOf(
                done(at("2026-11-03", 9)), missed(at("2026-11-03", 10)),
                done(at("2026-11-09", 9)), auto(at("2026-11-09", 10)), missed(at("2026-11-09", 11)), snoozed(at("2026-11-09", 12)),
            ),
        )
        val dashboard = StatsCalculator.dashboard(snapshot, now)
        assertEquals(LocalDate.parse("2026-11-09"), dashboard.today)
        assertEquals(TodaySummary(movements = 2, snoozes = 1, missed = 1, dailyGoal = 4), dashboard.summary)
        assertEquals(67, dashboard.summary.percent)
        assertEquals(2, dashboard.summary.remainingToGoal)
        assertEquals(0.5f, dashboard.summary.goalProgress, 0f)
        assertFalse(dashboard.summary.goalReached)
        assertEquals(0, dashboard.streak)

        assertEquals(7, dashboard.week.size)
        assertEquals(LocalDate.parse("2026-11-03"), dashboard.week.first().date)
        assertTrue(dashboard.week.last().isToday)
        assertEquals(3, dashboard.weekMovements)
        assertEquals(60, dashboard.weekPercent)

        assertEquals(30, dashboard.lastDays.size)
        assertEquals(LocalDate.parse("2026-11-09"), dashboard.lastDays.first().date)
        assertEquals(LocalDate.parse("2026-10-11"), dashboard.lastDays.last().date)
        assertNull(dashboard.lastDays[1].percent)
        assertFalse(dashboard.lastDays[1].hasData)
    }

    @Test
    fun dailyGoalOverrideIsUsedForSummaryAndCalendar() {
        val now = at("2026-11-09", 15)
        val snapshot = TestData.snapshot(generatedAtMillis = now, events = listOf(done(at("2026-11-09", 9)), done(at("2026-11-09", 10))))
        val dashboard = StatsCalculator.dashboard(snapshot, now, dailyGoal = 2)
        assertTrue(dashboard.summary.goalReached)
        assertEquals(1, dashboard.calendar.goalDays)
    }
}
