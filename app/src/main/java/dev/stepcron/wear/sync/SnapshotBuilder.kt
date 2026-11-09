package dev.stepcron.wear.sync

import dev.stepcron.shared.sync.SyncCodec
import dev.stepcron.shared.sync.SyncSnapshot
import dev.stepcron.wear.data.MovementHistory
import dev.stepcron.wear.data.settings.SettingsRepository
import dev.stepcron.wear.domain.toLocalDate
import java.time.ZoneId

/** Stellt den Stand der Uhr fürs Handy zusammen: Einstellungen, Status und die Historie der letzten Tage. */
class SnapshotBuilder(
    private val settingsRepository: SettingsRepository,
    private val history: MovementHistory,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
) {

    suspend fun build(): SyncSnapshot {
        val now = clock()
        val zone = zoneProvider()
        return SyncSnapshot(
            generatedAtMillis = now,
            zoneId = zone.id,
            settings = SyncMapping.toSyncSettings(settingsRepository.currentSettings()),
            status = SyncMapping.toSyncStatus(settingsRepository.currentState()),
            events = history.eventsSince(historyStart(now, zone)).map(SyncMapping::toSyncEvent),
        )
    }

    companion object {
        /** Beginn der Historie im Snapshot: Mitternacht vor [SyncCodec.HISTORY_DAYS] Tagen, heute mitgezählt. */
        fun historyStart(nowMillis: Long, zone: ZoneId): Long {
            val firstDay = nowMillis.toLocalDate(zone).minusDays(SyncCodec.HISTORY_DAYS - 1L)
            return firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }
}
