package dev.stepcron.wear.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.stepcron.wear.DeskBreakApp
import dev.stepcron.wear.Permissions
import dev.stepcron.wear.data.DemoDataSeeder
import dev.stepcron.wear.data.MovementRepository
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.SettingsRepository
import dev.stepcron.wear.domain.toLocalDate
import dev.stepcron.wear.service.AlarmScheduler
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
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    val homeUiState: StateFlow<HomeUiState> = combine(
        settingsRepository.settings,
        settingsRepository.state,
        ticker,
        systemStatus.filterNotNull(),
    ) { settings, state, _, system ->
        // Der Ticker löst nur die Neuberechnung aus; gerechnet wird mit der aktuellen Uhrzeit,
        // sonst wäre der Countdown direkt nach einem Reset um bis zu einen Tick zu lang.
        HomeUiState.create(settings, state, System.currentTimeMillis(), system, zone)
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
        val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        return combine(
            movementRepository.eventsBetween(todayStart, tomorrowStart),
            movementRepository.dailyCountsSince(weekStart),
            movementRepository.currentStreak(),
            settingsRepository.settings,
            ticker,
        ) { todayEvents, dailyCounts, streak, settings, now ->
            StatsUiState.create(todayEvents, dailyCounts, streak, settings, now, today, zone)
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

    fun changeStartHour(delta: Int) {
        viewModelScope.launch {
            val settings = settingsRepository.currentSettings()
            val start = (settings.activeStartHour + delta).coerceIn(0, settings.activeEndHour - 1)
            engine.setActiveHours(start, settings.activeEndHour)
        }
    }

    fun changeEndHour(delta: Int) {
        viewModelScope.launch {
            val settings = settingsRepository.currentSettings()
            val end = (settings.activeEndHour + delta).coerceIn(settings.activeStartHour + 1, 24)
            engine.setActiveHours(settings.activeStartHour, end)
        }
    }

    fun setAutoStepEnabled(enabled: Boolean) {
        viewModelScope.launch { engine.setAutoStepEnabled(enabled) }
    }

    fun manualReset() {
        viewModelScope.launch { engine.onManualReset() }
    }

    fun seedDemoData() {
        // Default: Kann der erste Datenbankzugriff sein (Room-Aufbau) und erzeugt Daten im Speicher.
        viewModelScope.launch(Dispatchers.Default) {
            demoDataSeeder.seedLastSevenDays(settingsRepository.currentSettings())
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.Default) { movementRepository.clearAll() }
    }

    private fun readSystemStatus() = SystemStatus(
        notificationsGranted = Permissions.canPostNotifications(application),
        activityRecognitionGranted = Permissions.hasActivityRecognition(application),
        exactAlarmsAllowed = alarmScheduler.canScheduleExactAlarms(),
        stepSensorAvailable = StepTracker.isAvailable(application),
    )

    companion object {
        private const val TICK_MILLIS = 15_000L
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
                )
            }
        }
    }
}
