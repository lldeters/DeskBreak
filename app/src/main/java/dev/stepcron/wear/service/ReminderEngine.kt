package dev.stepcron.wear.service

import android.content.Context
import android.util.Log
import dev.stepcron.wear.data.MovementRepository
import dev.stepcron.wear.data.db.MovementEventType
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.SettingsRepository
import dev.stepcron.wear.domain.AlarmKind
import dev.stepcron.wear.domain.PlannedAlarm
import dev.stepcron.wear.domain.ReminderPlanner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Zentrale Zustandsmaschine des Bewegungs-Timers.
 *
 * Alle Einstiegspunkte (Alarm, Notification-Aktionen, Schritterkennung, UI, Boot) laufen hier
 * zusammen und werden über einen [Mutex] serialisiert. Es ist immer genau ein Alarm geplant:
 * - [AlarmKind.REMINDER]: Erinnerung innerhalb der aktiven Zeit
 * - [AlarmKind.WINDOW_END]: Ende der aktiven Zeit (Schritterkennung stoppen)
 * - [AlarmKind.CYCLE_START]: Beginn der nächsten aktiven Zeit (Zyklus startet neu)
 *
 * Alle Einstiegspunkte dürfen vom Main-Thread aufgerufen werden: Die Arbeit (Binder-Aufrufe an
 * AlarmManager und ActivityManager, DataStore, Room) läuft auf [dispatcher], damit sie keine Frames kostet.
 */
