package dev.stepcron.wear.domain

import java.time.ZoneId

/** Art des (einzigen) geplanten DeskBreak-Alarms. */
enum class AlarmKind {
    /** Reguläre Bewegungs-Erinnerung (auch die einmalige Snooze-Erinnerung). */
    REMINDER,

    /** Beginn des aktiven Zeitfensters: Der Zyklus startet neu, noch ohne Erinnerung. */
    CYCLE_START,

    /** Ende des aktiven Zeitfensters: Schritterkennung beenden und offene Erinnerung abschließen. */
    WINDOW_END;

    companion object {
        fun fromName(name: String?): AlarmKind? = entries.firstOrNull { it.name == name }
    }
}

data class PlannedAlarm(val triggerAtMillis: Long, val kind: AlarmKind)

/** Reine Planungslogik (ohne Android-Abhängigkeiten, daher unit-testbar). */
object ReminderPlanner {

    /** Mindestvorlauf, falls eine Erinnerung eigentlich schon überfällig wäre. */
    const val MIN_LEAD_MILLIS = 60_000L

    /**
     * Plant innerhalb des aktiven Fensters: eine Erinnerung zu [desiredAtMillis], sofern sie vor dem
     * Fensterende liegt – ansonsten das Fensterende selbst.
     */
    fun planInWindow(desiredAtMillis: Long, nowMillis: Long, window: ActiveWindow, zone: ZoneId): PlannedAlarm {
        val windowEnd = window.endOfDay(nowMillis, zone)
        return if (desiredAtMillis < windowEnd) {
            PlannedAlarm(desiredAtMillis, AlarmKind.REMINDER)
        } else {
            PlannedAlarm(windowEnd, AlarmKind.WINDOW_END)
        }
    }

    /** Außerhalb der aktiven Zeit: direkt den nächsten Fensterbeginn planen. */
    fun planOutsideWindow(nowMillis: Long, window: ActiveWindow, zone: ZoneId): PlannedAlarm =
        PlannedAlarm(window.nextStart(nowMillis, zone), AlarmKind.CYCLE_START)

    /**
     * Vollständige Neuplanung aus dem gespeicherten Zustand: Snooze hat Vorrang, sonst
     * Zyklusbeginn + Intervall. Nur eine bereits überfällige Erinnerung wird auf
     * „jetzt + [MIN_LEAD_MILLIS]" gelegt – eine noch anstehende behält ihre exakte Zeit, auch wenn
     * kurz davor neu geplant wird (z. B. durch eine Netzwerk-Zeitsynchronisation mit TIME_SET).
     */
    fun planNext(
        nowMillis: Long,
        cycleStartMillis: Long,
        snoozeUntilMillis: Long,
        intervalMillis: Long,
        window: ActiveWindow,
        zone: ZoneId,
    ): PlannedAlarm {
        if (!window.contains(nowMillis, zone)) return planOutsideWindow(nowMillis, window, zone)
        val due = cycleStartMillis + intervalMillis
        val desired = when {
            snoozeUntilMillis > nowMillis -> snoozeUntilMillis
            due > nowMillis -> due
            else -> nowMillis + MIN_LEAD_MILLIS
        }
        return planInWindow(desired, nowMillis, window, zone)
    }
}
