package dev.stepcron.mobile.settings

import dev.stepcron.shared.sync.SettingsUpdate
import dev.stepcron.shared.sync.SyncDay
import dev.stepcron.shared.sync.SyncSettings
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Vibrationsstärke der Erinnerung – dieselben Namen wie VibrationLevel der Uhr. */
enum class VibrationLevel {
    OFF,
    LIGHT,
    MEDIUM,
    STRONG;

    companion object {
        fun fromName(name: String?): VibrationLevel? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Wertebereiche der Einstellungen, identisch zu ReminderSettings der Uhr. Die Uhr prüft und begrenzt alles
 * noch einmal selbst; das Handy schickt trotzdem nur gültige Werte, damit die Anzeige nicht springt.
 */
object SettingsRules {
    const val MIN_INTERVAL_MINUTES = 5
    const val MAX_INTERVAL_MINUTES = 120
    const val INTERVAL_STEP_MINUTES = 5

    const val MIN_DAILY_GOAL = 2
    const val MAX_DAILY_GOAL = 16

    val SNOOZE_OPTIONS: List<Int> = listOf(5, 10, 15, 20, 30)
    val STEP_THRESHOLD_OPTIONS: List<Int> = listOf(20, 30, 50, 75, 100)

    /** Dauer der Schnellpause auf dem Handy. */
    const val QUICK_PAUSE_MINUTES = 60

    /** Intervall im Bereich 5–120, auf 5 Minuten gerundet. */
    fun clampInterval(minutes: Int): Int {
        val clamped = minutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
        return ((clamped + INTERVAL_STEP_MINUTES / 2) / INTERVAL_STEP_MINUTES) * INTERVAL_STEP_MINUTES
    }

    /** Intervall [steps] Schritte (je 5 Minuten) neben [current], begrenzt auf den gültigen Bereich. */
    fun stepInterval(current: Int, steps: Int): Int = clampInterval(clampInterval(current) + steps * INTERVAL_STEP_MINUTES)

    fun canDecreaseInterval(current: Int): Boolean = clampInterval(current) > MIN_INTERVAL_MINUTES
    fun canIncreaseInterval(current: Int): Boolean = clampInterval(current) < MAX_INTERVAL_MINUTES

    fun clampDailyGoal(goal: Int): Int = goal.coerceIn(MIN_DAILY_GOAL, MAX_DAILY_GOAL)

    fun stepDailyGoal(current: Int, steps: Int): Int = clampDailyGoal(clampDailyGoal(current) + steps)

    fun canDecreaseGoal(current: Int): Boolean = clampDailyGoal(current) > MIN_DAILY_GOAL
    fun canIncreaseGoal(current: Int): Boolean = clampDailyGoal(current) < MAX_DAILY_GOAL

    /** Nächstgelegene Snooze-Option (bei Gleichstand die kleinere). */
    fun nearestSnooze(minutes: Int): Int = SNOOZE_OPTIONS.nearest(minutes)

    /** Nächstgelegene Schritt-Schwelle (bei Gleichstand die kleinere). */
    fun nearestStepThreshold(steps: Int): Int = STEP_THRESHOLD_OPTIONS.nearest(steps)

    /** Snooze [steps] Optionen neben [current] (5 → 10 → 15 → 20 → 30), begrenzt auf die Liste – wie auf der Uhr. */
    fun stepSnooze(current: Int, steps: Int): Int = SNOOZE_OPTIONS.step(nearestSnooze(current), steps)

    fun canDecreaseSnooze(current: Int): Boolean = nearestSnooze(current) > SNOOZE_OPTIONS.first()
    fun canIncreaseSnooze(current: Int): Boolean = nearestSnooze(current) < SNOOZE_OPTIONS.last()

    /** Schritt-Schwelle [steps] Optionen neben [current] (20 → 30 → 50 → 75 → 100), begrenzt auf die Liste. */
    fun stepStepThreshold(current: Int, steps: Int): Int =
        STEP_THRESHOLD_OPTIONS.step(nearestStepThreshold(current), steps)

    fun canDecreaseStepThreshold(current: Int): Boolean = nearestStepThreshold(current) > STEP_THRESHOLD_OPTIONS.first()
    fun canIncreaseStepThreshold(current: Int): Boolean = nearestStepThreshold(current) < STEP_THRESHOLD_OPTIONS.last()

    /** Vibrationsstufe eines Snapshots; unbekannte Namen (neuere Uhr) gelten als „Stark" wie auf der Uhr. */
    fun vibrationOf(name: String): VibrationLevel = VibrationLevel.fromName(name) ?: VibrationLevel.STRONG

    /** Läuft gerade eine Pause? */
    fun isPaused(settings: SyncSettings, nowMillis: Long): Boolean = settings.pausedUntilMillis > nowMillis

    /** Pause für [minutes] Minuten ab [nowMillis]. */
    fun pauseFor(nowMillis: Long, minutes: Int): Long = nowMillis + minutes * 60_000L

    /** Pause bis Mitternacht des aktuellen Tages in der Zeitzone der Uhr (wie „Rest des Tages" auf der Uhr). */
    fun pauseRestOfDay(nowMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Übernimmt die gesetzten Felder von [update] in [settings]. */
    fun apply(settings: SyncSettings, update: SettingsUpdate): SyncSettings = settings.copy(
        enabled = update.enabled ?: settings.enabled,
        intervalMinutes = update.intervalMinutes ?: settings.intervalMinutes,
        schedule = update.schedule ?: settings.schedule,
        autoStepEnabled = update.autoStepEnabled ?: settings.autoStepEnabled,
        snoozeMinutes = update.snoozeMinutes ?: settings.snoozeMinutes,
        stepThreshold = update.stepThreshold ?: settings.stepThreshold,
        vibration = update.vibration ?: settings.vibration,
        respectQuietModes = update.respectQuietModes ?: settings.respectQuietModes,
        onlyWhenWorn = update.onlyWhenWorn ?: settings.onlyWhenWorn,
        dailyGoal = update.dailyGoal ?: settings.dailyGoal,
        pausedUntilMillis = update.pausedUntilMillis ?: settings.pausedUntilMillis,
    )

    /** Nur die Felder, in denen sich [new] von [old] unterscheidet. */
    fun diff(old: SyncSettings, new: SyncSettings): SettingsUpdate = SettingsUpdate(
        enabled = new.enabled.takeIf { it != old.enabled },
        intervalMinutes = new.intervalMinutes.takeIf { it != old.intervalMinutes },
        schedule = new.schedule.takeIf { it != old.schedule },
        autoStepEnabled = new.autoStepEnabled.takeIf { it != old.autoStepEnabled },
        snoozeMinutes = new.snoozeMinutes.takeIf { it != old.snoozeMinutes },
        stepThreshold = new.stepThreshold.takeIf { it != old.stepThreshold },
        vibration = new.vibration.takeIf { it != old.vibration },
        respectQuietModes = new.respectQuietModes.takeIf { it != old.respectQuietModes },
        onlyWhenWorn = new.onlyWhenWorn.takeIf { it != old.onlyWhenWorn },
        dailyGoal = new.dailyGoal.takeIf { it != old.dailyGoal },
        pausedUntilMillis = new.pausedUntilMillis.takeIf { it != old.pausedUntilMillis },
    )

    /** Fasst zwei Änderungen zusammen; bei gleichen Feldern gewinnt [newer]. */
    fun merge(older: SettingsUpdate, newer: SettingsUpdate): SettingsUpdate = SettingsUpdate(
        enabled = newer.enabled ?: older.enabled,
        intervalMinutes = newer.intervalMinutes ?: older.intervalMinutes,
        schedule = newer.schedule ?: older.schedule,
        autoStepEnabled = newer.autoStepEnabled ?: older.autoStepEnabled,
        snoozeMinutes = newer.snoozeMinutes ?: older.snoozeMinutes,
        stepThreshold = newer.stepThreshold ?: older.stepThreshold,
        vibration = newer.vibration ?: older.vibration,
        respectQuietModes = newer.respectQuietModes ?: older.respectQuietModes,
        onlyWhenWorn = newer.onlyWhenWorn ?: older.onlyWhenWorn,
        dailyGoal = newer.dailyGoal ?: older.dailyGoal,
        pausedUntilMillis = newer.pausedUntilMillis ?: older.pausedUntilMillis,
    )

    private fun List<Int>.nearest(value: Int): Int = minBy { abs(it - value) }

    /** Option [steps] Plätze neben [option] (muss in der Liste stehen), begrenzt auf die Liste. */
    private fun List<Int>.step(option: Int, steps: Int): Int = this[(indexOf(option) + steps).coerceIn(0, lastIndex)]
}

/**
 * Baut die [SettingsUpdate]s der Bedienelemente aus dem aktuellen Stand: Jede Änderung enthält nur das
 * betroffene Feld mit einem gültigen Wert, unveränderte Werte ergeben eine leere Änderung.
 */
object SettingsEdits {

    fun enabled(current: SyncSettings, enabled: Boolean): SettingsUpdate =
        SettingsUpdate(enabled = enabled).without(current)

    fun stepInterval(current: SyncSettings, steps: Int): SettingsUpdate =
        SettingsUpdate(intervalMinutes = SettingsRules.stepInterval(current.intervalMinutes, steps)).without(current)

    fun stepDailyGoal(current: SyncSettings, steps: Int): SettingsUpdate =
        SettingsUpdate(dailyGoal = SettingsRules.stepDailyGoal(current.dailyGoal, steps)).without(current)

    fun snooze(current: SyncSettings, minutes: Int): SettingsUpdate =
        SettingsUpdate(snoozeMinutes = SettingsRules.nearestSnooze(minutes)).without(current)

    fun stepThreshold(current: SyncSettings, steps: Int): SettingsUpdate =
        SettingsUpdate(stepThreshold = SettingsRules.nearestStepThreshold(steps)).without(current)

    /** Snooze eine Option kürzer (−1) bzw. länger (+1). */
    fun stepSnooze(current: SyncSettings, steps: Int): SettingsUpdate =
        SettingsUpdate(snoozeMinutes = SettingsRules.stepSnooze(current.snoozeMinutes, steps)).without(current)

    /** Schritt-Schwelle eine Option niedriger (−1) bzw. höher (+1). */
    fun stepStepThreshold(current: SyncSettings, steps: Int): SettingsUpdate =
        SettingsUpdate(stepThreshold = SettingsRules.stepStepThreshold(current.stepThreshold, steps)).without(current)

    fun vibration(current: SyncSettings, level: VibrationLevel): SettingsUpdate =
        SettingsUpdate(vibration = level.name).without(current)

    fun autoSteps(current: SyncSettings, enabled: Boolean): SettingsUpdate =
        SettingsUpdate(autoStepEnabled = enabled).without(current)

    fun respectQuietModes(current: SyncSettings, enabled: Boolean): SettingsUpdate =
        SettingsUpdate(respectQuietModes = enabled).without(current)

    fun onlyWhenWorn(current: SyncSettings, enabled: Boolean): SettingsUpdate =
        SettingsUpdate(onlyWhenWorn = enabled).without(current)

    fun dayEnabled(current: SyncSettings, dayIndex: Int, enabled: Boolean): SettingsUpdate =
        schedule(current, ScheduleRules.withEnabled(current.schedule, dayIndex, enabled))

    fun dayStart(current: SyncSettings, dayIndex: Int, startMinute: Int): SettingsUpdate =
        schedule(current, ScheduleRules.withStart(current.schedule, dayIndex, startMinute))

    fun dayEnd(current: SyncSettings, dayIndex: Int, endMinute: Int): SettingsUpdate =
        schedule(current, ScheduleRules.withEnd(current.schedule, dayIndex, endMinute))

    /**
     * Beginn und Ende eines Tages in einem Schritt (z. B. 08:00–17:00 → 18:00–22:00, was nacheinander am
     * jeweils alten Ende bzw. Beginn scheitern würde). Werte wie [window] begrenzt.
     */
    fun dayWindow(current: SyncSettings, dayIndex: Int, startMinute: Int, endMinute: Int): SettingsUpdate {
        val schedule = ScheduleRules.normalize(current.schedule)
        return schedule(
            current,
            schedule.mapIndexed { index, day -> if (index == dayIndex) window(day, startMinute, endMinute) else day },
        )
    }

    /**
     * Dieselben Uhrzeiten für alle sieben Tage – wie „Für alle Tage" auf der Uhr: Welche Tage aktiv oder frei sind,
     * bleibt gleich; freie Tage haben die Zeiten dann parat, wenn man sie einschaltet.
     */
    fun windowForAllDays(current: SyncSettings, startMinute: Int, endMinute: Int): SettingsUpdate =
        schedule(current, ScheduleRules.normalize(current.schedule).map { window(it, startMinute, endMinute) })

    /** Haben alle Tage schon das Fenster [startMinute]–[endMinute] (nach dem Begrenzen)? Dann gibt es nichts zu übernehmen. */
    fun allDaysHaveWindow(current: SyncSettings, startMinute: Int, endMinute: Int): Boolean =
        windowForAllDays(current, startMinute, endMinute).isEmpty

    /** Fenster auf das 15-Minuten-Raster gerundet, Beginn höchstens 23:45, mindestens 15 Minuten lang, Ende bis 24:00. */
    private fun window(day: SyncDay, startMinute: Int, endMinute: Int): SyncDay {
        val start = ScheduleRules.snap(startMinute).coerceIn(0, ScheduleRules.MAX_START_MINUTE)
        val end = ScheduleRules.snap(endMinute)
            .coerceIn(start + ScheduleRules.MIN_LENGTH_MINUTES, ScheduleRules.MINUTES_PER_DAY)
        return day.copy(startMinute = start, endMinute = end)
    }

    fun endPause(current: SyncSettings): SettingsUpdate = SettingsUpdate(pausedUntilMillis = 0L).without(current)

    fun pauseFor(current: SyncSettings, nowMillis: Long, minutes: Int): SettingsUpdate =
        SettingsUpdate(pausedUntilMillis = SettingsRules.pauseFor(nowMillis, minutes)).without(current)

    fun pauseRestOfDay(current: SyncSettings, nowMillis: Long, zone: ZoneId): SettingsUpdate =
        SettingsUpdate(pausedUntilMillis = SettingsRules.pauseRestOfDay(nowMillis, zone)).without(current)

    private fun schedule(current: SyncSettings, schedule: List<SyncDay>): SettingsUpdate =
        SettingsUpdate(schedule = schedule).without(current)

    /** Lässt Felder weg, die schon den Wert von [current] haben. */
    private fun SettingsUpdate.without(current: SyncSettings): SettingsUpdate =
        SettingsRules.diff(current, SettingsRules.apply(current, this))
}