class ReminderEngine(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val movementRepository: MovementRepository,
    private val alarmScheduler: AlarmScheduler,
    private val notifier: ReminderNotifier,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutex = Mutex()

    /** Serialisiert einen Einstiegspunkt über [mutex] und führt ihn abseits des Main-Threads aus. */
    private suspend fun locked(block: suspend () -> Unit) {
        withContext(dispatcher) { mutex.withLock { block() } }
    }

    // ---------------------------------------------------------------------------------------------
    // Alarm
    // ---------------------------------------------------------------------------------------------

    suspend fun onAlarmFired(kind: AlarmKind?): Unit = locked {
        val now = clock()
        val settings = settingsRepository.currentSettings()
        if (!settings.enabled) {
            cancelScheduleLocked()
            syncStepTrackerLocked(now, settings)
            return@locked
        }
        when (kind ?: settingsRepository.currentState().nextAlarmKind ?: AlarmKind.REMINDER) {
            AlarmKind.REMINDER -> handleReminderLocked(now, settings)
            AlarmKind.CYCLE_START -> handleCycleStartLocked(now, settings)
            AlarmKind.WINDOW_END -> handleWindowEndLocked(now, settings)
        }
    }

    private suspend fun handleReminderLocked(now: Long, settings: ReminderSettings) {
        val zone = zoneProvider()
        if (!settings.window.contains(now, zone)) {
            // Ruhezeit (z. B. weil das Fenster geändert wurde): direkt den nächsten Fensterbeginn planen.
            scheduleLocked(ReminderPlanner.planOutsideWindow(now, settings.window, zone))
            syncStepTrackerLocked(now, settings)
            return
        }
        val previous = settingsRepository.currentState()

        // 1) Sofort den nächsten Alarm planen: jetzt + X Minuten (bzw. Fensterende).
        settingsRepository.updateState { it.copy(cycleStartAt = now, snoozeUntil = 0L) }
        scheduleLocked(ReminderPlanner.planInWindow(now + settings.intervalMillis, now, settings.window, zone))

        // 2) War die vorherige Erinnerung noch unbestätigt, gilt sie als verpasst.
        if (previous.pendingReminderAt > 0L) {
            movementRepository.log(MovementEventType.MISSED, previous.pendingReminderAt, settings.intervalMinutes)
        }

        // 3) Vibrationsmuster und 4) High-Priority-Notification.
        notifier.vibrateReminder()
        notifier.showReminder()
        settingsRepository.updateState { it.copy(pendingReminderAt = now) }

        // Schritterkennung sicherstellen (exakte Alarme dürfen Foreground-Services starten).
        syncStepTrackerLocked(now, settings)
    }

    private suspend fun handleCycleStartLocked(now: Long, settings: ReminderSettings) {
        val zone = zoneProvider()
        if (!settings.window.contains(now, zone)) {
            scheduleLocked(ReminderPlanner.planOutsideWindow(now, settings.window, zone))
            syncStepTrackerLocked(now, settings)
            return
        }
        // Erinnerung vom Vortag, die nie beantwortet wurde, endgültig als verpasst werten.
        val state = settingsRepository.currentState()
        if (state.pendingReminderAt > 0L) {
            movementRepository.log(MovementEventType.MISSED, state.pendingReminderAt, settings.intervalMinutes)
            notifier.cancelReminder()
            settingsRepository.updateState { it.copy(pendingReminderAt = 0L) }
        }
        startCycleLocked(now, settings)
        syncStepTrackerLocked(now, settings)
    }

    private suspend fun handleWindowEndLocked(now: Long, settings: ReminderSettings) {
        val zone = zoneProvider()
        if (settings.window.contains(now, zone)) {
            // Fenster wurde inzwischen verlängert: regulär weiterplanen.
            resyncLocked(now, settings)
            return
        }
        val state = settingsRepository.currentState()
        if (state.pendingReminderAt > 0L && now - state.pendingReminderAt >= WINDOW_END_GRACE_MILLIS) {
            movementRepository.log(MovementEventType.MISSED, state.pendingReminderAt, settings.intervalMinutes)
            notifier.cancelReminder()
            settingsRepository.updateState { it.copy(pendingReminderAt = 0L) }
        }
        settingsRepository.updateState { it.copy(snoozeUntil = 0L) }
        scheduleLocked(ReminderPlanner.planOutsideWindow(now, settings.window, zone))
        syncStepTrackerLocked(now, settings)
    }

    // ---------------------------------------------------------------------------------------------
    // Notification-Aktionen
    // ---------------------------------------------------------------------------------------------

    /** „Erledigt": Notification schließen und Erfolg loggen. Der Takt (jetzt + X) läuft weiter. */
    suspend fun onReminderDone(): Unit = locked {
        notifier.cancelReminder()
        val now = clock()
        val settings = settingsRepository.currentSettings()
        val state = settingsRepository.currentState()
        if (state.pendingReminderAt == 0L) return@locked // bereits erledigt, z. B. per Schritterkennung
        movementRepository.log(MovementEventType.COMPLETED_REMINDER, now, settings.intervalMinutes)
        settingsRepository.updateState { it.copy(pendingReminderAt = 0L, lastMovementAt = now) }
    }

    /** „+10 Min Snooze": Snooze loggen und den nächsten Alarm einmalig auf jetzt + 10 Minuten legen. */
    suspend fun onReminderSnoozed(): Unit = locked {
        notifier.cancelReminder()
        val now = clock()
        val settings = settingsRepository.currentSettings()
        val state = settingsRepository.currentState()
        if (state.pendingReminderAt == 0L) return@locked
        movementRepository.log(MovementEventType.SNOOZED, now, settings.intervalMinutes)
        settingsRepository.updateState { it.copy(pendingReminderAt = 0L) }
        if (!settings.enabled) return@locked

        val zone = zoneProvider()
        if (settings.window.contains(now, zone)) {
            val snoozeAt = now + SNOOZE_MILLIS
            val plan = ReminderPlanner.planInWindow(snoozeAt, now, settings.window, zone)
            val snoozeUntil = if (plan.kind == AlarmKind.REMINDER) snoozeAt else 0L
            settingsRepository.updateState { it.copy(snoozeUntil = snoozeUntil) }
            scheduleLocked(plan)
        } else {
            scheduleLocked(ReminderPlanner.planOutsideWindow(now, settings.window, zone))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Bewegung
    // ---------------------------------------------------------------------------------------------

    /** „Jetzt bewegt (Timer Reset)": Intervall sofort neu starten und positives Ereignis loggen. */
    suspend fun onManualReset(): Unit = locked {
        val now = clock()
        val settings = settingsRepository.currentSettings()
        val state = settingsRepository.currentState()
        val hadPending = state.pendingReminderAt > 0L
        if (hadPending) notifier.cancelReminder()
        // Doppeltipps innerhalb einer Minute nicht doppelt zählen (der Timer wird trotzdem zurückgesetzt).
        if (hadPending || now - state.lastMovementAt >= MANUAL_DEBOUNCE_MILLIS) {
            movementRepository.log(MovementEventType.MANUAL_RESET, now, settings.intervalMinutes)
        }
        settingsRepository.updateState { it.copy(pendingReminderAt = 0L, lastMovementAt = now) }
        if (settings.enabled && settings.window.contains(now, zoneProvider())) {
            startCycleLocked(now, settings)
        }
    }

    /**
     * Schritterkennung: ≥ 30 Schritte in 2 Minuten. Setzt den Timer zurück, schließt eine offene
     * Erinnerung und loggt [MovementEventType.AUTO_STEP_RESET]. Geht der Nutzer am Stück weiter,
     * wird der Timer nur still verlängert, damit eine lange Gehpause nicht mehrfach zählt.
     */
    suspend fun onWalkDetected(): Unit = locked {
        val now = clock()
        val settings = settingsRepository.currentSettings()
        if (!settings.enabled || !settings.autoStepEnabled || !settings.window.contains(now, zoneProvider())) {
            return@locked
        }
        val state = settingsRepository.currentState()
        val lastActivity = maxOf(state.lastWalkDetectedAt, state.lastMovementAt)
        val sameSession = now - lastActivity < WALK_SESSION_GAP_MILLIS
        settingsRepository.updateState { it.copy(lastWalkDetectedAt = now) }

        when {
            state.pendingReminderAt > 0L -> {
                notifier.cancelReminder()
                movementRepository.log(MovementEventType.AUTO_STEP_RESET, now, settings.intervalMinutes)
                settingsRepository.updateState { it.copy(pendingReminderAt = 0L, lastMovementAt = now) }
                startCycleLocked(now, settings)
            }

            sameSession -> {
                val extendsReminder = state.nextAlarmKind == AlarmKind.REMINDER &&
                    now + settings.intervalMillis - state.nextAlarmAt >= MIN_EXTENSION_MILLIS
                if (extendsReminder) startCycleLocked(now, settings)
            }

            else -> {
                movementRepository.log(MovementEventType.AUTO_STEP_RESET, now, settings.intervalMinutes)
                settingsRepository.updateState { it.copy(lastMovementAt = now) }
                startCycleLocked(now, settings)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Einstellungen & Synchronisation
    // ---------------------------------------------------------------------------------------------

    suspend fun setEnabled(enabled: Boolean): Unit = locked {
        val now = clock()
        val settings = settingsRepository.updateSettings { it.copy(enabled = enabled) }
        if (enabled) {
            settingsRepository.updateState {
                it.copy(cycleStartAt = now, snoozeUntil = 0L, pendingReminderAt = 0L)
            }
            resyncLocked(now, settings)
        } else {
            cancelScheduleLocked()
            notifier.cancelReminder()
            settingsRepository.updateState {
                it.copy(cycleStartAt = 0L, snoozeUntil = 0L, pendingReminderAt = 0L)
            }
            syncStepTrackerLocked(now, settings)
        }
    }

    suspend fun setIntervalMinutes(minutes: Int): Unit = locked {
        val settings = settingsRepository.updateSettings { it.copy(intervalMinutes = minutes) }
        resyncLocked(clock(), settings)
    }

    suspend fun setActiveHours(startHour: Int, endHour: Int): Unit = locked {
        val settings = settingsRepository.updateSettings {
            it.copy(activeStartHour = startHour, activeEndHour = endHour)
        }
        resyncLocked(clock(), settings)
    }

    suspend fun setAutoStepEnabled(enabled: Boolean): Unit = locked {
        val settings = settingsRepository.updateSettings { it.copy(autoStepEnabled = enabled) }
        syncStepTrackerLocked(clock(), settings)
    }

    /** Gleicht Alarm und Schritterkennung mit dem gespeicherten Zustand ab (App-Start, Zeitänderung …). */
    suspend fun resync(): Unit = locked {
        resyncLocked(clock(), settingsRepository.currentSettings())
    }

    /** Nach Neustart/Update: Alarm aus dem DataStore wiederherstellen. */
    suspend fun restoreAfterBoot(): Unit = locked {
        val now = clock()
        val settings = settingsRepository.currentSettings()
        if (!settings.enabled) {
            syncStepTrackerLocked(now, settings)
            return@locked
        }
        val state = settingsRepository.currentState()
        val kind = state.nextAlarmKind
        if (kind != null && state.nextAlarmAt > now) {
            alarmScheduler.schedule(PlannedAlarm(state.nextAlarmAt, kind))
            syncStepTrackerLocked(now, settings)
        } else {
            // Der Alarm wäre während der Ausschaltzeit fällig gewesen → frischen Zyklus beginnen.
            if (settings.window.contains(now, zoneProvider())) {
                settingsRepository.updateState { it.copy(cycleStartAt = now, snoozeUntil = 0L) }
            }
            resyncLocked(now, settings)
        }
    }

    private suspend fun resyncLocked(now: Long, settings: ReminderSettings) {
        if (!settings.enabled) {
            cancelScheduleLocked()
            syncStepTrackerLocked(now, settings)
            return
        }
        val zone = zoneProvider()
        if (settings.window.contains(now, zone)) {
            val state = settingsRepository.currentState()
            val periodStart = settings.window.startOfDay(now, zone)
            if (state.cycleStartAt !in periodStart..now) {
                // Kein laufender Zyklus in der heutigen aktiven Zeit → jetzt starten.
                settingsRepository.updateState { it.copy(cycleStartAt = now, snoozeUntil = 0L) }
            }
        }
        val state = settingsRepository.currentState()
        scheduleLocked(
            ReminderPlanner.planNext(
                nowMillis = now,
                cycleStartMillis = state.cycleStartAt,
                snoozeUntilMillis = state.snoozeUntil,
                intervalMillis = settings.intervalMillis,
                window = settings.window,
                zone = zone,
            ),
        )
        syncStepTrackerLocked(now, settings)
    }

    private suspend fun startCycleLocked(now: Long, settings: ReminderSettings) {
        settingsRepository.updateState { it.copy(cycleStartAt = now, snoozeUntil = 0L) }
        scheduleLocked(
            ReminderPlanner.planInWindow(now + settings.intervalMillis, now, settings.window, zoneProvider()),
        )
    }

    private suspend fun scheduleLocked(alarm: PlannedAlarm) {
        val exact = alarmScheduler.schedule(alarm)
        if (!exact) Log.i(TAG, "Alarm ${alarm.kind} nur ungenau geplant")
        settingsRepository.updateState {
            it.copy(nextAlarmAt = alarm.triggerAtMillis, nextAlarmKind = alarm.kind)
        }
    }

    private suspend fun cancelScheduleLocked() {
        alarmScheduler.cancel()
        settingsRepository.updateState { it.copy(nextAlarmAt = 0L, nextAlarmKind = null) }
    }

    /** Startet bzw. stoppt die Schritterkennung passend zu Einstellungen, Uhrzeit und Berechtigung. */
    private suspend fun syncStepTrackerLocked(now: Long, settings: ReminderSettings) {
        if (StepTrackerService.shouldRun(context, settings, now, zoneProvider())) {
            StepTrackerService.start(context)
        } else {
            StepTrackerService.stop(context)
            settingsRepository.updateState { it.copy(stepTrackingActive = false) }
        }
    }

    companion object {
        private const val TAG = "ReminderEngine"

        const val SNOOZE_MILLIS = 10 * 60_000L

        /** Erinnerungen, die kurz vor Feierabend kamen, bleiben bis zum nächsten Morgen beantwortbar. */
        const val WINDOW_END_GRACE_MILLIS = 15 * 60_000L

        /** Mehrfaches Tippen auf „Jetzt bewegt" innerhalb dieser Zeit wird nur einmal gezählt. */
        const val MANUAL_DEBOUNCE_MILLIS = 60_000L

        /** Gehpausen mit weniger Abstand gelten als eine zusammenhängende Bewegungspause. */
        const val WALK_SESSION_GAP_MILLIS = 5 * 60_000L

        /** Stilles Verlängern nur, wenn sich der Alarm dadurch spürbar verschiebt. */
        const val MIN_EXTENSION_MILLIS = 60_000L
    }
}
