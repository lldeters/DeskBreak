package dev.stepcron.mobile.settings

import dev.stepcron.mobile.TestData
import dev.stepcron.mobile.TestData.at
import dev.stepcron.shared.sync.SettingsUpdate
import dev.stepcron.shared.sync.SyncDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRulesTest {

    private val settings = TestData.settings()

    @Test
    fun intervalIsClampedToFiveToHundredTwentyInStepsOfFive() {
        assertEquals(5, SettingsRules.clampInterval(0))
        assertEquals(120, SettingsRules.clampInterval(500))
        assertEquals(45, SettingsRules.clampInterval(43))
        assertEquals(40, SettingsRules.clampInterval(42))
        assertEquals(50, SettingsRules.stepInterval(45, 1))
        assertEquals(5, SettingsRules.stepInterval(5, -1))
        assertEquals(120, SettingsRules.stepInterval(120, 1))
        assertFalse(SettingsRules.canDecreaseInterval(5))
        assertFalse(SettingsRules.canIncreaseInterval(120))
        assertTrue(SettingsRules.canIncreaseInterval(115))
    }

    @Test
    fun dailyGoalIsClampedToTwoToSixteen() {
        assertEquals(2, SettingsRules.stepDailyGoal(2, -1))
        assertEquals(16, SettingsRules.stepDailyGoal(16, 1))
        assertEquals(9, SettingsRules.stepDailyGoal(8, 1))
        assertEquals(16, SettingsRules.clampDailyGoal(40))
        assertFalse(SettingsRules.canDecreaseGoal(2))
        assertFalse(SettingsRules.canIncreaseGoal(16))
    }

    @Test
    fun snoozeAndStepThresholdSnapToTheNearestOptionPreferringTheSmaller() {
        assertEquals(5, SettingsRules.nearestSnooze(1))
        assertEquals(10, SettingsRules.nearestSnooze(12))
        assertEquals(30, SettingsRules.nearestSnooze(99))
        assertEquals(20, SettingsRules.nearestSnooze(25))
        assertEquals(50, SettingsRules.nearestStepThreshold(45))
        assertEquals(30, SettingsRules.nearestStepThreshold(40))
        assertEquals(100, SettingsRules.nearestStepThreshold(1000))
        assertEquals(20, SettingsRules.nearestStepThreshold(0))
    }

    @Test
    fun unknownVibrationNamesFallBackToStrong() {
        assertEquals(VibrationLevel.LIGHT, SettingsRules.vibrationOf("LIGHT"))
        assertEquals(VibrationLevel.STRONG, SettingsRules.vibrationOf("ULTRA"))
    }

    @Test
    fun diffContainsOnlyChangedFieldsAndApplyRestoresThem() {
        val changed = settings.copy(intervalMinutes = 60, vibration = "OFF")
        val diff = SettingsRules.diff(settings, changed)
        assertEquals(SettingsUpdate(intervalMinutes = 60, vibration = "OFF"), diff)
        assertEquals(changed, SettingsRules.apply(settings, diff))
        assertTrue(SettingsRules.diff(settings, settings).isEmpty)
    }

    @Test
    fun mergePrefersTheNewerValue() {
        val merged = SettingsRules.merge(
            SettingsUpdate(intervalMinutes = 50, dailyGoal = 9),
            SettingsUpdate(intervalMinutes = 55, enabled = false),
        )
        assertEquals(SettingsUpdate(intervalMinutes = 55, dailyGoal = 9, enabled = false), merged)
    }

    @Test
    fun editsContainOnlyTheChangedFieldWithAValidValue() {
        assertEquals(SettingsUpdate(intervalMinutes = 50), SettingsEdits.stepInterval(settings, 1))
        assertEquals(SettingsUpdate(dailyGoal = 7), SettingsEdits.stepDailyGoal(settings, -1))
        assertEquals(SettingsUpdate(snoozeMinutes = 15), SettingsEdits.snooze(settings, 14))
        assertEquals(SettingsUpdate(stepThreshold = 75), SettingsEdits.stepThreshold(settings, 75))
        assertEquals(SettingsUpdate(vibration = "MEDIUM"), SettingsEdits.vibration(settings, VibrationLevel.MEDIUM))
        assertEquals(SettingsUpdate(enabled = false), SettingsEdits.enabled(settings, false))
        assertEquals(SettingsUpdate(autoStepEnabled = false), SettingsEdits.autoSteps(settings, false))
        assertEquals(SettingsUpdate(respectQuietModes = false), SettingsEdits.respectQuietModes(settings, false))
        assertEquals(SettingsUpdate(onlyWhenWorn = false), SettingsEdits.onlyWhenWorn(settings, false))
    }

    @Test
    fun editsWithoutChangeAreEmpty() {
        assertTrue(SettingsEdits.enabled(settings, true).isEmpty)
        assertTrue(SettingsEdits.snooze(settings, 10).isEmpty)
        assertTrue(SettingsEdits.stepInterval(settings.copy(intervalMinutes = 120), 1).isEmpty)
        assertTrue(SettingsEdits.endPause(settings).isEmpty)
        assertTrue(SettingsEdits.dayEnabled(settings, 0, true).isEmpty)
    }

    @Test
    fun scheduleEditSendsTheWholeWeek() {
        val update = SettingsEdits.dayEnabled(settings, 5, true)
        assertEquals(7, update.schedule?.size)
        assertEquals(SyncDay(true, 480, 1020), update.schedule?.get(5))
        assertEquals(settings.schedule.take(5), update.schedule?.take(5))
    }

    @Test
    fun pausesEndAfterAnHourOrAtMidnightInTheWatchZone() {
        val now = at("2026-11-09", 10, 20)
        assertEquals(SettingsUpdate(pausedUntilMillis = now + 3_600_000L), SettingsEdits.pauseFor(settings, now, 60))
        assertEquals(
            SettingsUpdate(pausedUntilMillis = at("2026-11-10", 0)),
            SettingsEdits.pauseRestOfDay(settings, now, TestData.zone),
        )
        val paused = settings.copy(pausedUntilMillis = now + 1)
        assertTrue(SettingsRules.isPaused(paused, now))
        assertEquals(SettingsUpdate(pausedUntilMillis = 0L), SettingsEdits.endPause(paused))
    }
}
