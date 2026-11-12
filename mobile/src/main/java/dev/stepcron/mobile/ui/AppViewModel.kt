package dev.stepcron.mobile.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.stepcron.mobile.DeskBreakPhoneApp
import dev.stepcron.mobile.settings.SettingsEdits
import dev.stepcron.mobile.settings.SettingsRules
import dev.stepcron.mobile.settings.VibrationLevel
import dev.stepcron.mobile.stats.BackupFiles
import dev.stepcron.mobile.stats.BackupSummary
import dev.stepcron.mobile.stats.CsvExport
import dev.stepcron.mobile.stats.Dashboard
import dev.stepcron.mobile.stats.StatsCalculator
import dev.stepcron.mobile.stats.WatchStatusCalculator
import dev.stepcron.mobile.stats.WatchStatusInfo
import dev.stepcron.mobile.sync.Decoded
import dev.stepcron.mobile.sync.RestoreState
import dev.stepcron.mobile.sync.SettingsSendState
import dev.stepcron.mobile.sync.SyncDecoding
import dev.stepcron.mobile.sync.SyncRepository
import dev.stepcron.mobile.sync.SyncState
import dev.stepcron.mobile.ui.settings.SettingsActions
import dev.stepcron.shared.sync.SettingsUpdate
import dev.stepcron.shared.sync.SyncBackup
import dev.stepcron.shared.sync.SyncCodec
import dev.stepcron.shared.sync.SyncSettings
import dev.stepcron.shared.sync.SyncSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId

/** Kurzmeldungen (Snackbar) nach Export und Dateiauswahl. */
enum class UserMessage {
    CSV_EXPORTED,
    BACKUP_EXPORTED,
    EXPORT_FAILED,
    NOTHING_TO_EXPORT,
    BACKUP_UNREADABLE,
    BACKUP_TOO_NEW,
    BACKUP_TOO_LARGE,
    READ_FAILED,
    NO_FILE_APP,
}

/** Eine anzuzeigende Meldung; die [id] unterscheidet zwei gleiche Meldungen nacheinander. */
data class MessageEvent(val id: Long, val message: UserMessage)

/** Gelesene Sicherung, die auf die Bestätigung des Nutzers wartet. */
data class PendingRestore(val backup: SyncBackup, val summary: BackupSummary)

/** Aus dem Snapshot berechnete Inhalte (Auswertung und Status). */
data class WatchData(
    val loaded: Boolean = false,
    val snapshot: SyncSnapshot? = null,
    val receivedAtMillis: Long? = null,
    /** Einstellungen samt noch nicht bestätigter Änderungen. */
    val settings: SyncSettings? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val dashboard: Dashboard? = null,
    val watchStatus: WatchStatusInfo? = null,
    val nowMillis: Long = 0L,
)

/** Zustand, der nur in der Oberfläche lebt. */
data class LocalUiState(
    val pendingRestore: PendingRestore? = null,
    val message: MessageEvent? = null,
    val fileBusy: Boolean = false,
)

data class AppUiState(
    val data: WatchData = WatchData(),
    val sync: SyncState = SyncState(),
    val settingsSend: SettingsSendState = SettingsSendState.Idle,
    val restore: RestoreState = RestoreState.Idle,
    val local: LocalUiState = LocalUiState(),
) {
    /** Noch kein gespeicherter Stand gelesen. */
    val loading: Boolean get() = !data.loaded
    val hasSnapshot: Boolean get() = data.snapshot != null
}

