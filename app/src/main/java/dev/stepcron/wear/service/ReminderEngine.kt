package dev.stepcron.wear.service

import android.util.Log
import dev.stepcron.wear.data.MovementLog
import dev.stepcron.wear.data.db.MovementEventType
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.SettingsRepository
import dev.stepcron.wear.data.settings.SkipReason
import dev.stepcron.wear.data.settings.VibrationLevel
import dev.stepcron.wear.domain.AlarmKind
import dev.stepcron.wear.domain.PlannedAlarm
import dev.stepcron.wear.domain.ReminderPlanner
import dev.stepcron.wear.domain.ScheduleChange
import dev.stepcron.wear.domain.WeeklySchedule
import dev.stepcron.wear.domain.toLocalDate
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
 * zusammen und werden über einen [Mutex] serialisiert. Es ist höchstens ein Alarm geplant (keiner, wenn an
 * keinem Tag erinnert wird und keine Erinnerung offen ist):
 * - [AlarmKind.REMINDER]: Erinnerung innerhalb der aktiven Zeit
 * - [AlarmKind.WINDOW_END]: Ende der aktiven Zeit (Schritterkennung stoppen) – oder, wenn freie Tage folgen,
 *   der Abschluss einer offenen Erinnerung nach der Kulanzzeit
 * - [AlarmKind.CYCLE_START]: Beginn der nächsten aktiven Zeit (Zyklus startet neu)
 *
 * Alle Einstiegspunkte dürfen vom Main-Thread aufgerufen werden: Die Arbeit (Binder-Aufrufe an
 * AlarmManager und ActivityManager, DataStore, Room) läuft auf [dispatcher], damit sie keine Frames kostet.
 *
 * Ohne gültige Lizenz ([licenseCheck]: Testphase vorbei, nicht freigeschaltet) gelten Erinnerungen als ausgeschaltet –
 * kein Alarm, keine Erinnerung, keine Schritterkennung. Die gespeicherte Einstellung bleibt, wie sie ist; nach dem
 * Freischalten genügt ein [resync], und es geht weiter. Verlauf und „Jetzt bewegt" bleiben nutzbar.
 */
