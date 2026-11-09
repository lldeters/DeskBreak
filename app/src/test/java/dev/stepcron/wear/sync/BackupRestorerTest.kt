package dev.stepcron.wear.sync

import dev.stepcron.shared.sync.RestoreResult
import dev.stepcron.shared.sync.SyncBackup
import dev.stepcron.shared.sync.SyncCodec
import dev.stepcron.shared.sync.SyncEvent
import dev.stepcron.wear.data.MovementHistory
import dev.stepcron.wear.data.MovementRepository
import dev.stepcron.wear.data.db.FakeMovementDao
import dev.stepcron.wear.data.db.MovementEvent
import dev.stepcron.wear.data.db.MovementEventType
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.domain.AlarmKind.REMINDER
import dev.stepcron.wear.domain.PlannedAlarm
import dev.stepcron.wear.service.EngineHarness
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/** Wiederherstellung mit echtem MovementRepository (DAO im Speicher) und echter Engine. Montag 09:00. */
class BackupRestorerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var harness: EngineHarness
    private lateinit var dao: FakeMovementDao
    private lateinit var restorer: BackupRestorer

    private val oldEvents = listOf(
        MovementEvent(timestamp = 1_000L, eventType = MovementEventType.MISSED, intervalMinutes = 45),
        MovementEvent(timestamp = 2_000L, eventType = MovementEventType.MANUAL_RESET, intervalMinutes = 45),
    )

    @Before
    fun setUp() {
        harness = EngineHarness(folder)
        dao = FakeMovementDao(oldEvents)
        restorer = restorerFor(MovementRepository { dao })
    }

    @After
    fun tearDown() {
        harness.close()
    }

    private fun restorerFor(history: MovementHistory) = BackupRestorer(
        history = history,
        settingsApplier = SettingsUpdateApplier(harness.settingsRepository, harness.engine, clock = { harness.now }),
        clock = { harness.now },
        describe = { "failed: ${it.name}" },
    )

    private fun backup(
        settings: ReminderSettings = ReminderSettings(enabled = true, intervalMinutes = 30, dailyGoal = 12),
        events: List<SyncEvent> = listOf(
            SyncEvent(timestampMillis = harness.at("08:30"), type = SyncEvent.AUTO_STEP_RESET, intervalMinutes = 30),
            SyncEvent(timestampMillis = harness.at("08:40"), type = "JUMPED", intervalMinutes = 30),
            SyncEvent(timestampMillis = harness.at("08:50"), type = SyncEvent.SNOOZED, intervalMinutes = 30),
        ),
        version: Int = SyncCodec.VERSION,
    ) = SyncBackup(
        version = version,
        createdAtMillis = harness.now,
        zoneId = "Europe/Berlin",
        settings = SyncMapping.toSyncSettings(settings),
        events = events,
    )

    private fun bytes(backup: SyncBackup) = SyncCodec.encodeBackup(backup)

    @Test
    fun `a backup replaces the whole history and applies its settings`() = runBlocking {
        val result = restorer.restore("r1", bytes(backup()))

        assertEquals(RestoreResult(restoreId = "r1", success = true, restoredEvents = 2), result)
        assertEquals(
            listOf(
                harness.at("08:30") to MovementEventType.AUTO_STEP_RESET,
                harness.at("08:50") to MovementEventType.SNOOZED,
            ),
            dao.rows.map { it.timestamp to it.eventType },
        )
        val settings = harness.settingsRepository.currentSettings()
        assertTrue(settings.enabled)
        assertEquals(30, settings.intervalMinutes)
        assertEquals(12, settings.dailyGoal)
        assertEquals(PlannedAlarm(harness.at("09:30"), REMINDER), harness.alarms.planned)
    }

    @Test
    fun `restoring the same backup twice gives the same history`() = runBlocking {
        restorer.restore("r1", bytes(backup()))
        val first = dao.rows.map { it.copy(id = 0L) }
        restorer.restore("r2", bytes(backup()))

        assertEquals(first, dao.rows.map { it.copy(id = 0L) })
    }

    @Test
    fun `a broken backup changes nothing`() = runBlocking {
        listOf(null, ByteArray(0), "kein JSON".toByteArray(), "{\"version\":1}".toByteArray()).forEach { input ->
            val result = restorer.restore("r1", input)

            assertEquals(
                RestoreResult(restoreId = "r1", success = false, restoredEvents = 0, message = "failed: UNREADABLE"),
                result,
            )
        }
        assertEquals(oldEvents, dao.rows.map { it.copy(id = 0L) })
        assertFalse(harness.settingsRepository.currentSettings().enabled)
        assertNull(harness.alarms.planned)
        assertEquals(0, harness.stateChanges)
    }

    @Test
    fun `a backup from a newer version or with an incomplete schedule is rejected`() = runBlocking {
        val newer = restorer.restore("r1", bytes(backup(version = SyncCodec.VERSION + 1)))
        val sixDays = backup().let { it.copy(settings = it.settings.copy(schedule = it.settings.schedule.take(6))) }
        val incomplete = restorer.restore("r2", bytes(sixDays))

        assertFalse(newer.success)
        assertFalse(incomplete.success)
        assertEquals(oldEvents, dao.rows.map { it.copy(id = 0L) })
        assertFalse(harness.settingsRepository.currentSettings().enabled)
    }

    @Test
    fun `a backup without events empties the history`() = runBlocking {
        val result = restorer.restore("r1", bytes(backup(events = emptyList())))

        assertTrue(result.success)
        assertEquals(0, result.restoredEvents)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `a storage failure is reported to the phone`() = runBlocking {
        val failing = object : MovementHistory {
            override suspend fun eventsSince(fromMillis: Long): List<MovementEvent> = emptyList()

            override suspend fun replaceAll(events: List<MovementEvent>) = throw IOException("Speicher voll")
        }

        val result = restorerFor(failing).restore("r1", bytes(backup()))

        assertEquals(
            RestoreResult(restoreId = "r1", success = false, restoredEvents = 0, message = "failed: NOT_SAVED"),
            result,
        )
        assertFalse(harness.settingsRepository.currentSettings().enabled)
    }
}
