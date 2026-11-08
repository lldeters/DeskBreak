package dev.stepcron.wear.domain

import java.time.ZoneId

/** Art des (einzigen) geplanten DeskBreak-Alarms. */
enum class AlarmKind {
    /** Reguläre Bewegungs-Erinnerung (auch die einmalige Snooze-Erinnerung). */
    REMINDER,

    /** Beginn des aktiven Zeitfensters: Der Zyklus startet neu, noch ohne Erinnerung. */
    CYCLE_START,

    /**
     * Ende des aktiven Zeitfensters: Schritterkennung beenden und offene Erinnerung abschließen – bzw., wenn freie
     * Tage folgen, der Abschluss einer offenen Erinnerung nach der Kulanzzeit.
     */
    WINDOW_END;

    companion object {
        fun fromName(name: String?): AlarmKind? = entries.firstOrNull { it.name == name }
    }
}

data class PlannedAlarm(val triggerAtMillis: Long, val kind: AlarmKind)

/** Wie sich eine Änderung des Wochenplans auf das gerade laufende Fenster auswirkt. */
enum class ScheduleChange {
    /** Kein Übergang: weiter wie bisher. */
    NONE,

    /** Der heutige Tag wurde frei gestellt – im Fenster, davor oder danach. */
    TODAY_OFF,

    /** Das heutige Fenster wurde so verschoben, dass es jetzt vorbei ist bzw. noch nicht begonnen hat. */
    WINDOW_LEFT,

    /** Jetzt liegt (wieder) im heutigen Fenster, vorher nicht. */
    WINDOW_ENTERED,
}

/** Reine Planungslogik (ohne Android-Abhängigkeiten, daher unit-testbar). */
object ReminderPlanner {

    /** Mindestvorlauf, falls eine Erinnerung eigentlich schon überfällig wäre. */
    const val MIN_LEAD_MILLIS = 60_000L

    /** Eine Erinnerung, die kurz vor dem Fensterende kam, bleibt mindestens so lange beantwortbar. */
    const val WINDOW_END_GRACE_MILLIS = 15 * 60_000L

    /**
     * Plant innerhalb des heutigen Fensters: eine Erinnerung zu [desiredAtMillis], sofern sie vor dem
     * Fensterende liegt – ansonsten das Fensterende selbst. Aufrufer prüfen vorher, dass jetzt im Fenster
     * liegt; andernfalls wird der nächste Fensterbeginn geplant.
     */
    fun planInWindow(
        desiredAtMillis: Long,
        nowMillis: Long,
        schedule: WeeklySchedule,
        zone: ZoneId,
    ): PlannedAlarm? {
        val windowEnd = schedule.endOfDay(nowMillis, zone)
        if (windowEnd == null || !schedule.contains(nowMillis, zone)) {
            return planOutsideWindow(nowMillis, schedule, zone, pendingReminderAt = 0L)
        }
        return if (desiredAtMillis < windowEnd) {
            PlannedAlarm(desiredAtMillis, AlarmKind.REMINDER)
        } else {
            PlannedAlarm(windowEnd, AlarmKind.WINDOW_END)
        }
    }

    /**
     * Außerhalb der aktiven Zeit: den nächsten Fensterbeginn planen, oder nichts (`null`), wenn an keinem
     * Tag erinnert wird.
     *
     * Eine noch offene Erinnerung bleibt bis zum nächsten Morgen beantwortbar und wird erst beim nächsten
     * Fensterbeginn als verpasst gewertet. Liegt der nächste aktive Tag später (freie Tage dazwischen) oder
     * gibt es keinen, wird sie stattdessen nach [WINDOW_END_GRACE_MILLIS] abgeschlossen ([AlarmKind.WINDOW_END]),
     * damit sie nicht über freie Tage offen bleibt.
     */
    fun planOutsideWindow(
        nowMillis: Long,
        schedule: WeeklySchedule,
        zone: ZoneId,
        pendingReminderAt: Long,
    ): PlannedAlarm? {
        val nextStart = schedule.nextStart(nowMillis, zone)
        if (pendingReminderAt > 0L) {
            val lastAnswerableDay = pendingReminderAt.toLocalDate(zone).plusDays(1)
            if (nextStart == null || nextStart.toLocalDate(zone) > lastAnswerableDay) {
                return PlannedAlarm(maxOf(pendingReminderAt + WINDOW_END_GRACE_MILLIS, nowMillis), AlarmKind.WINDOW_END)
            }
        }
        return nextStart?.let { PlannedAlarm(it, AlarmKind.CYCLE_START) }
    }

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
        schedule: WeeklySchedule,
        zone: ZoneId,
        pendingReminderAt: Long,
    ): PlannedAlarm? {
        if (!schedule.contains(nowMillis, zone)) {
            return planOutsideWindow(nowMillis, schedule, zone, pendingReminderAt)
        }
        val due = cycleStartMillis + intervalMillis
        val desired = when {
            snoozeUntilMillis > nowMillis -> snoozeUntilMillis
            due > nowMillis -> due
            else -> nowMillis + MIN_LEAD_MILLIS
        }
        return planInWindow(desired, nowMillis, schedule, zone)
    }

    /**
     * Während einer Pause bis [pausedUntil]: Ihr Ende ist der erste aktive Moment danach, dort startet ein frischer
     * Zyklus ([AlarmKind.CYCLE_START]). `null`, wenn an keinem Tag erinnert wird.
     */
    fun planPauseEnd(pausedUntil: Long, schedule: WeeklySchedule, zone: ZoneId): PlannedAlarm? =
        schedule.nextActiveAt(pausedUntil, zone)?.let { PlannedAlarm(it, AlarmKind.CYCLE_START) }

    /** Ordnet eine Änderung des Wochenplans von [before] zu [after] zum Zeitpunkt [nowMillis] ein. */
    fun scheduleChange(
        before: WeeklySchedule,
        after: WeeklySchedule,
        nowMillis: Long,
        zone: ZoneId,
    ): ScheduleChange {
        val wasInside = before.contains(nowMillis, zone)
        val isInside = after.contains(nowMillis, zone)
        val todayWasOn = before.windowAt(nowMillis, zone) != null
        val todayIsOn = after.windowAt(nowMillis, zone) != null
        return when {
            todayWasOn && !todayIsOn -> ScheduleChange.TODAY_OFF
            wasInside && !isInside -> ScheduleChange.WINDOW_LEFT
            !wasInside && isInside -> ScheduleChange.WINDOW_ENTERED
            else -> ScheduleChange.NONE
        }
    }
}