class ReminderEngine(
    private val settingsRepository: SettingsRepository,
    private val movementLog: MovementLog,
    private val alarms: Alarms,
    private val alerts: ReminderAlerts,
    private val stepTracking: StepTracking,
    private val deviceState: DeviceState,
    private val licenseCheck: LicenseCheck,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Nach jedem Einstiegspunkt aufgerufen, z. B. um Kachel und Komplikation zu aktualisieren. */
    private val onStateChanged: () -> Unit = {},
) {
    private val mutex = Mutex()

    /**
     * Serialisiert einen Einstiegspunkt über [mutex] und führt ihn abseits des Main-Threads aus.
     * Danach werden Anzeigen außerhalb der App (Kachel, Komplikation) benachrichtigt.
     */
    private suspend fun locked(block: suspend () -> Unit) {
        withContext(dispatcher) {
            mutex.withLock { block() }
            onStateChanged()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Alarm
    // ---------------------------------------------------------------------------------------------

    suspend fun onAlarmFired(kind: AlarmKind?): Unit = locked {
        val now = clock()
        var settings = currentSettingsLocked(now)
        if (!settings.enabled) {
            stopLocked(now, settings)
            return@locked
        }
        if (settings.isPausedAt(now)) {
            resyncLocked(now, settings)
            return@locked
        }
        if (settings.pausedUntil != 0L) settings = settingsRepository.updateSettings { it.copy(pausedUntil = 0L) }
        when (kind ?: settingsRepository.currentState().nextAlarmKind ?: AlarmKind.REMINDER) {
            AlarmKind.REMINDER -> handleReminderLocked(now, settings)
            AlarmKind.CYCLE_START -> handleCycleStartLocked(now, settings)
            AlarmKind.WINDOW_END -> handleWindowEndLocked(now, settings)
        }
    }

    private suspend fun handleReminderLocked(now: Long, settings: ReminderSettings) {
        val zone = zoneProvider()
        val previous = settingsRepository.currentState()
        if (!settings.schedule.contains(now, zone)) {
            // Ruhezeit (z. B. weil der Zeitplan geändert wurde): direkt den nächsten Fensterbeginn planen.
            scheduleLocked(
                ReminderPlanner.planOutsideWindow(now, settings.schedule, zone, previous.pendingReminderAt),
            )
            syncStepTrackerLocked(now, settings)
            return
        }

        // 1) Sofort den nächsten Alarm planen: jetzt + X Minuten (bzw. Fensterende).
        settingsRepository.updateState { it.copy(cycleStartAt = now, snoozeUntil = 0L) }
        scheduleLocked(ReminderPlanner.planInWindow(now + settings.intervalMillis, now, settings.schedule, zone))

        // 2) War die vorherige Erinnerung noch unbestätigt, gilt sie als verpasst.
        if (previous.pendingReminderAt > 0L) {
            movementLog.log(MovementEventType.MISSED, previous.pendingReminderAt, settings.intervalMinutes)
        }

        // Ruhemodus oder abgelegt: diese Erinnerung still überspringen – sie zählt weder als offen noch als verpasst,
        // der Takt läuft ab jetzt weiter. Eine noch sichtbare vorherige Erinnerung (oben als verpasst gezählt) geht.
        val skipReason = skipReasonLocked(settings)
        if (skipReason != null) {
            alerts.cancelReminder()
            settingsRepository.updateState {
                it.copy(pendingReminderAt = 0L, lastSkippedAt = now, lastSkipReason = skipReason)
            }
            syncStepTrackerLocked(now, settings)
            return
        }

        // 3) Vibrationsmuster und 4) High-Priority-Notification.
        alerts.vibrateReminder(settings.vibration)
        alerts.showReminder(now, settings.snoozeMinutes)
        settingsRepository.updateState { it.copy(pendingReminderAt = now) }

        // Schritterkennung sicherstellen (exakte Alarme dürfen Foreground-Services starten).
        syncStepTrackerLocked(now, settings)
    }

    private suspend fun handleCycleStartLocked(now: Long, settings: ReminderSettings) {
        val zone = zoneProvider()
        val state = settingsRepository.currentState()
        if (!settings.schedule.contains(now, zone)) {
            scheduleLocked(ReminderPlanner.planOutsideWindow(now, settings.schedule, zone, state.pendingReminderAt))
            syncStepTrackerLocked(now, settings)
            return
        }
        // Erinnerung vom Vortag, die nie beantwortet wurde, endgültig als verpasst werten.
        if (state.pendingReminderAt > 0L) missPendingReminderLocked(state.pendingReminderAt, settings)
        startCycleLocked(now, settings)
        syncStepTrackerLocked(now, settings)
    }

    private suspend fun handleWindowEndLocked(now: Long, settings: ReminderSettings) {
        val zone = zoneProvider()
        if (settings.schedule.contains(now, zone)) {
            // Fenster wurde inzwischen verlängert: regulär weiterplanen.
            resyncLocked(now, settings)
            return
        }
        var pendingReminderAt = settingsRepository.currentState().pendingReminderAt
        if (pendingReminderAt > 0L && now - pendingReminderAt >= ReminderPlanner.WINDOW_END_GRACE_MILLIS) {
            missPendingReminderLocked(pendingReminderAt, settings)
            pendingReminderAt = 0L
        }
        settingsRepository.updateState { it.copy(snoozeUntil = 0L) }
        // Eine jüngere offene Erinnerung bleibt bis zum nächsten Morgen beantwortbar – folgen freie Tage,
        // plant der Planer stattdessen ihren Abschluss nach der Kulanzzeit.
        scheduleLocked(ReminderPlanner.planOutsideWindow(now, settings.schedule, zone, pendingReminderAt))
        syncStepTrackerLocked(now, settings)
    }

    /** Warum die fällige Erinnerung ausfallen soll, nach den Einstellungen; `null`: erinnern. */
    private suspend fun skipReasonLocked(settings: ReminderSettings): SkipReason? {
        if (settings.respectQuietModes) deviceState.quietMode()?.let { return it }
        // Lässt sich das Tragen nicht feststellen (kein Sensor), wird erinnert.
        if (settings.onlyWhenWorn && deviceState.isWorn() == false) return SkipReason.NOT_WORN
        return null
    }

    /** Die offene Erinnerung von [pendingReminderAt] endgültig als verpasst werten und schließen. */
    private suspend fun missPendingReminderLocked(pendingReminderAt: Long, settings: ReminderSettings) {
        movementLog.log(MovementEventType.MISSED, pendingReminderAt, settings.intervalMinutes)
        alerts.cancelReminder()
        settingsRepository.updateState { it.copy(pendingReminderAt = 0L) }
    }

    // ---------------------------------------------------------------------------------------------
    // Notification-Aktionen
    // ---------------------------------------------------------------------------------------------

    /** „Erledigt": Notification schließen und Erfolg loggen. Der Takt (jetzt + X) läuft weiter. */
    suspend fun onReminderDone(): Unit = locked { completeReminderLocked() }

    private suspend fun completeReminderLocked() {
        alerts.cancelReminder()
        val now = clock()
        val settings = settingsRepository.currentSettings()
        val state = settingsRepository.currentState()
        if (state.pendingReminderAt == 0L) return // bereits erledigt, z. B. per Schritterkennung
        movementLog.log(MovementEventType.COMPLETED_REMINDER, now, settings.intervalMinutes)
        settingsRepository.updateState { it.copy(pendingReminderAt = 0L, lastMovementAt = now) }
    }

    /** Snooze: loggen und den nächsten Alarm einmalig auf jetzt + Snooze-Dauer (Einstellung) legen. */
    suspend fun onReminderSnoozed(): Unit = locked {
        alerts.cancelReminder()
        val now = clock()
        val settings = currentSettingsLocked(now)
        val state = settingsRepository.currentState()
        if (state.pendingReminderAt == 0L) return@locked
        movementLog.log(MovementEventType.SNOOZED, now, settings.intervalMinutes)
        settingsRepository.updateState { it.copy(pendingReminderAt = 0L) }
        if (!settings.enabled) return@locked

        val zone = zoneProvider()
        if (settings.schedule.contains(now, zone)) {
            val snoozeAt = now + settings.snoozeMillis
            val plan = ReminderPlanner.planInWindow(snoozeAt, now, settings.schedule, zone)
            val snoozeUntil = if (plan?.kind == AlarmKind.REMINDER) snoozeAt else 0L
            settingsRepository.updateState { it.copy(snoozeUntil = snoozeUntil) }
            scheduleLocked(plan)
        } else {
            // Die Erinnerung ist mit dem Snooze beantwortet; offen ist nichts mehr.
            scheduleLocked(ReminderPlanner.planOutsideWindow(now, settings.schedule, zone, pendingReminderAt = 0L))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Bewegung
    // ---------------------------------------------------------------------------------------------

    /** „Jetzt bewegt (Timer Reset)": Intervall sofort neu starten und positives Ereignis loggen. */
    suspend fun onManualReset(): Unit = locked { manualResetLocked() }

    /** 1-Minuten-Übung beendet: beantwortet eine offene Erinnerung, sonst zählt sie wie „Jetzt bewegt". */
    suspend fun onExerciseCompleted(): Unit = locked {
        if (settingsRepository.currentState().pendingReminderAt > 0L) completeReminderLocked() else manualResetLocked()
    }

    private suspend fun manualResetLocked() {
        val now = clock()
        val settings = currentSettingsLocked(now)
        val state = settingsRepository.currentState()
        val hadPending = state.pendingReminderAt > 0L
        if (hadPending) alerts.cancelReminder()
        // Doppeltipps innerhalb einer Minute nicht doppelt zählen (der Timer wird trotzdem zurückgesetzt).
        if (hadPending || now - state.lastMovementAt >= MANUAL_DEBOUNCE_MILLIS) {
            movementLog.log(MovementEventType.MANUAL_RESET, now, settings.intervalMinutes)
        }
        settingsRepository.updateState { it.copy(pendingReminderAt = 0L, lastMovementAt = now) }
        if (settings.enabled && !settings.isPausedAt(now) && settings.schedule.contains(now, zoneProvider())) {
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
        val settings = currentSettingsLocked(now)
        if (!settings.wantsStepTracking(now, zoneProvider())) return@locked
        val state = settingsRepository.currentState()
        val lastActivity = maxOf(state.lastWalkDetectedAt, state.lastMovementAt)
        val sameSession = now - lastActivity < WALK_SESSION_GAP_MILLIS
        settingsRepository.updateState { it.copy(lastWalkDetectedAt = now) }

        when {
            state.pendingReminderAt > 0L -> {
                alerts.cancelReminder()
                movementLog.log(MovementEventType.AUTO_STEP_RESET, now, settings.intervalMinutes)
                settingsRepository.updateState { it.copy(pendingReminderAt = 0L, lastMovementAt = now) }
                startCycleLocked(now, settings)
            }

            sameSession -> {
                val extendsReminder = state.nextAlarmKind == AlarmKind.REMINDER &&
                    now + settings.intervalMillis - state.nextAlarmAt >= MIN_EXTENSION_MILLIS
                if (extendsReminder) startCycleLocked(now, settings)
            }

            else -> {
                movementLog.log(MovementEventType.AUTO_STEP_RESET, now, settings.intervalMinutes)
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
        // Eine Pause gehört zu eingeschalteten Erinnerungen: Aus- und wieder Einschalten startet ohne sie.
        val settings = updateSettingsLocked(now) { it.copy(enabled = enabled, pausedUntil = 0L) }
        if (enabled) {
            settingsRepository.updateState {
                // Den Zyklus startet der Abgleich: im Fenster jetzt, sonst am nächsten Fensterbeginn.
                it.copy(cycleStartAt = 0L, snoozeUntil = 0L, pendingReminderAt = 0L)
            }
            resyncLocked(now, settings)
        } else {
            cancelScheduleLocked()
            alerts.cancelReminder()
            settingsRepository.updateState {
                it.copy(cycleStartAt = 0L, snoozeUntil = 0L, pendingReminderAt = 0L)
            }
            syncStepTrackerLocked(now, settings)
        }
    }

    suspend fun setIntervalMinutes(minutes: Int): Unit = locked {
        val now = clock()
        val settings = updateSettingsLocked(now) { it.copy(intervalMinutes = minutes) }
        resyncLocked(now, settings)
    }

    /**
     * Ändert den Wochenplan atomar ([transform] läuft im Lese-Ändere-Schreibe-Schritt des DataStores) und
     * gleicht den Timer ab. Endet oder beginnt das heutige Fenster dadurch jetzt, wird das wie ein
     * Fensterende bzw. -beginn behandelt.
     */
    suspend fun updateSchedule(transform: (WeeklySchedule) -> WeeklySchedule): Unit = locked {
        val now = clock()
        val zone = zoneProvider()
        // Konsistent, weil alle Änderungen am Wochenplan durch diesen Mutex laufen.
        val before = settingsRepository.currentSettings().schedule
        val settings = updateSettingsLocked(now) { it.copy(schedule = transform(it.schedule)) }
        if (!settings.enabled || settings.isPausedAt(now)) {
            // Ausgeschaltet (auch ohne Lizenz) bzw. pausiert: Übergänge des heutigen Fensters spielen keine Rolle, nach
            // einer Pause beginnt ohnehin ein frischer Zyklus.
            resyncLocked(now, settings)
            return@locked
        }
        val today = now.toLocalDate(zone)
        val pendingReminderAt = settingsRepository.currentState().pendingReminderAt
        when (ReminderPlanner.scheduleChange(before, settings.schedule, now, zone)) {
            ScheduleChange.TODAY_OFF -> {
                // Heute frei gestellt (auch nach Feierabend): Eine heutige offene Erinnerung schließen, ohne sie
                // als verpasst zu werten – wie beim Ausschalten. Eine vom Vortag läuft regulär aus.
                if (pendingReminderAt > 0L && pendingReminderAt.toLocalDate(zone) == today) {
                    alerts.cancelReminder()
                    settingsRepository.updateState { it.copy(pendingReminderAt = 0L) }
                }
                settingsRepository.updateState { it.copy(snoozeUntil = 0L) }
            }

            ScheduleChange.WINDOW_LEFT -> {
                // Das heutige Fenster ist vorbei oder hat noch nicht begonnen: wie am Feierabend abschließen.
                handleWindowEndLocked(now, settings)
                return@locked
            }

            ScheduleChange.WINDOW_ENTERED -> {
                // Das Fenster beginnt jetzt, also wie ein Fensterbeginn: statt einer sofort überfälligen Erinnerung
                // ein frischer Zyklus. Eine offene Erinnerung von vor dem Fenster schließt der Abgleich.
                settingsRepository.updateState { it.copy(cycleStartAt = now, snoozeUntil = 0L) }
            }

            // Ein heute laufender Zyklus läuft weiter, auch wenn der Beginn jetzt nach seinem Start liegt (siehe resync).
            ScheduleChange.NONE -> Unit
        }
        resyncLocked(now, settings)
    }

    /**
     * Schnellpause bzw. Urlaub bis [untilMillis]: Eine offene Erinnerung wird geschlossen, ohne als verpasst zu
     * zählen; bis dahin gibt es keine Erinnerungen und keine Schritterkennung. Danach startet ein frischer Zyklus.
     */
    suspend fun pauseUntil(untilMillis: Long): Unit = locked {
        val now = clock()
        val settings = updateSettingsLocked(now) { it.copy(pausedUntil = untilMillis.coerceAtLeast(0L)) }
        alerts.cancelReminder()
        // Ohne laufenden Zyklus beginnt nach der Pause in jedem Fall ein frischer (Alarm oder Abgleich).
        settingsRepository.updateState { it.copy(pendingReminderAt = 0L, snoozeUntil = 0L, cycleStartAt = 0L) }
        resyncLocked(now, settings)
    }

    /** Pause vorzeitig beenden: Im Fenster beginnt jetzt ein frischer Zyklus. */
    suspend fun resume(): Unit = locked {
        val now = clock()
        val settings = updateSettingsLocked(now) { it.copy(pausedUntil = 0L) }
        settingsRepository.updateState { it.copy(cycleStartAt = 0L, snoozeUntil = 0L) }
        resyncLocked(now, settings)
    }

    /** Snooze-Dauer ab der nächsten Erinnerung; ein laufender Snooze bleibt, wie er ist. */
    suspend fun setSnoozeMinutes(minutes: Int): Unit = locked {
        settingsRepository.updateSettings { it.copy(snoozeMinutes = minutes) }
    }

    suspend fun setDailyGoal(goal: Int): Unit = locked {
        settingsRepository.updateSettings { it.copy(dailyGoal = goal) }
    }

    suspend fun setRespectQuietModes(enabled: Boolean): Unit = locked {
        settingsRepository.updateSettings { it.copy(respectQuietModes = enabled) }
    }

    suspend fun setOnlyWhenWorn(enabled: Boolean): Unit = locked {
        settingsRepository.updateSettings { it.copy(onlyWhenWorn = enabled) }
    }

    suspend fun setVibration(level: VibrationLevel): Unit = locked {
        settingsRepository.updateSettings { it.copy(vibration = level) }
    }

    /** Schritt-Schwelle der Schritterkennung; der laufende Dienst übernimmt sie sofort. */
    suspend fun setStepThreshold(steps: Int): Unit = locked {
        settingsRepository.updateSettings { it.copy(stepThreshold = steps) }
    }

    suspend fun setAutoStepEnabled(enabled: Boolean): Unit = locked {
        val now = clock()
        val settings = updateSettingsLocked(now) { it.copy(autoStepEnabled = enabled) }
        syncStepTrackerLocked(now, settings)
    }

    /**
     * Gleicht Alarm und Schritterkennung mit dem gespeicherten Zustand ab (App-Start, Zeitänderung, Lizenz aktiv bzw.
     * inaktiv geworden …).
     */
    suspend fun resync(): Unit = locked {
        val now = clock()
        resyncLocked(now, currentSettingsLocked(now))
    }

    /** Nach Neustart/Update: Alarm aus dem DataStore wiederherstellen. */
    suspend fun restoreAfterBoot(): Unit = locked {
        val now = clock()
        val settings = currentSettingsLocked(now)
        if (!settings.enabled) {
            stopLocked(now, settings)
            return@locked
        }
        val state = settingsRepository.currentState()
        val kind = state.nextAlarmKind
        if (kind != null && state.nextAlarmAt > now) {
            alarms.schedule(PlannedAlarm(state.nextAlarmAt, kind))
            syncStepTrackerLocked(now, settings)
        } else {
            // Der Alarm wäre während der Ausschaltzeit fällig gewesen → frischen Zyklus beginnen.
            if (settings.schedule.contains(now, zoneProvider())) {
                settingsRepository.updateState { it.copy(cycleStartAt = now, snoozeUntil = 0L) }
            }
            resyncLocked(now, settings)
        }
    }

    /**
     * Plant aus dem gespeicherten Zustand neu. Liegt jetzt im heutigen Fenster und läuft heute noch kein Zyklus,
     * startet jetzt ein frischer; eine offene Erinnerung von einem früheren Tag gilt dann wie beim regulären
     * Fensterbeginn als verpasst – z. B. nach einem Neustart, der den Fensterbeginn verschlafen hat.
     *
     * „Läuft heute" statt „seit dem heutigen Fensterbeginn": Wird der Beginn hinter den Zyklusstart verschoben,
     * während das Fenster schon lief, bleiben Zyklus und Snooze erhalten. Einen Zyklusbeginn außerhalb eines
     * Fensters gibt es nicht (Einschalten und Pause setzen ihn auf 0).
     */
    private suspend fun resyncLocked(now: Long, settings: ReminderSettings) {
        if (!settings.enabled) {
            stopLocked(now, settings)
            return
        }
        val zone = zoneProvider()
        if (settings.isPausedAt(now)) {
            scheduleLocked(ReminderPlanner.planPauseEnd(settings.pausedUntil, settings.schedule, zone))
            syncStepTrackerLocked(now, settings)
            return
        }
        if (settings.schedule.contains(now, zone)) {
            val today = now.toLocalDate(zone)
            val state = settingsRepository.currentState()
            if (state.pendingReminderAt > 0L && state.pendingReminderAt.toLocalDate(zone) < today) {
                missPendingReminderLocked(state.pendingReminderAt, settings)
            }
            val cycleRunsToday = state.cycleStartAt in 1..now && state.cycleStartAt.toLocalDate(zone) == today
            if (!cycleRunsToday) {
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
                schedule = settings.schedule,
                zone = zone,
                pendingReminderAt = state.pendingReminderAt,
            ),
        )
        syncStepTrackerLocked(now, settings)
    }

    /**
     * Ausgeschaltet bzw. ohne gültige Lizenz: kein Alarm und keine Schritterkennung. Eine noch offene Erinnerung
     * (z. B. von kurz vor dem Ende der Testphase) wird geschlossen, ohne als verpasst zu zählen – wie beim Ausschalten;
     * ein laufender Zyklus endet, nach dem Ein- bzw. Freischalten beginnt ein frischer. Nach dem Ausschalten ist das
     * alles schon so, dann ändert sich hier nichts.
     */
    private suspend fun stopLocked(now: Long, settings: ReminderSettings) {
        cancelScheduleLocked()
        val state = settingsRepository.currentState()
        if (state.pendingReminderAt > 0L) alerts.cancelReminder()
        if (state.pendingReminderAt != 0L || state.snoozeUntil != 0L || state.cycleStartAt != 0L) {
            settingsRepository.updateState { it.copy(pendingReminderAt = 0L, snoozeUntil = 0L, cycleStartAt = 0L) }
        }
        syncStepTrackerLocked(now, settings)
    }

    /**
     * Die Einstellungen, wie die Engine sie anwendet: ohne gültige Lizenz mit ausgeschalteten Erinnerungen. Gespeichert
     * bleibt [ReminderSettings.enabled] unverändert.
     */
    private suspend fun licensed(settings: ReminderSettings, now: Long): ReminderSettings =
        if (settings.enabled && !licenseCheck.isActive(now)) settings.copy(enabled = false) else settings

    private suspend fun currentSettingsLocked(now: Long): ReminderSettings =
        licensed(settingsRepository.currentSettings(), now)

    /** Wie [SettingsRepository.updateSettings], liefert aber die angewandten Einstellungen ([licensed]). */
    private suspend fun updateSettingsLocked(now: Long, transform: (ReminderSettings) -> ReminderSettings): ReminderSettings =
        licensed(settingsRepository.updateSettings(transform), now)

    private suspend fun startCycleLocked(now: Long, settings: ReminderSettings) {
        settingsRepository.updateState { it.copy(cycleStartAt = now, snoozeUntil = 0L) }
        scheduleLocked(
            ReminderPlanner.planInWindow(now + settings.intervalMillis, now, settings.schedule, zoneProvider()),
        )
    }

    /** Plant [alarm]; `null` heißt: an keinem Tag wird erinnert, also nichts planen. */
    private suspend fun scheduleLocked(alarm: PlannedAlarm?) {
        if (alarm == null) {
            cancelScheduleLocked()
            return
        }
        val exact = alarms.schedule(alarm)
        if (!exact) Log.i(TAG, "Alarm ${alarm.kind} nur ungenau geplant")
        settingsRepository.updateState {
            it.copy(nextAlarmAt = alarm.triggerAtMillis, nextAlarmKind = alarm.kind)
        }
    }

    private suspend fun cancelScheduleLocked() {
        alarms.cancel()
        settingsRepository.updateState { it.copy(nextAlarmAt = 0L, nextAlarmKind = null) }
    }

    /** Startet bzw. stoppt die Schritterkennung passend zu Einstellungen, Uhrzeit und Berechtigung. */
    private suspend fun syncStepTrackerLocked(now: Long, settings: ReminderSettings) {
        if (stepTracking.shouldRun(settings, now, zoneProvider())) {
            stepTracking.start()
        } else {
            stepTracking.stop()
            settingsRepository.updateState { it.copy(stepTrackingActive = false) }
        }
    }

    companion object {
        private const val TAG = "ReminderEngine"

        /** Mehrfaches Tippen auf „Jetzt bewegt" innerhalb dieser Zeit wird nur einmal gezählt. */
        const val MANUAL_DEBOUNCE_MILLIS = 60_000L

        /** Gehpausen mit weniger Abstand gelten als eine zusammenhängende Bewegungspause. */
        const val WALK_SESSION_GAP_MILLIS = 5 * 60_000L

        /** Stilles Verlängern nur, wenn sich der Alarm dadurch spürbar verschiebt. */
        const val MIN_EXTENSION_MILLIS = 60_000L
    }
}
