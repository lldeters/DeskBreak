package dev.stepcron.wear.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.stepcron.wear.domain.ActiveWindow
import dev.stepcron.wear.domain.AlarmKind
import dev.stepcron.wear.domain.WeeklySchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "deskbreak_settings")

/** Einstellungen und Timer-Zustand auf Basis von Jetpack DataStore Preferences. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) {
            Log.w(TAG, "DataStore nicht lesbar – verwende Standardwerte", error)
            emit(emptyPreferences())
        } else {
            throw error
        }
    }

    val settings: Flow<ReminderSettings> = preferences.map { it.toSettings() }.distinctUntilChanged()

    val state: Flow<ReminderState> = preferences.map { it.toState() }.distinctUntilChanged()

    suspend fun currentSettings(): ReminderSettings = settings.first()

    suspend fun currentState(): ReminderState = state.first()

    /** Atomares Read-Modify-Write der Einstellungen; liefert die gespeicherten (bereinigten) Werte. */
    suspend fun updateSettings(transform: (ReminderSettings) -> ReminderSettings): ReminderSettings =
        dataStore.edit { prefs -> prefs.writeSettings(transform(prefs.toSettings()).sanitized()) }.toSettings()

    /** Atomares Read-Modify-Write des Laufzeitzustands. */
    suspend fun updateState(transform: (ReminderState) -> ReminderState): ReminderState =
        dataStore.edit { prefs -> prefs.writeState(transform(prefs.toState())) }.toState()

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
        val AUTO_STEP_ENABLED = booleanPreferencesKey("auto_step_enabled")
        val PERMISSIONS_REQUESTED = booleanPreferencesKey("permissions_requested")
        val PAUSED_UNTIL = longPreferencesKey("paused_until")
        val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        val STEP_THRESHOLD = intPreferencesKey("step_threshold")
        val VIBRATION = stringPreferencesKey("vibration_level")
        val RESPECT_QUIET_MODES = booleanPreferencesKey("respect_quiet_modes")
        val ONLY_WHEN_WORN = booleanPreferencesKey("only_when_worn")
        val DAILY_GOAL = intPreferencesKey("daily_goal")

        /** Wochenplan im Format von [ScheduleCodec]. Ist der Schlüssel gesetzt, gilt der Plan als übernommen. */
        val WEEKLY_SCHEDULE = stringPreferencesKey("weekly_schedule_v1")

        /** Vor dem Wochenplan: ein Fenster in vollen Stunden für alle Tage. Wird nur noch zum Übernehmen gelesen. */
        val LEGACY_START_HOUR = intPreferencesKey("active_start_hour")
        val LEGACY_END_HOUR = intPreferencesKey("active_end_hour")

        val NEXT_ALARM_AT = longPreferencesKey("next_alarm_at")
        val NEXT_ALARM_KIND = stringPreferencesKey("next_alarm_kind")
        val CYCLE_START_AT = longPreferencesKey("cycle_start_at")
        val SNOOZE_UNTIL = longPreferencesKey("snooze_until")
        val PENDING_REMINDER_AT = longPreferencesKey("pending_reminder_at")
        val LAST_MOVEMENT_AT = longPreferencesKey("last_movement_at")
        val LAST_WALK_DETECTED_AT = longPreferencesKey("last_walk_detected_at")
        val STEP_TRACKING_ACTIVE = booleanPreferencesKey("step_tracking_active")
        val LAST_SKIPPED_AT = longPreferencesKey("last_skipped_at")
        val LAST_SKIP_REASON = stringPreferencesKey("last_skip_reason")
    }

    private fun Preferences.toSettings(): ReminderSettings = ReminderSettings(
        enabled = this[Keys.ENABLED] ?: false,
        intervalMinutes = this[Keys.INTERVAL_MINUTES] ?: ReminderSettings.DEFAULT_INTERVAL_MINUTES,
        schedule = toSchedule(),
        autoStepEnabled = this[Keys.AUTO_STEP_ENABLED] ?: true,
        permissionsRequested = this[Keys.PERMISSIONS_REQUESTED] ?: false,
        pausedUntil = this[Keys.PAUSED_UNTIL] ?: 0L,
        snoozeMinutes = this[Keys.SNOOZE_MINUTES] ?: ReminderSettings.DEFAULT_SNOOZE_MINUTES,
        stepThreshold = this[Keys.STEP_THRESHOLD] ?: ReminderSettings.DEFAULT_STEP_THRESHOLD,
        vibration = VibrationLevel.fromName(this[Keys.VIBRATION]) ?: VibrationLevel.STRONG,
        respectQuietModes = this[Keys.RESPECT_QUIET_MODES] ?: true,
        onlyWhenWorn = this[Keys.ONLY_WHEN_WORN] ?: true,
        dailyGoal = this[Keys.DAILY_GOAL] ?: ReminderSettings.DEFAULT_DAILY_GOAL,
    ).sanitized()

    private fun MutablePreferences.writeSettings(settings: ReminderSettings) {
        this[Keys.ENABLED] = settings.enabled
        this[Keys.INTERVAL_MINUTES] = settings.intervalMinutes
        this[Keys.WEEKLY_SCHEDULE] = ScheduleCodec.encode(settings.schedule)
        // Mit dem ersten Schreiben ist der alte Einheitszeitraum in den Wochenplan übernommen.
        remove(Keys.LEGACY_START_HOUR)
        remove(Keys.LEGACY_END_HOUR)
        this[Keys.AUTO_STEP_ENABLED] = settings.autoStepEnabled
        this[Keys.PERMISSIONS_REQUESTED] = settings.permissionsRequested
        this[Keys.PAUSED_UNTIL] = settings.pausedUntil
        this[Keys.SNOOZE_MINUTES] = settings.snoozeMinutes
        this[Keys.STEP_THRESHOLD] = settings.stepThreshold
        this[Keys.VIBRATION] = settings.vibration.name
        this[Keys.RESPECT_QUIET_MODES] = settings.respectQuietModes
        this[Keys.ONLY_WHEN_WORN] = settings.onlyWhenWorn
        this[Keys.DAILY_GOAL] = settings.dailyGoal
    }

    /**
     * Wochenplan: gespeicherter Plan; sonst (Installation von vor dem Wochenplan) das alte Fenster für alle
     * Tage, damit sich nach dem Update nichts ändert; sonst (neue Installation) [WeeklySchedule.DEFAULT].
     *
     * Die alten Stunden-Schlüssel sind nur Quelle der Migration. Ein Downgrade auf eine Version ohne Wochenplan
     * wird nicht unterstützt: Sie fände nur ihre Standardzeiten, und was sie speichert, ignoriert diese Version.
     */
    private fun Preferences.toSchedule(): WeeklySchedule =
        this[Keys.WEEKLY_SCHEDULE]?.let(ScheduleCodec::decode) ?: legacySchedule() ?: WeeklySchedule.DEFAULT

    private fun Preferences.legacySchedule(): WeeklySchedule? {
        val startHour = this[Keys.LEGACY_START_HOUR]
        val endHour = this[Keys.LEGACY_END_HOUR]
        if (startHour == null && endHour == null) return null
        // Bereinigung wie in der alten Version: Start 0–23, Ende nach dem Start, höchstens 24.
        val start = (startHour ?: LEGACY_DEFAULT_START_HOUR).coerceIn(0, 23)
        val end = (endHour ?: LEGACY_DEFAULT_END_HOUR).coerceIn(start + 1, 24)
        return WeeklySchedule.everyDay(ActiveWindow.ofHours(start, end))
    }

    private fun Preferences.toState(): ReminderState = ReminderState(
        nextAlarmAt = this[Keys.NEXT_ALARM_AT] ?: 0L,
        nextAlarmKind = AlarmKind.fromName(this[Keys.NEXT_ALARM_KIND]),
        cycleStartAt = this[Keys.CYCLE_START_AT] ?: 0L,
        snoozeUntil = this[Keys.SNOOZE_UNTIL] ?: 0L,
        pendingReminderAt = this[Keys.PENDING_REMINDER_AT] ?: 0L,
        lastMovementAt = this[Keys.LAST_MOVEMENT_AT] ?: 0L,
        lastWalkDetectedAt = this[Keys.LAST_WALK_DETECTED_AT] ?: 0L,
        stepTrackingActive = this[Keys.STEP_TRACKING_ACTIVE] ?: false,
        lastSkippedAt = this[Keys.LAST_SKIPPED_AT] ?: 0L,
        lastSkipReason = SkipReason.fromName(this[Keys.LAST_SKIP_REASON]),
    )

    private fun MutablePreferences.writeState(state: ReminderState) {
        this[Keys.NEXT_ALARM_AT] = state.nextAlarmAt
        val kind = state.nextAlarmKind
        if (kind != null) this[Keys.NEXT_ALARM_KIND] = kind.name else remove(Keys.NEXT_ALARM_KIND)
        this[Keys.CYCLE_START_AT] = state.cycleStartAt
        this[Keys.SNOOZE_UNTIL] = state.snoozeUntil
        this[Keys.PENDING_REMINDER_AT] = state.pendingReminderAt
        this[Keys.LAST_MOVEMENT_AT] = state.lastMovementAt
        this[Keys.LAST_WALK_DETECTED_AT] = state.lastWalkDetectedAt
        this[Keys.STEP_TRACKING_ACTIVE] = state.stepTrackingActive
        this[Keys.LAST_SKIPPED_AT] = state.lastSkippedAt
        val skipReason = state.lastSkipReason
        if (skipReason != null) this[Keys.LAST_SKIP_REASON] = skipReason.name else remove(Keys.LAST_SKIP_REASON)
    }

    private companion object {
        const val TAG = "SettingsRepository"
        const val LEGACY_DEFAULT_START_HOUR = 8
        const val LEGACY_DEFAULT_END_HOUR = 17
    }
}
