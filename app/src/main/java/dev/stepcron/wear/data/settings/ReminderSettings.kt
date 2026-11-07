package dev.stepcron.wear.data.settings

import dev.stepcron.wear.domain.ActiveWindow
import dev.stepcron.wear.domain.AlarmKind

/** Vom Nutzer einstellbare Werte (persistiert im DataStore). */
data class ReminderSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val activeStartHour: Int = DEFAULT_START_HOUR,
    val activeEndHour: Int = DEFAULT_END_HOUR,
    val autoStepEnabled: Boolean = true,
    /** Wurden die Laufzeit-Berechtigungen beim ersten Start schon angefragt? */
    val permissionsRequested: Boolean = false,
) {
    val window: ActiveWindow get() = ActiveWindow(activeStartHour, activeEndHour)
    val intervalMillis: Long get() = intervalMinutes * 60_000L

    /** Bringt alle Werte in gültige Bereiche (Intervall 5–120 in 5er-Schritten, Start < Ende). */
    fun sanitized(): ReminderSettings {
        val clamped = intervalMinutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
        val interval = ((clamped + INTERVAL_STEP_MINUTES / 2) / INTERVAL_STEP_MINUTES) * INTERVAL_STEP_MINUTES
        val start = activeStartHour.coerceIn(0, 23)
        val end = activeEndHour.coerceIn(start + 1, 24)
        return copy(intervalMinutes = interval, activeStartHour = start, activeEndHour = end)
    }

    companion object {
        const val MIN_INTERVAL_MINUTES = 5
        const val MAX_INTERVAL_MINUTES = 120
        const val INTERVAL_STEP_MINUTES = 5
        const val DEFAULT_INTERVAL_MINUTES = 45
        const val DEFAULT_START_HOUR = 8
        const val DEFAULT_END_HOUR = 17
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
