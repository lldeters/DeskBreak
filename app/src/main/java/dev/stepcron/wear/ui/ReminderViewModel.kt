package dev.stepcron.wear.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.stepcron.shared.license.LicenseRecord
import dev.stepcron.shared.license.LicenseRules
import dev.stepcron.wear.DeskBreakApp
import dev.stepcron.wear.Permissions
import dev.stepcron.wear.data.DemoDataSeeder
import dev.stepcron.wear.data.MovementRepository
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.SettingsRepository
import dev.stepcron.wear.data.settings.stepFrom
import dev.stepcron.wear.data.db.MovementEvent
import dev.stepcron.wear.domain.ActiveWindow
import dev.stepcron.wear.domain.StatsCalculator
import dev.stepcron.wear.domain.WeeklySchedule
import dev.stepcron.wear.domain.toLocalDate
import dev.stepcron.wear.service.AlarmScheduler
import dev.stepcron.wear.service.ReminderAlerts
import dev.stepcron.wear.service.ReminderEngine
import dev.stepcron.wear.service.StepTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** Gemeinsames ViewModel für Haupt- und Statistik-Bildschirm. */
class ReminderViewModel(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val movementRepository: MovementRepository,
    private val engine: ReminderEngine,
    private val demoDataSeeder: DemoDataSeeder,
    private val alarmScheduler: AlarmScheduler,
    /** Für die Probevibration beim Wechsel der Stärke. */
    private val alerts: ReminderAlerts,
    /** Lizenz der Uhr (Testphase, Freischaltung) für Hinweise auf dem Hauptbildschirm. */
    private val licenseRecord: Flow<LicenseRecord>,
    /** Nach Änderungen an der Historie (Demo-Daten, Löschen), z. B. für den Abgleich mit dem Handy. */
    private val onHistoryChanged: () -> Unit = {},
) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Taktgeber für Countdown und Tageswechsel. */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MILLIS)
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /** `null`, bis [onResume] den Status zum ersten Mal gelesen hat. */
    private val systemStatus = MutableStateFlow<SystemStatus?>(null)

    /**
     * Heutige Bewegungspausen für das Tagesziel. Beginnt mit `null`, damit der Startbildschirm nicht auf die
     * Datenbank wartet (sie wird erst dabei aufgebaut).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayMovements: Flow<Int?> = ticker
        .map { now -> now.toLocalDate(zone) }
        .distinctUntilChanged()
        .flatMapLatest { today ->
            val zone = zone
            movementRepository.eventsBetween(
                today.atStartOfDay(zone).toInstant().toEpochMilli(),
                today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            ).map<List<MovementEvent>, Int?> { events -> events.count { it.eventType.isMovement } }
        }
        .onStart { emit(null) }

    val homeUiState: StateFlow<HomeUiState> = combine(
        settingsRepository.settings,
        settingsRepository.state,
        ticker,
        systemStatus.filterNotNull(),
        // Lizenz mit: Ihr kleiner DataStore ist so schnell gelesen wie die Einstellungen, und Hinweis bzw. Karte sind
        // so gleich im ersten Bild statt kurz danach.
        combine(todayMovements, licenseRecord) { movements, license -> movements to license },
    ) { settings, state, _, system, (movements, license) ->
        // Der Ticker löst nur die Neuberechnung aus; gerechnet wird mit der aktuellen Uhrzeit,
        // sonst wäre der Countdown direkt nach einem Reset um bis zu einen Tick zu lang.
        val now = System.currentTimeMillis()
        HomeUiState.create(settings, state, now, system, zone, movements, LicenseRules.status(license, now))
    }
        // Berechnung (inkl. der einmaligen Zeitzonen-Initialisierung von java.time) nicht im UI-Thread.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val statsUiState: StateFlow<StatsUiState> = ticker
        .map { now -> now.toLocalDate(zone) }
        .distinctUntilChanged()
        .flatMapLatest { today -> statsFor(today) }
        // Room-Aufbau beim ersten Zugriff und die Auswertung laufen abseits des UI-Threads.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StatsUiState())

    private fun statsFor(today: LocalDate): Flow<StatsUiState> {
        val zone = zone
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        // Monatsansicht (deckt auch die 7 Tage ab) und Tageszeiten der letzten 30 Tage.
        val calendarStart = StatsCalculator.calendarStart(today).atStartOfDay(zone).toInstant().toEpochMilli()
        val hourlyStart = today.minusDays(TIME_OF_DAY_DAYS - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
        val history = combine(
            movementRepository.eventsBetween(todayStart, tomorrowStart),
            movementRepository.dailyCountsSince(calendarStart),
            movementRepository.hourlyCountsSince(hourlyStart),
        ) { todayEvents, dailyCounts, hourlyCounts -> Triple(todayEvents, dailyCounts, hourlyCounts) }
        return combine(
            history,
            movementRepository.currentStreak(),
            settingsRepository.settings,
            ticker,
        ) { (todayEvents, dailyCounts, hourlyCounts), streak, settings, now ->
            StatsUiState.create(todayEvents, dailyCounts, hourlyCounts, streak, settings, now, today, zone)
        }
    }

    /**
     * Bei jedem Öffnen bzw. Zurückkehren in die App (auch nach dem Berechtigungsdialog): Berechtigungen
     * neu lesen und Alarm/Schritterkennung abgleichen – einmal pro Resume und nicht auf dem Main-Thread,
     * denn beides besteht aus Binder-Aufrufen ans System, die sonst die ersten Frames verzögern.
     * Bewusster Kompromiss: Direkt nach dem Berechtigungsdialog kann der erste Frame noch den alten
     * Status zeigen; er liegt unter der Schließ-Animation des Dialogs und ist ein bis zwei Frames später
     * aktuell.
     */
    fun onResume() {
        viewModelScope.launch(Dispatchers.Default) {
            systemStatus.value = readSystemStatus()
            engine.resync()
        }
    }

    fun markPermissionsRequested() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(permissionsRequested = true) }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { engine.setEnabled(enabled) }
    }

    fun changeInterval(steps: Int) {
        viewModelScope.launch {
            val current = settingsRepository.currentSettings().intervalMinutes
            engine.setIntervalMinutes(current + steps * ReminderSettings.INTERVAL_STEP_MINUTES)
        }
    }

    fun setDayEnabled(day: DayOfWeek, enabled: Boolean) {
        updateSchedule { schedule -> schedule.with(day) { it.copy(enabled = enabled) } }
    }

    /** Neuer Beginn; bleibt vor dem Ende des Tages, ohne das Ende zu verschieben. */
    fun setDayStart(day: DayOfWeek, minuteOfDay: Int) {
        updateSchedule { schedule ->
            schedule.with(day) { entry ->
                // Mindestens 15 Minuten Fenster, wie beim Laden (ActiveWindow.sanitized).
                val start = minuteOfDay.coerceIn(0, entry.window.endMinute - ActiveWindow.MIN_LENGTH_MINUTES)
                entry.copy(window = entry.window.copy(startMinute = start))
            }
        }
    }

    /** Neues Ende; bleibt nach dem Beginn des Tages, ohne den Beginn zu verschieben. */
    fun setDayEnd(day: DayOfWeek, minuteOfDay: Int) {
        updateSchedule { schedule ->
            schedule.with(day) { entry ->
                val end = minuteOfDay.coerceIn(
                    entry.window.startMinute + ActiveWindow.MIN_LENGTH_MINUTES,
                    ActiveWindow.MINUTES_PER_DAY,
                )
                entry.copy(window = entry.window.copy(endMinute = end))
            }
        }
    }

    /** Übernimmt die Uhrzeiten von [day] für alle Tage; welche Tage an oder frei sind, bleibt gleich. */
    fun copyDayToAll(day: DayOfWeek) {
        updateSchedule { schedule -> schedule.withAllWindows(schedule[day].window) }
    }

    /** Die Änderung läuft atomar im DataStore auf dem aktuell gespeicherten Plan (keine verlorenen Tipps). */
    private fun updateSchedule(transform: (WeeklySchedule) -> WeeklySchedule) {
        viewModelScope.launch { engine.updateSchedule(transform) }
    }

    fun setAutoStepEnabled(enabled: Boolean) {
        viewModelScope.launch { engine.setAutoStepEnabled(enabled) }
    }

    fun manualReset() {
        viewModelScope.launch { engine.onManualReset() }
    }

    fun changeDailyGoal(steps: Int) {
        viewModelScope.launch {
            val current = settingsRepository.currentSettings().dailyGoal
            engine.setDailyGoal(current + steps)
        }
    }

    fun changeSnooze(steps: Int) {
        viewModelScope.launch {
            val current = settingsRepository.currentSettings().snoozeMinutes
            engine.setSnoozeMinutes(ReminderSettings.SNOOZE_OPTIONS.stepFrom(current, steps))
        }
    }

    fun changeStepThreshold(steps: Int) {
        viewModelScope.launch {
            val current = settingsRepository.currentSettings().stepThreshold
            engine.setStepThreshold(ReminderSettings.STEP_THRESHOLD_OPTIONS.stepFrom(current, steps))
        }
    }

    fun setRespectQuietModes(enabled: Boolean) {
        viewModelScope.launch { engine.setRespectQuietModes(enabled) }
    }

    fun setOnlyWhenWorn(enabled: Boolean) {
        viewModelScope.launch { engine.setOnlyWhenWorn(enabled) }
    }

    /** Nächste Vibrationsstufe – und gleich zur Probe vibrieren. */
    fun nextVibration() {
        viewModelScope.launch {
            val level = settingsRepository.currentSettings().vibration.next()
            engine.setVibration(level)
            alerts.vibrateReminder(level)
        }
    }

    /** Schnellpause bzw. Urlaub bis [untilMillis]. */
    fun pauseUntil(untilMillis: Long) {
        viewModelScope.launch { engine.pauseUntil(untilMillis) }
    }

    fun resume() {
        viewModelScope.launch { engine.resume() }
    }

    fun completeExercise() {
        viewModelScope.launch { engine.onExerciseCompleted() }
    }

    fun seedDemoData() {
        // Default: Kann der erste Datenbankzugriff sein (Room-Aufbau) und erzeugt Daten im Speicher.
        viewModelScope.launch(Dispatchers.Default) {
            demoDataSeeder.seedLastSevenDays(settingsRepository.currentSettings())
            onHistoryChanged()
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.Default) {
            movementRepository.clearAll()
            onHistoryChanged()
        }
    }

    private fun readSystemStatus() = SystemStatus(
        notificationsGranted = Permissions.canPostNotifications(application),
        activityRecognitionGranted = Permissions.hasActivityRecognition(application),
        exactAlarmsAllowed = alarmScheduler.canScheduleExactAlarms(),
        stepSensorAvailable = StepTracker.isAvailable(application),
    )

    companion object {
        private const val TICK_MILLIS = 15_000L
        private const val TIME_OF_DAY_DAYS = 30
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeskBreakApp
                val container = app.container
                ReminderViewModel(
                    application = app,
                    settingsRepository = container.settingsRepository,
                    movementRepository = container.movementRepository,
                    engine = container.engine,
                    demoDataSeeder = container.demoDataSeeder,
                    alarmScheduler = container.alarmScheduler,
                    alerts = container.notifier,
                    licenseRecord = container.licenseRepository.record,
                    // Erst beim Aufruf auflösen: Der Abgleich wird beim App-Start nicht gebraucht.
                    onHistoryChanged = { container.watchSync.requestPublish() },
                )
            }
        }
    }
}