/** Ein ViewModel für alle drei Seiten; Rechnen und Dateizugriffe laufen abseits des Main-Threads. */
class AppViewModel(
    private val repository: SyncRepository,
    private val contentResolver: ContentResolver,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel(), SettingsActions {

    /** Taktgeber für Countdown und Tageswechsel. */
    private val ticker = flow {
        while (true) {
            emit(clock())
            delay(TICK_MILLIS)
        }
    }

    private val local = MutableStateFlow(LocalUiState())
    private var messageCounter = 0L

    // Snapshot und Änderungen getrennt beziehen und hier zusammensetzen: so passen Auswertung und Einstellungen
    // in jedem Zustand zusammen (kein Zwischenstand aus neuem Snapshot und alten Einstellungen).
    private val watchData = combine(repository.snapshot, repository.pendingChanges, ticker) { state, pending, now ->
        val snapshot = state.snapshot
        val settings = snapshot?.settings?.let { SettingsRules.apply(it, pending) }
        if (snapshot == null || settings == null) {
            WatchData(loaded = state.loaded, nowMillis = now)
        } else {
            val zone = StatsCalculator.zoneOf(snapshot)
            WatchData(
                loaded = true,
                snapshot = snapshot,
                receivedAtMillis = state.receivedAtMillis,
                settings = settings,
                zone = zone,
                dashboard = StatsCalculator.dashboard(snapshot, now, SettingsRules.clampDailyGoal(settings.dailyGoal)),
                watchStatus = WatchStatusCalculator.compute(snapshot, settings, now, zone),
                nowMillis = now,
            )
        }
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<AppUiState> = combine(
        watchData,
        repository.sync,
        repository.settingsSend,
        repository.restore,
        local,
    ) { data, sync, send, restore, localState ->
        AppUiState(data = data, sync = sync, settingsSend = send, restore = restore, local = localState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AppUiState())

    // --- Abgleich ---

    override fun syncNow() {
        repository.refresh()
    }

    // --- Einstellungen: jede Änderung geht sofort an die Uhr ---

    private fun edit(build: (SyncSettings) -> SettingsUpdate) {
        val current = repository.currentSettings() ?: return
        repository.changeSettings(build(current))
    }

    override fun setEnabled(enabled: Boolean) = edit { SettingsEdits.enabled(it, enabled) }
    override fun stepInterval(steps: Int) = edit { SettingsEdits.stepInterval(it, steps) }
    override fun stepDailyGoal(steps: Int) = edit { SettingsEdits.stepDailyGoal(it, steps) }
    override fun stepSnooze(steps: Int) = edit { SettingsEdits.stepSnooze(it, steps) }
    override fun stepStepThreshold(steps: Int) = edit { SettingsEdits.stepStepThreshold(it, steps) }
    override fun setVibration(level: VibrationLevel) = edit { SettingsEdits.vibration(it, level) }
    override fun setAutoSteps(enabled: Boolean) = edit { SettingsEdits.autoSteps(it, enabled) }
    override fun setRespectQuietModes(enabled: Boolean) = edit { SettingsEdits.respectQuietModes(it, enabled) }
    override fun setOnlyWhenWorn(enabled: Boolean) = edit { SettingsEdits.onlyWhenWorn(it, enabled) }
    override fun setDayEnabled(dayIndex: Int, enabled: Boolean) = edit { SettingsEdits.dayEnabled(it, dayIndex, enabled) }

    override fun setDayWindow(dayIndex: Int, startMinute: Int, endMinute: Int) =
        edit { SettingsEdits.dayWindow(it, dayIndex, startMinute, endMinute) }

    override fun setWindowForAllDays(startMinute: Int, endMinute: Int) =
        edit { SettingsEdits.windowForAllDays(it, startMinute, endMinute) }

    override fun endPause() = edit { SettingsEdits.endPause(it) }
    override fun pauseOneHour() = edit { SettingsEdits.pauseFor(it, clock(), SettingsRules.QUICK_PAUSE_MINUTES) }

    override fun pauseRestOfDay() {
        val zone = watchZone()
        edit { SettingsEdits.pauseRestOfDay(it, clock(), zone) }
    }

    override fun retrySettings() {
        repository.retrySettings()
    }

    // --- Export ---

    /** Dateiname für den CSV-Export (Tag in der Zeitzone der Uhr). */
    override fun csvFileName(): String = CsvExport.fileName(today())

    override fun backupFileName(): String = BackupFiles.fileName(today())

    private fun today() = Instant.ofEpochMilli(clock()).atZone(watchZone()).toLocalDate()

    /** Zeitzone der Uhr laut letztem Snapshot, sonst die des Handys. */
    private fun watchZone(): ZoneId =
        repository.snapshot.value.snapshot?.let(StatsCalculator::zoneOf) ?: ZoneId.systemDefault()

    override fun exportCsv(uri: Uri) {
        val snapshot = repository.snapshot.value.snapshot ?: return show(UserMessage.NOTHING_TO_EXPORT)
        writeFile(uri, UserMessage.CSV_EXPORTED) { CsvExport.build(snapshot).toByteArray(Charsets.UTF_8) }
    }

    override fun exportBackup(uri: Uri) {
        val snapshot = repository.snapshot.value.snapshot ?: return show(UserMessage.NOTHING_TO_EXPORT)
        // Einstellungen wie gerade angezeigt (inkl. noch nicht bestätigter Änderungen).
        val settings = repository.currentSettings() ?: snapshot.settings
        writeFile(uri, UserMessage.BACKUP_EXPORTED) {
            SyncCodec.encodeBackup(BackupFiles.fromSnapshot(snapshot.copy(settings = settings), clock()))
        }
    }

    private fun writeFile(uri: Uri, success: UserMessage, content: () -> ByteArray) {
        viewModelScope.launch {
            local.update { it.copy(fileBusy = true) }
            val ok = withContext(Dispatchers.IO) {
                try {
                    val bytes = content()
                    val stream = contentResolver.openOutputStream(uri) ?: return@withContext false
                    stream.use { it.write(bytes) }
                    true
                } catch (e: IOException) {
                    false
                } catch (e: SecurityException) {
                    false
                } catch (e: IllegalArgumentException) {
                    false
                }
            }
            local.update { it.copy(fileBusy = false) }
            show(if (ok) success else UserMessage.EXPORT_FAILED)
        }
    }

    // --- Wiederherstellung ---

    /** Liest die gewählte Datei; bei einer gültigen Sicherung folgt die Rückfrage ([LocalUiState.pendingRestore]). */
    override fun loadBackup(uri: Uri) {
        viewModelScope.launch {
            local.update { it.copy(fileBusy = true) }
            val result = withContext(Dispatchers.IO) { readBackup(uri) }
            local.update { state ->
                when (result) {
                    is BackupRead.Ok -> state.copy(fileBusy = false, pendingRestore = PendingRestore(result.backup, BackupFiles.summarize(result.backup)))
                    is BackupRead.Error -> state.copy(fileBusy = false, message = nextMessage(result.message))
                }
            }
        }
    }

    override fun confirmRestore() {
        val pending = local.value.pendingRestore ?: return
        local.update { it.copy(pendingRestore = null) }
        repository.startRestore(pending.backup)
    }

    override fun cancelRestore() {
        local.update { it.copy(pendingRestore = null) }
    }

    override fun dismissRestoreResult() {
        repository.clearRestoreState()
    }

    /** Keine App zum Speichern bzw. Öffnen von Dateien gefunden. */
    override fun onNoFileApp() {
        show(UserMessage.NO_FILE_APP)
    }

    fun messageShown(id: Long) {
        local.update { if (it.message?.id == id) it.copy(message = null) else it }
    }

    private fun show(message: UserMessage) {
        local.update { it.copy(message = nextMessage(message)) }
    }

    private fun nextMessage(message: UserMessage): MessageEvent = MessageEvent(++messageCounter, message)

    private sealed interface BackupRead {
        data class Ok(val backup: SyncBackup) : BackupRead
        data class Error(val message: UserMessage) : BackupRead
    }

    private fun readBackup(uri: Uri): BackupRead = try {
        val stream = contentResolver.openInputStream(uri)
        if (stream == null) {
            BackupRead.Error(UserMessage.READ_FAILED)
        } else {
            val bytes = stream.use { readLimited(it, BackupFiles.MAX_BACKUP_BYTES) }
            when (bytes) {
                null -> BackupRead.Error(UserMessage.BACKUP_TOO_LARGE)
                else -> when (val decoded = SyncDecoding.backup(bytes)) {
                    is Decoded.Ok -> BackupRead.Ok(decoded.value)
                    is Decoded.TooNew -> BackupRead.Error(UserMessage.BACKUP_TOO_NEW)
                    Decoded.Invalid -> BackupRead.Error(UserMessage.BACKUP_UNREADABLE)
                }
            }
        }
    } catch (e: IOException) {
        BackupRead.Error(UserMessage.READ_FAILED)
    } catch (e: SecurityException) {
        BackupRead.Error(UserMessage.READ_FAILED)
    } catch (e: IllegalArgumentException) {
        BackupRead.Error(UserMessage.READ_FAILED)
    }

    /** Liest höchstens [limit] Bytes; `null`, wenn die Datei größer ist. */
    private fun readLimited(input: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        private const val TICK_MILLIS = 30_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val BUFFER_BYTES = 64 * 1024

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeskBreakPhoneApp
                AppViewModel(app.repository, app.contentResolver)
            }
        }
    }
}
