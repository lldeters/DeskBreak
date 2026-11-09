package dev.stepcron.wear.sync

import dev.stepcron.shared.sync.SyncCodec
import dev.stepcron.shared.sync.SyncEvent
import dev.stepcron.shared.sync.SyncStatus
import dev.stepcron.wear.data.MovementRepository
import dev.stepcron.wear.data.db.FakeMovementDao
import dev.stepcron.wear.data.db.MovementEvent
import dev.stepcron.wear.data.db.MovementEventType
import dev.stepcron.wear.data.settings.VibrationLevel
import dev.stepcron.wear.domain.AlarmKind
import dev.stepcron.wear.service.EngineHarness
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.ZoneId

class SnapshotBuilderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var harness: EngineHarness
    private val zone = ZoneId.of("Europe/Berlin")

    @Before
    fun setUp() {
        harness = EngineHarness(folder, zone)
    }

    @After
    fun tearDown() {
        harness.close()
    }

    @Test
    fun `the history starts at midnight 399 days before today`() {
        val start = SnapshotBuilder.historyStart(harness.at("10:00"), zone)

        assertEquals(LocalDate.of(2025, 10, 6).atStartOfDay(zone).toInstant().toEpochMilli(), start)
        assertEquals(LocalDate.of(2026, 11, 9).minusDays(SyncCodec.HISTORY_DAYS - 1L), LocalDate.of(2025, 10, 6))
    }

    @Test
    fun `the snapshot holds settings, status, zone and the events of the history window`() = runBlocking {
        harness.now = harness.at("10:00")
        val start = SnapshotBuilder.historyStart(harness.now, zone)
        val dao = FakeMovementDao(
            listOf(
                MovementEvent(timestamp = start - 1, eventType = MovementEventType.MISSED, intervalMinutes = 45),
                MovementEvent(timestamp = start, eventType = MovementEventType.SNOOZED, intervalMinutes = 45),
                MovementEvent(
                    timestamp = harness.at("09:40"),
                    eventType = MovementEventType.AUTO_STEP_RESET,
                    intervalMinutes = 30,
                ),
            ),
        )
        val settings = harness.settingsRepository.updateSettings {
            it.copy(enabled = true, intervalMinutes = 30, vibration = VibrationLevel.MEDIUM)
        }
        harness.settingsRepository.updateState {
            it.copy(
                nextAlarmAt = harness.at("10:10"),
                nextAlarmKind = AlarmKind.REMINDER,
                pendingReminderAt = harness.at("09:30"),
            )
        }
        val builder = SnapshotBuilder(
            settingsRepository = harness.settingsRepository,
            history = MovementRepository { dao },
            clock = { harness.now },
            zoneProvider = { zone },
        )

        val snapshot = builder.build()

        assertEquals(SyncCodec.VERSION, snapshot.version)
        assertEquals(harness.at("10:00"), snapshot.generatedAtMillis)
        assertEquals("Europe/Berlin", snapshot.zoneId)
        assertEquals(SyncMapping.toSyncSettings(settings), snapshot.settings)
        assertEquals(
            SyncStatus(
                nextAlarmAtMillis = harness.at("10:10"),
                nextAlarmKind = "REMINDER",
                pendingReminderAtMillis = harness.at("09:30"),
                snoozeUntilMillis = 0L,
            ),
            snapshot.status,
        )
        assertEquals(
            listOf(
                SyncEvent(timestampMillis = start, type = SyncEvent.SNOOZED, intervalMinutes = 45),
                SyncEvent(harness.at("09:40"), type = SyncEvent.AUTO_STEP_RESET, intervalMinutes = 30),
            ),
            snapshot.events,
        )
        // So geht er übers Netz.
        assertEquals(snapshot, SyncCodec.decodeSnapshot(SyncCodec.encodeSnapshot(snapshot)))
    }
}
