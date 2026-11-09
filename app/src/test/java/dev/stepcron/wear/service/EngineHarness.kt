package dev.stepcron.wear.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.stepcron.wear.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Echte [ReminderEngine] mit echtem DataStore (Datei in [folder]) und den Attrappen aus EngineFakes – für Tests
 * außerhalb von ReminderEngineTest. Die Uhr steht auf [now] (Standard: Montag, 2026-11-09, 09:00 in Berlin).
 * Nach dem Test [close] aufrufen.
 */
internal class EngineHarness(folder: TemporaryFolder, val zone: ZoneId = ZoneId.of("Europe/Berlin")) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    var now: Long = at("09:00")
    var stateChanges = 0

    val alarms = FakeAlarms()
    val alerts = FakeAlerts()
    val stepTracking = FakeStepTracking()
    val deviceState = FakeDeviceState()
    val log = FakeMovementLog()

    val settingsRepository: SettingsRepository = folder.newFile("settings.preferences_pb").let { file ->
        // Der Dateiname muss auf .preferences_pb enden; DataStore legt die Datei selbst an.
        file.delete()
        SettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
    }

    val engine = ReminderEngine(
        settingsRepository = settingsRepository,
        movementLog = log,
        alarms = alarms,
        alerts = alerts,
        stepTracking = stepTracking,
        deviceState = deviceState,
        clock = { now },
        zoneProvider = { zone },
        dispatcher = Dispatchers.Unconfined,
        onStateChanged = { stateChanges++ },
    )

    fun at(time: String, date: String = MONDAY): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    fun close() {
        scope.cancel()
    }

    companion object {
        const val MONDAY = "2026-11-09"
        const val TUESDAY = "2026-11-10"
    }
}
