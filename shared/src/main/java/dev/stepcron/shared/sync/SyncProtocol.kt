package dev.stepcron.shared.sync

import kotlinx.serialization.Serializable

/**
 * Abgleich zwischen Uhr und Handy über den Wearable Data Layer. Beide Apps haben dieselbe applicationId
 * (`dev.stepcron.wear`) und dieselbe Signatur – sonst stellt der Data Layer nichts zu.
 *
 * - Die Uhr veröffentlicht ihren Stand als DataItem [SNAPSHOT] mit dem Asset [SNAPSHOT_ASSET]
 *   ([SyncSnapshot], gzip-komprimiert). Der Data Layer hält es vor, das Handy liest es auch später noch.
 * - Das Handy bittet per Message [REQUEST_SYNC] um einen frischen Stand.
 * - Einstellungen schickt das Handy per Message [UPDATE_SETTINGS] ([SettingsUpdate]).
 * - Eine Sicherung spielt das Handy als DataItem unter [RESTORE_PREFIX]`<id>` mit dem Asset [RESTORE_ASSET]
 *   ([SyncBackup]) zurück; die Uhr antwortet mit der Message [RESTORE_RESULT] ([RestoreResult]) und löscht
 *   das DataItem.
 */
object SyncPaths {
    const val SNAPSHOT = "/deskbreak/snapshot"
    const val SNAPSHOT_ASSET = "snapshot"

    const val REQUEST_SYNC = "/deskbreak/request-sync"
    const val UPDATE_SETTINGS = "/deskbreak/update-settings"

    const val RESTORE_PREFIX = "/deskbreak/restore/"
    const val RESTORE_ASSET = "backup"
    const val RESTORE_RESULT = "/deskbreak/restore-result"

    /** Capabilities (res/values/wear.xml der jeweiligen App), über die sich die Geräte finden. */
    const val CAPABILITY_WATCH = "deskbreak_watch"
    const val CAPABILITY_PHONE = "deskbreak_phone"
}

/** Ein Wochentag des Zeitplans; Minuten seit Mitternacht, Ende bis 1440 (24:00). */
@Serializable
data class SyncDay(
    val enabled: Boolean,
    val startMinute: Int,
    val endMinute: Int,
)

/** Alle Einstellungen der Uhr. [schedule] hat sieben Einträge, Montag zuerst. */
@Serializable
data class SyncSettings(
    val enabled: Boolean,
    val intervalMinutes: Int,
    val schedule: List<SyncDay>,
    val autoStepEnabled: Boolean,
    val snoozeMinutes: Int,
    val stepThreshold: Int,
    /** Name von VibrationLevel der Uhr: OFF, LIGHT, MEDIUM, STRONG. */
    val vibration: String,
    val respectQuietModes: Boolean,
    val onlyWhenWorn: Boolean,
    val dailyGoal: Int,
    /** Pause bis (Epoch-Millis); 0 = keine Pause. */
    val pausedUntilMillis: Long,
)

/** Was die Uhr gerade vorhat – für die Anzeige auf dem Handy. */
@Serializable
data class SyncStatus(
    /** Nächster geplanter Alarm (Epoch-Millis), 0 = keiner. */
    val nextAlarmAtMillis: Long,
    /** Name von AlarmKind der Uhr: REMINDER, CYCLE_START, WINDOW_END; `null` = keiner. */
    val nextAlarmKind: String?,
    /** Noch unbeantwortete Erinnerung (Epoch-Millis), 0 = keine. */
    val pendingReminderAtMillis: Long,
    val snoozeUntilMillis: Long,
)

/**
 * Ein Ereignis der Bewegungs-Historie. [type] ist der Name von MovementEventType der Uhr:
 * COMPLETED_REMINDER, AUTO_STEP_RESET, MANUAL_RESET (Bewegungspausen), SNOOZED, MISSED.
 * Unbekannte Typen (aus einer neueren Version) werden beim Auswerten übergangen.
 */
@Serializable
data class SyncEvent(
    val timestampMillis: Long,
    val type: String,
    val intervalMinutes: Int,
) {
    val isMovement: Boolean get() = type in MOVEMENT_TYPES

    companion object {
        const val COMPLETED_REMINDER = "COMPLETED_REMINDER"
        const val AUTO_STEP_RESET = "AUTO_STEP_RESET"
        const val MANUAL_RESET = "MANUAL_RESET"
        const val SNOOZED = "SNOOZED"
        const val MISSED = "MISSED"

        val MOVEMENT_TYPES = setOf(COMPLETED_REMINDER, AUTO_STEP_RESET, MANUAL_RESET)
        val KNOWN_TYPES = MOVEMENT_TYPES + setOf(SNOOZED, MISSED)
    }
}

/** Stand der Uhr: Einstellungen, Status und die Historie der letzten [SyncCodec.HISTORY_DAYS] Tage. */
@Serializable
data class SyncSnapshot(
    val version: Int = SyncCodec.VERSION,
    val generatedAtMillis: Long,
    /** Zeitzone der Uhr (IANA-ID), in der Tage und Stunden ausgewertet werden. */
    val zoneId: String,
    val settings: SyncSettings,
    val status: SyncStatus,
    val events: List<SyncEvent>,
)

/**
 * Teilweise Änderung der Einstellungen vom Handy aus: Nur gesetzte Felder werden übernommen, die Uhr
 * prüft und begrenzt alle Werte selbst.
 */
@Serializable
data class SettingsUpdate(
    val enabled: Boolean? = null,
    val intervalMinutes: Int? = null,
    val schedule: List<SyncDay>? = null,
    val autoStepEnabled: Boolean? = null,
    val snoozeMinutes: Int? = null,
    val stepThreshold: Int? = null,
    val vibration: String? = null,
    val respectQuietModes: Boolean? = null,
    val onlyWhenWorn: Boolean? = null,
    val dailyGoal: Int? = null,
    /** Pause bis (Epoch-Millis); 0 beendet eine Pause. */
    val pausedUntilMillis: Long? = null,
) {
    val isEmpty: Boolean get() = this == SettingsUpdate()
}

/** Sicherung als Datei auf dem Handy (lesbares JSON) und Inhalt einer Wiederherstellung auf der Uhr. */
@Serializable
data class SyncBackup(
    val version: Int = SyncCodec.VERSION,
    val createdAtMillis: Long,
    val zoneId: String,
    val settings: SyncSettings,
    val events: List<SyncEvent>,
)

/** Antwort der Uhr auf eine Wiederherstellung. */
@Serializable
data class RestoreResult(
    /** Pfad-Suffix des wiederhergestellten DataItems (die `<id>` aus [SyncPaths.RESTORE_PREFIX]). */
    val restoreId: String,
    val success: Boolean,
    val restoredEvents: Int,
    val message: String? = null,
)
