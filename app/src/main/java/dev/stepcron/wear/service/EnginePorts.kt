package dev.stepcron.wear.service

import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.VibrationLevel
import dev.stepcron.wear.domain.PlannedAlarm
import java.time.ZoneId

// Schnittstellen der ReminderEngine nach außen: In der App stecken Android-Dienste dahinter (AlarmManager,
// Notifications, Foreground-Service), in den Unit-Tests einfache Attrappen. Siehe auch MovementLog.

/** Der eine geplante Alarm der Engine. */
interface Alarms {
    /** Plant [alarm] und ersetzt einen geplanten; `false`, wenn er nur ungenau geplant werden konnte. */
    fun schedule(alarm: PlannedAlarm): Boolean

    fun cancel()
}

/** Die Erinnerung, die man sieht und spürt. */
interface ReminderAlerts {
    /**
     * Zeigt die Erinnerung zum Zeitpunkt [reminderAtMillis] (bestimmt auch die vorgeschlagene Übung); die
     * Snooze-Aktion nennt [snoozeMinutes].
     */
    fun showReminder(reminderAtMillis: Long, snoozeMinutes: Int)

    /** Vibriert in der Stärke [level] ([VibrationLevel.OFF]: gar nicht). */
    fun vibrateReminder(level: VibrationLevel)

    fun cancelReminder()
}

/** Die automatische Schritterkennung. */
interface StepTracking {
    /** Ob sie jetzt laufen soll – inklusive Berechtigung und Sensor. */
    fun shouldRun(settings: ReminderSettings, nowMillis: Long, zone: ZoneId): Boolean

    fun start()

    fun stop()
}
