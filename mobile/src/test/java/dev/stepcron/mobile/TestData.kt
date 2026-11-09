package dev.stepcron.mobile

import dev.stepcron.shared.sync.SyncDay
import dev.stepcron.shared.sync.SyncEvent
import dev.stepcron.shared.sync.SyncSettings
import dev.stepcron.shared.sync.SyncSnapshot
import dev.stepcron.shared.sync.SyncStatus
import java.time.LocalDate
import java.time.ZoneId

/** Testdaten: Zeitplan Montag bis Freitag 08–17 Uhr, Zeitzone Berlin. */
object TestData {
    const val ZONE_ID = "Europe/Berlin"
    val zone: ZoneId = ZoneId.of(ZONE_ID)

    val weekdays: List<SyncDay> = List(7) { SyncDay(enabled = it < 5, startMinute = 8 * 60, endMinute = 17 * 60) }

    fun settings(
        enabled: Boolean = true,
        schedule: List<SyncDay> = weekdays,
        dailyGoal: Int = 8,
        pausedUntilMillis: Long = 0L,
    ) = SyncSettings(
        enabled = enabled,
        intervalMinutes = 45,
        schedule = schedule,
        autoStepEnabled = true,
        snoozeMinutes = 10,
        stepThreshold = 30,
        vibration = "STRONG",
        respectQuietModes = true,
        onlyWhenWorn = true,
        dailyGoal = dailyGoal,
        pausedUntilMillis = pausedUntilMillis,
    )

    fun status(
        nextAlarmAtMillis: Long = 0L,
        nextAlarmKind: String? = null,
        pendingReminderAtMillis: Long = 0L,
        snoozeUntilMillis: Long = 0L,
    ) = SyncStatus(nextAlarmAtMillis, nextAlarmKind, pendingReminderAtMillis, snoozeUntilMillis)

    fun snapshot(
        generatedAtMillis: Long,
        events: List<SyncEvent> = emptyList(),
        settings: SyncSettings = settings(),
        status: SyncStatus = status(),
        zoneId: String = ZONE_ID,
    ) = SyncSnapshot(
        generatedAtMillis = generatedAtMillis,
        zoneId = zoneId,
        settings = settings,
        status = status,
        events = events,
    )

    /** Zeitpunkt in Berlin, z. B. `at("2026-11-09", 10, 30)`. */
    fun at(date: String, hour: Int, minute: Int = 0, zone: ZoneId = this.zone): Long =
        LocalDate.parse(date).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    fun event(type: String, millis: Long, interval: Int = 45) = SyncEvent(millis, type, interval)

    fun done(millis: Long) = event(SyncEvent.COMPLETED_REMINDER, millis)
    fun auto(millis: Long) = event(SyncEvent.AUTO_STEP_RESET, millis)
    fun manual(millis: Long) = event(SyncEvent.MANUAL_RESET, millis)
    fun missed(millis: Long) = event(SyncEvent.MISSED, millis)
    fun snoozed(millis: Long) = event(SyncEvent.SNOOZED, millis)
}
