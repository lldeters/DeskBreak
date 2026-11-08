package dev.stepcron.wear.data.settings

import dev.stepcron.wear.domain.AlarmKind
import dev.stepcron.wear.domain.WeeklySchedule

/** Vom Nutzer einstellbare Werte (persistiert im DataStore). */
data class ReminderSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    /** An welchen Wochentagen von wann bis wann erinnert wird. */
    val schedule: WeeklySchedule = WeeklySchedule.DEFAULT,
    val autoStepEnabled: Boolean = true,
    /** Wurden die Laufzeit-Berechtigungen beim ersten Start schon angefragt? */
    val permissionsRequested: Boolean = false,
) {
    val intervalMillis: Long get() = intervalMinutes * 60_000L

    /** Bringt das Intervall in den gültigen Bereich (5–120 in 5er-Schritten); der Wochenplan ist immer gültig. */
    fun sanitized(): ReminderSettings {
        val clamped = intervalMinutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
        val interval = ((clamped + INTERVAL_STEP_MINUTES / 2) / INTERVAL_STEP_MINUTES) * INTERVAL_STEP_MINUTES
        return copy(intervalMinutes = interval)
    }

    companion object {
        const val MIN_INTERVAL_MINUTES = 5
        const val MAX_INTERVAL_MINUTES = 120
        const val INTERVAL_STEP_MINUTES = 5
        const val DEFAULT_INTERVAL_MINUTES = 45
    }
}

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
)
