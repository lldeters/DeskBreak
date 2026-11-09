package dev.stepcron.mobile.stats

import dev.stepcron.mobile.settings.ScheduleRules
import dev.stepcron.mobile.settings.toLocalDate
import dev.stepcron.shared.sync.SyncSettings
import dev.stepcron.shared.sync.SyncSnapshot
import java.time.ZoneId

/** Was die Uhr gerade vorhat – abgeleitet wie der Status-Kopf der Uhr (HomeUiState dort). */
sealed interface WatchStatus {
    /** Erinnerungen sind ausgeschaltet. */
    data object RemindersOff : WatchStatus

    /** Erinnerungen an, aber im Zeitplan ist kein Tag aktiv. */
    data object NoActiveDays : WatchStatus

    /** Vom Nutzer pausiert bis [untilMillis]; weiter geht es um [resumeAtMillis] (erster aktiver Moment danach). */
    data class Paused(val untilMillis: Long, val resumeAtMillis: Long?) : WatchStatus

    /** Ruhezeit bis [resumeAtMillis]; [freeToday]: heute ist laut Zeitplan frei. */
    data class OffHours(val resumeAtMillis: Long, val freeToday: Boolean) : WatchStatus

    /** Heute passt keine Erinnerung mehr ins Fenster; Ruhezeit ab [offHoursFromMillis]. */
    data class DayDone(val offHoursFromMillis: Long) : WatchStatus

    /** Nächste Erinnerung um [atMillis]; [snoozed]: es ist die Snooze-Erinnerung. */
    data class NextReminder(val atMillis: Long, val snoozed: Boolean) : WatchStatus

    /** Die Uhr plant gerade bzw. der Stand ist veraltet (geplanter Zeitpunkt schon vorbei). */
    data object Unknown : WatchStatus
}

/** Status der Uhr samt offener Erinnerung und Alter des Stands. */
data class WatchStatusInfo(
    val status: WatchStatus,
    /** Zeitpunkt einer noch unbeantworteten Erinnerung oder `null`. */
    val pendingReminderAtMillis: Long?,
    /** Wann die Uhr diesen Stand erzeugt hat. */
    val asOfMillis: Long,
    /** Ist der Stand so alt, dass er womöglich nicht mehr stimmt? */
    val outdated: Boolean,
)

object WatchStatusCalculator {

    /** Ab diesem Alter gilt ein Snapshot für die Status-Anzeige als veraltet. */
    const val OUTDATED_AFTER_MILLIS = 30 * 60_000L

    /** Name von AlarmKind der Uhr. */
    const val KIND_REMINDER = "REMINDER"
    const val KIND_WINDOW_END = "WINDOW_END"

    /**
     * Status zum Zeitpunkt [nowMillis]. [settings] sind die des Snapshots oder – nach einer Änderung auf dem
     * Handy – schon die geänderten, damit z. B. „Erinnerungen aus" sofort erscheint.
     */
    fun compute(
        snapshot: SyncSnapshot,
        settings: SyncSettings,
        nowMillis: Long,
        zone: ZoneId = StatsCalculator.zoneOf(snapshot),
    ): WatchStatusInfo {
        val status = status(snapshot, settings, nowMillis, zone)
        val pending = snapshot.status.pendingReminderAtMillis.takeIf { settings.enabled && it > 0L }
        return WatchStatusInfo(
            status = status,
            pendingReminderAtMillis = pending,
            asOfMillis = snapshot.generatedAtMillis,
            outdated = nowMillis - snapshot.generatedAtMillis > OUTDATED_AFTER_MILLIS,
        )
    }

    private fun status(snapshot: SyncSnapshot, settings: SyncSettings, nowMillis: Long, zone: ZoneId): WatchStatus {
        if (!settings.enabled) return WatchStatus.RemindersOff
        val schedule = ScheduleRules.normalize(settings.schedule)
        if (schedule.none { it.enabled }) return WatchStatus.NoActiveDays

        if (settings.pausedUntilMillis > nowMillis) {
            val resumeAt = ScheduleRules.nextActiveAt(schedule, settings.pausedUntilMillis, zone)
            return WatchStatus.Paused(settings.pausedUntilMillis, resumeAt)
        }

        if (!ScheduleRules.contains(schedule, nowMillis, zone)) {
            // Aus dem Zeitplan statt aus dem gemeldeten Alarm, wie auf der Uhr: nach einer Änderung sofort aktuell.
            val resumeAt = ScheduleRules.nextStart(schedule, nowMillis, zone) ?: return WatchStatus.NoActiveDays
            val freeToday = ScheduleRules.windowOn(schedule, nowMillis.toLocalDate(zone)) == null
            return WatchStatus.OffHours(resumeAt, freeToday)
        }

        val watch = snapshot.status
        return when {
            watch.nextAlarmKind == KIND_WINDOW_END -> {
                val end = ScheduleRules.endOfDay(schedule, nowMillis, zone) ?: watch.nextAlarmAtMillis
                WatchStatus.DayDone(end)
            }

            watch.nextAlarmKind == KIND_REMINDER && watch.nextAlarmAtMillis > nowMillis -> WatchStatus.NextReminder(
                atMillis = watch.nextAlarmAtMillis,
                snoozed = watch.snoozeUntilMillis > 0L && watch.snoozeUntilMillis == watch.nextAlarmAtMillis,
            )

            else -> WatchStatus.Unknown
        }
    }
}
