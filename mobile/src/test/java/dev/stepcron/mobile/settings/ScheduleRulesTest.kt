package dev.stepcron.mobile.settings

import dev.stepcron.mobile.TestData
import dev.stepcron.mobile.TestData.at
import dev.stepcron.shared.sync.SyncDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRulesTest {

    private val day = SyncDay(enabled = true, startMinute = 8 * 60, endMinute = 17 * 60)

    @Test
    fun pickerTimesSnapToFifteenMinutes() {
        assertEquals(8 * 60, ScheduleRules.startFromPicker(8, 7))
        assertEquals(8 * 60 + 15, ScheduleRules.startFromPicker(8, 8))
        assertEquals(17 * 60 + 45, ScheduleRules.endFromPicker(17, 40))
    }

    @Test
    fun endOfMidnightMeansTwentyFour() {
        assertEquals(1440, ScheduleRules.endFromPicker(0, 0))
        assertEquals(1440, ScheduleRules.endFromPicker(23, 53))
        assertEquals(1440, ScheduleRules.endFromPicker(0, 5))
        assertEquals(15, ScheduleRules.endFromPicker(0, 10))
    }

    @Test
    fun latestStartIsQuarterToMidnight() {
        assertEquals(23 * 60 + 45, ScheduleRules.startFromPicker(23, 53))
        assertEquals(0, ScheduleRules.startFromPicker(0, 5))
    }

    @Test
    fun startMustBeFifteenMinutesBeforeTheEnd() {
        assertNull(ScheduleRules.checkStart(day, 16 * 60 + 45))
        assertEquals(ScheduleIssue.StartNotBeforeEnd(17 * 60), ScheduleRules.checkStart(day, 17 * 60))
        assertNull(ScheduleRules.checkEnd(day, 8 * 60 + 15))
        assertEquals(ScheduleIssue.EndNotAfterStart(8 * 60), ScheduleRules.checkEnd(day, 8 * 60))
    }

    @Test
    fun editsKeepTheOtherBoundAndTheMinimumLength() {
        val schedule = TestData.weekdays
        assertEquals(16 * 60 + 45, ScheduleRules.withStart(schedule, 0, 20 * 60)[0].startMinute)
        assertEquals(17 * 60, ScheduleRules.withStart(schedule, 0, 20 * 60)[0].endMinute)
        assertEquals(8 * 60 + 15, ScheduleRules.withEnd(schedule, 2, 5 * 60)[2].endMinute)
        assertEquals(1440, ScheduleRules.withEnd(schedule, 2, 1440)[2].endMinute)
        assertEquals(schedule.drop(1), ScheduleRules.withStart(schedule, 0, 9 * 60).drop(1))
        assertTrue(ScheduleRules.withEnabled(schedule, 6, true)[6].enabled)
    }

    @Test
    fun normalizeRepairsBrokenAndMissingDays() {
        val broken = listOf(SyncDay(true, 1430, 1430), SyncDay(false, -5, 2000))
        val normalized = ScheduleRules.normalize(broken)
        assertEquals(7, normalized.size)
        assertEquals(SyncDay(true, 1425, 1440), normalized[0])
        assertEquals(SyncDay(false, 0, 1440), normalized[1])
        assertEquals(ScheduleRules.defaultDay(4), normalized[4])
        assertFalse(normalized[6].enabled)
        assertTrue(normalized.all { ScheduleRules.isValid(it) })
    }

    @Test
    fun scheduleWithoutActiveDayIsReported() {
        assertEquals(listOf(ScheduleIssue.NoActiveDay), ScheduleRules.issues(List(7) { day.copy(enabled = false) }))
        assertTrue(ScheduleRules.issues(TestData.weekdays).isEmpty())
    }

    @Test
    fun timelineMatchesTheWatch() {
        val schedule = TestData.weekdays
        val zone = TestData.zone
        assertTrue(ScheduleRules.contains(schedule, at("2026-11-09", 8), zone))
        assertFalse(ScheduleRules.contains(schedule, at("2026-11-09", 17), zone))
        assertFalse(ScheduleRules.contains(schedule, at("2026-11-14", 10), zone))
        assertEquals(at("2026-11-10", 8), ScheduleRules.nextStart(schedule, at("2026-11-09", 8), zone))
        assertEquals(at("2026-11-16", 8), ScheduleRules.nextStart(schedule, at("2026-11-13", 18), zone))
        assertEquals(at("2026-11-09", 9), ScheduleRules.nextActiveAt(schedule, at("2026-11-09", 9), zone))
        assertEquals(at("2026-11-09", 17), ScheduleRules.endOfDay(schedule, at("2026-11-09", 9), zone))
        assertNull(ScheduleRules.endOfDay(schedule, at("2026-11-15", 9), zone))
        assertNull(ScheduleRules.nextStart(List(7) { day.copy(enabled = false) }, at("2026-11-09", 9), zone))
    }

    @Test
    fun onlyDayActiveAgainNextWeek() {
        val mondayOnly = List(7) { day.copy(enabled = it == 0) }
        assertEquals(at("2026-11-16", 8), ScheduleRules.nextStart(mondayOnly, at("2026-11-09", 18), TestData.zone))
    }
}
