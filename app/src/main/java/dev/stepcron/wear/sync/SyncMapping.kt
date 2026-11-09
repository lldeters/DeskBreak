package dev.stepcron.wear.sync

import dev.stepcron.shared.sync.SettingsUpdate
import dev.stepcron.shared.sync.SyncDay
import dev.stepcron.shared.sync.SyncEvent
import dev.stepcron.shared.sync.SyncPaths
import dev.stepcron.shared.sync.SyncSettings
import dev.stepcron.shared.sync.SyncStatus
import dev.stepcron.wear.data.db.MovementEvent
import dev.stepcron.wear.data.db.MovementEventType
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.ReminderState
import dev.stepcron.wear.data.settings.VibrationLevel
import dev.stepcron.wear.domain.ActiveWindow
import dev.stepcron.wear.domain.DaySchedule
import dev.stepcron.wear.domain.PauseOptions
import dev.stepcron.wear.domain.WeeklySchedule

/**
 * Übersetzt zwischen den Modellen der Uhr und dem Sync-Protokoll (:shared). Ohne Android, damit es auf der JVM
 * testbar bleibt.
 *
 * Werte vom Handy werden wie gespeicherte Werte bereinigt (Wochenplan über [ActiveWindow.sanitized], Zahlen über
 * [ReminderSettings.sanitized]): Ein falscher oder unbekannter Wert führt nie zu einer Ausnahme, sondern wird begrenzt
 * oder übergangen.
 */
object SyncMapping {

    private const val DAY_MILLIS = 24 * 60 * 60_000L

    /**
     * Längste Pause, die das Handy setzen kann: wie der längste Urlaub auf der Uhr (bis Mitternacht nach dem letzten
     * wählbaren Tag). So kann ein Tippfehler die Erinnerungen nicht für Jahre abschalten.
     */
    const val MAX_PAUSE_MILLIS = (PauseOptions.MAX_VACATION_DAYS + 1) * DAY_MILLIS

    /** Ereignisse bis so weit nach „jetzt" (abweichende Uhrzeit des Handys) werden übernommen, spätere übergangen. */
    const val FUTURE_TOLERANCE_MILLIS = DAY_MILLIS

    /** Rohwerte vom Handy vor dem Runden begrenzen, damit die Abstandsrechnung nicht überläuft. */
    private const val RAW_LIMIT = 100_000

    // ---------------------------------------------------------------------------------------------
    // Einstellungen
    // ---------------------------------------------------------------------------------------------

    fun toSyncSettings(settings: ReminderSettings): SyncSettings = SyncSettings(
        enabled = settings.enabled,
        intervalMinutes = settings.intervalMinutes,
        schedule = settings.schedule.days.map(::toSyncDay),
        autoStepEnabled = settings.autoStepEnabled,
        snoozeMinutes = settings.snoozeMinutes,
        stepThreshold = settings.stepThreshold,
        vibration = settings.vibration.name,
        respectQuietModes = settings.respectQuietModes,
        onlyWhenWorn = settings.onlyWhenWorn,
        dailyGoal = settings.dailyGoal,
        pausedUntilMillis = settings.pausedUntil,
    )

    fun toSyncDay(day: DaySchedule): SyncDay =
        SyncDay(enabled = day.enabled, startMinute = day.window.startMinute, endMinute = day.window.endMinute)

    /** Vollständige Einstellungen (z. B. aus einer Sicherung) als Änderung, in der jedes Feld gesetzt ist. */
    fun toSettingsUpdate(settings: SyncSettings): SettingsUpdate = SettingsUpdate(
        enabled = settings.enabled,
        intervalMinutes = settings.intervalMinutes,
        schedule = settings.schedule,
        autoStepEnabled = settings.autoStepEnabled,
        snoozeMinutes = settings.snoozeMinutes,
        stepThreshold = settings.stepThreshold,
        vibration = settings.vibration,
        respectQuietModes = settings.respectQuietModes,
        onlyWhenWorn = settings.onlyWhenWorn,
        dailyGoal = settings.dailyGoal,
        pausedUntilMillis = settings.pausedUntilMillis,
    )

    /**
     * Bereinigte Änderung in den Typen der Uhr. Ungesetzte Felder bleiben `null`, ebenso unbrauchbare: ein Wochenplan
     * ohne genau sieben Tage und eine unbekannte Vibrationsstufe (aus einer neueren Version).
     */
    fun toSettingsChange(update: SettingsUpdate): SettingsChange {
        // Dieselbe Bereinigung wie beim Speichern; nur die gesetzten Felder werden übernommen.
        val clamped = ReminderSettings(
            intervalMinutes = update.intervalMinutes?.bounded() ?: ReminderSettings.DEFAULT_INTERVAL_MINUTES,
            snoozeMinutes = update.snoozeMinutes?.bounded() ?: ReminderSettings.DEFAULT_SNOOZE_MINUTES,
            stepThreshold = update.stepThreshold?.bounded() ?: ReminderSettings.DEFAULT_STEP_THRESHOLD,
            dailyGoal = update.dailyGoal?.bounded() ?: ReminderSettings.DEFAULT_DAILY_GOAL,
        ).sanitized()
        return SettingsChange(
            enabled = update.enabled,
            intervalMinutes = update.intervalMinutes?.let { clamped.intervalMinutes },
            schedule = update.schedule?.let(::toSchedule),
            autoStepEnabled = update.autoStepEnabled,
            snoozeMinutes = update.snoozeMinutes?.let { clamped.snoozeMinutes },
            stepThreshold = update.stepThreshold?.let { clamped.stepThreshold },
            vibration = VibrationLevel.fromName(update.vibration),
            respectQuietModes = update.respectQuietModes,
            onlyWhenWorn = update.onlyWhenWorn,
            dailyGoal = update.dailyGoal?.let { clamped.dailyGoal },
            pausedUntilMillis = update.pausedUntilMillis,
        )
    }

