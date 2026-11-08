package dev.stepcron.wear.data.settings

import dev.stepcron.wear.domain.AlarmKind
import dev.stepcron.wear.domain.WeeklySchedule
import java.time.ZoneId

/** Vom Nutzer einstellbare Werte (persistiert im DataStore). */
data class ReminderSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    /** An welchen Wochentagen von wann bis wann erinnert wird. */
    val schedule: WeeklySchedule = WeeklySchedule.DEFAULT,
    val autoStepEnabled: Boolean = true,
    /** Wurden die Laufzeit-Berechtigungen beim ersten Start schon angefragt? */
    val permissionsRequested: Boolean = false,
    /** Erinnerungen ruhen bis zu diesem Zeitpunkt (Schnellpause oder Urlaub); 0 = keine Pause. */
    val pausedUntil: Long = 0L,
    /** Dauer von „Snooze" (eine der [SNOOZE_OPTIONS]). */
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    /** So viele Schritte innerhalb von 2 Minuten gelten als Bewegungspause (eine der [STEP_THRESHOLD_OPTIONS]). */
    val stepThreshold: Int = DEFAULT_STEP_THRESHOLD,
    val vibration: VibrationLevel = VibrationLevel.STRONG,
    /** Keine Erinnerung bei „Nicht stören", Schlafenszeit oder Kinomodus der Uhr. */
    val respectQuietModes: Boolean = true,
    /** Keine Erinnerung, wenn die Uhr nicht getragen wird. */
    val onlyWhenWorn: Boolean = true,
    /** Angestrebte Bewegungspausen pro Tag. */
    val dailyGoal: Int = DEFAULT_DAILY_GOAL,
) {
    val intervalMillis: Long get() = intervalMinutes * 60_000L

    val snoozeMillis: Long get() = snoozeMinutes * 60_000L

    /** Ruhen die Erinnerungen gerade wegen einer Pause? */
    fun isPausedAt(nowMillis: Long): Boolean = pausedUntil > nowMillis

    /** Soll die Schritterkennung jetzt laufen? Berechtigung und Sensor prüft der Aufrufer. */
    fun wantsStepTracking(nowMillis: Long, zone: ZoneId): Boolean =
        enabled && autoStepEnabled && !isPausedAt(nowMillis) && schedule.contains(nowMillis, zone)

    /**
     * Bringt das Intervall in den gültigen Bereich (5–120 in 5er-Schritten) und Snooze-Dauer und Schritt-Schwelle
     * auf die nächste wählbare Option; der Wochenplan ist immer gültig.
     */
    fun sanitized(): ReminderSettings {
        val clamped = intervalMinutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
        val interval = ((clamped + INTERVAL_STEP_MINUTES / 2) / INTERVAL_STEP_MINUTES) * INTERVAL_STEP_MINUTES
        return copy(
            intervalMinutes = interval,
            snoozeMinutes = SNOOZE_OPTIONS.nearest(snoozeMinutes),
            stepThreshold = STEP_THRESHOLD_OPTIONS.nearest(stepThreshold),
            dailyGoal = dailyGoal.coerceIn(MIN_DAILY_GOAL, MAX_DAILY_GOAL),
        )
    }

    companion object {
        const val MIN_INTERVAL_MINUTES = 5
        const val MAX_INTERVAL_MINUTES = 120
        const val INTERVAL_STEP_MINUTES = 5
        const val DEFAULT_INTERVAL_MINUTES = 45

        val SNOOZE_OPTIONS: List<Int> = listOf(5, 10, 15, 20, 30)
        const val DEFAULT_SNOOZE_MINUTES = 10

        val STEP_THRESHOLD_OPTIONS: List<Int> = listOf(20, 30, 50, 75, 100)
        const val DEFAULT_STEP_THRESHOLD = 30

        const val MIN_DAILY_GOAL = 2
        const val MAX_DAILY_GOAL = 16
        const val DEFAULT_DAILY_GOAL = 8
    }
}

/** Wie stark die Erinnerung vibriert. */
enum class VibrationLevel {
    /** Nur die Notification, ohne Vibration. */
    OFF,
    LIGHT,
    MEDIUM,
    STRONG;

    /** Die nächste Stufe, nach [STRONG] wieder [OFF] (zum Durchtippen). */
    fun next(): VibrationLevel = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): VibrationLevel? = entries.firstOrNull { it.name == name }
    }
}

/** Nächstgelegene Option (bei Gleichstand die kleinere). */
fun List<Int>.nearest(value: Int): Int = minBy { kotlin.math.abs(it - value) }

/** Die Option [steps] Schritte neben der zu [current] nächstgelegenen, begrenzt auf die Liste. */
fun List<Int>.stepFrom(current: Int, steps: Int): Int = this[(indexOf(nearest(current)) + steps).coerceIn(indices)]

/** Laufzeitzustand von Timer und Schritterkennung (persistiert, damit er Neustarts übersteht). */
data class ReminderState(
    /** Zeitpunkt des aktuell geplanten Alarms (0 = keiner). */
    val nextAlarmAt: Long = 0L,
    val nextAlarmKind: AlarmKind? = null,
    /** Beginn des laufenden Intervalls (letzter Reset bzw. letzte Erinnerung). */
    val cycleStartAt: Long = 0L,
    /** Ziel der einmaligen Snooze-Erinnerung (0 = kein Snooze aktiv). */
    val snoozeUntil: Long = 0L,
    /** Zeitpunkt der noch unbestätigten Erinnerung (0 = keine offen). */
    val pendingReminderAt: Long = 0L,
    /** Letztes positives Bewegungs-Ereignis. */
    val lastMovementAt: Long = 0L,
    /** Letzte automatisch erkannte Gehpause (auch ohne Ereignis-Log). */
    val lastWalkDetectedAt: Long = 0L,
    /** Läuft die Schritterkennung gerade? */
    val stepTrackingActive: Boolean = false,
    /** Zeitpunkt der zuletzt still übersprungenen Erinnerung (0 = keine) und warum. */
    val lastSkippedAt: Long = 0L,
    val lastSkipReason: SkipReason? = null,
)

/** Warum eine fällige Erinnerung still übersprungen wurde. */
enum class SkipReason {
    DO_NOT_DISTURB,
    BEDTIME,
    THEATER_MODE,
    NOT_WORN;

    companion object {
        fun fromName(name: String?): SkipReason? = entries.firstOrNull { it.name == name }
    }
}