    /** Wochenplan vom Handy, Montag zuerst; `null`, wenn es nicht genau sieben Tage sind. Fenster werden bereinigt. */
    fun toSchedule(days: List<SyncDay>): WeeklySchedule? {
        if (days.size != WeeklySchedule.DAYS_PER_WEEK) return null
        return WeeklySchedule(
            days.map { day ->
                DaySchedule(enabled = day.enabled, window = ActiveWindow.sanitized(day.startMinute, day.endMinute))
            },
        )
    }

    /**
     * Was „Pause bis [pausedUntilMillis]" vom Handy um [nowMillis] bewirkt: 0 oder ein vergangener Zeitpunkt beenden
     * eine Pause, ein künftiger pausiert – höchstens [MAX_PAUSE_MILLIS] lang.
     */
    fun toPauseChange(pausedUntilMillis: Long, nowMillis: Long): PauseChange =
        if (pausedUntilMillis > nowMillis) {
            PauseChange.PauseUntil(minOf(pausedUntilMillis, nowMillis + MAX_PAUSE_MILLIS))
        } else {
            PauseChange.Resume
        }

    private fun Int.bounded(): Int = coerceIn(-RAW_LIMIT, RAW_LIMIT)

    // ---------------------------------------------------------------------------------------------
    // Historie, Status und Wiederherstellung
    // ---------------------------------------------------------------------------------------------

    /** Kennung einer Wiederherstellung: der Pfad hinter [SyncPaths.RESTORE_PREFIX]; `null` für andere Pfade. */
    fun restoreId(path: String?): String? =
        path?.removePrefix(SyncPaths.RESTORE_PREFIX)?.takeIf { it != path && it.isNotEmpty() }

    fun toSyncEvent(event: MovementEvent): SyncEvent = SyncEvent(
        timestampMillis = event.timestamp,
        type = event.eventType.name,
        intervalMinutes = event.intervalMinutes,
    )

    /**
     * Ereignis für die Datenbank der Uhr (neue ID); `null` für einen unbekannten Typ (aus einer neueren Version) und
     * für Zeitpunkte vor 1970 oder mehr als [FUTURE_TOLERANCE_MILLIS] nach [nowMillis] – die könnte die Statistik
     * nicht sinnvoll einordnen.
     */
    fun toMovementEvent(event: SyncEvent, nowMillis: Long): MovementEvent? {
        val type = MovementEventType.entries.firstOrNull { it.name == event.type } ?: return null
        if (event.timestampMillis !in 0L..nowMillis + FUTURE_TOLERANCE_MILLIS) return null
        return MovementEvent(
            timestamp = event.timestampMillis,
            eventType = type,
            intervalMinutes = event.intervalMinutes,
        )
    }

    fun toMovementEvents(events: List<SyncEvent>, nowMillis: Long): List<MovementEvent> =
        events.mapNotNull { toMovementEvent(it, nowMillis) }

    fun toSyncStatus(state: ReminderState): SyncStatus = SyncStatus(
        nextAlarmAtMillis = state.nextAlarmAt,
        nextAlarmKind = state.nextAlarmKind?.name,
        pendingReminderAtMillis = state.pendingReminderAt,
        snoozeUntilMillis = state.snoozeUntil,
    )
}

/** Bereinigte Änderung der Einstellungen in den Typen der Uhr; `null` heißt: unverändert lassen. */
data class SettingsChange(
    val enabled: Boolean? = null,
    val intervalMinutes: Int? = null,
    val schedule: WeeklySchedule? = null,
    val autoStepEnabled: Boolean? = null,
    val snoozeMinutes: Int? = null,
    val stepThreshold: Int? = null,
    val vibration: VibrationLevel? = null,
    val respectQuietModes: Boolean? = null,
    val onlyWhenWorn: Boolean? = null,
    val dailyGoal: Int? = null,
    /** Roh vom Handy; was er bewirkt, entscheidet [SyncMapping.toPauseChange] zum Zeitpunkt des Übernehmens. */
    val pausedUntilMillis: Long? = null,
)

/** Was eine Pause-Angabe vom Handy auf der Uhr bewirkt. */
sealed interface PauseChange {
    /** Eine laufende Pause beenden. */
    data object Resume : PauseChange

    /** Bis [untilMillis] pausieren. */
    data class PauseUntil(val untilMillis: Long) : PauseChange
}
