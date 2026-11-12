package dev.stepcron.mobile.screenshots

import dev.stepcron.billing.BillingAvailability
import dev.stepcron.billing.BillingOffer
import dev.stepcron.billing.BillingState
import dev.stepcron.mobile.settings.SettingsRules
import dev.stepcron.mobile.stats.StatsCalculator
import dev.stepcron.mobile.stats.WatchStatusCalculator
import dev.stepcron.mobile.sync.SyncState
import dev.stepcron.mobile.ui.AppUiState
import dev.stepcron.mobile.ui.WatchData
import dev.stepcron.mobile.ui.license.LicenseUiState
import dev.stepcron.shared.license.LicenseRecord
import dev.stepcron.shared.license.LicenseRules
import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.shared.license.Unlock
import dev.stepcron.shared.sync.SyncDay
import dev.stepcron.shared.sync.SyncEvent
import dev.stepcron.shared.sync.SyncSettings
import dev.stepcron.shared.sync.SyncSnapshot
import dev.stepcron.shared.sync.SyncStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Gleichbleibende Beispieldaten für die Screenshots: Dienstag, 10:22 Uhr, gut fünf Wochen Verlauf. */
object SampleData {

    val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val today: LocalDate = LocalDate.of(2026, 11, 10)
    val now: Long = millis(today, 10, 22)

    val settings = SyncSettings(
        enabled = true,
        intervalMinutes = 45,
        schedule = DayOfWeek.entries.map { day ->
            SyncDay(enabled = day <= DayOfWeek.FRIDAY, startMinute = 8 * 60, endMinute = if (day == DayOfWeek.FRIDAY) 15 * 60 else 17 * 60)
        },
        autoStepEnabled = true,
        snoozeMinutes = 10,
        stepThreshold = 30,
        vibration = "STRONG",
        respectQuietModes = true,
        onlyWhenWorn = true,
        dailyGoal = 8,
        pausedUntilMillis = 0L,
    )

    /** Werktage mit wechselndem Erfolg, Wochenenden frei; heute bisher drei Pausen. */
    private fun events(): List<SyncEvent> {
        val events = mutableListOf<SyncEvent>()
        for (offset in 36 downTo 1) {
            val date = today.minusDays(offset.toLong())
            if (date.dayOfWeek >= DayOfWeek.SATURDAY) continue
            val variant = (offset * 7 + date.dayOfMonth) % 5
            val breaks = listOf(9, 7, 5, 8, 3)[variant]
            val missed = listOf(1, 2, 3, 0, 4)[variant]
            repeat(breaks) { i ->
                val type = if (i % 3 == 0) SyncEvent.AUTO_STEP_RESET else SyncEvent.COMPLETED_REMINDER
                events += SyncEvent(millis(date, 8 + i, 30 + (i * 7) % 20), type, 45)
            }
            repeat(missed) { i -> events += SyncEvent(millis(date, 14 + i, 15), SyncEvent.MISSED, 45) }
            if (variant == 1) events += SyncEvent(millis(date, 11, 5), SyncEvent.SNOOZED, 45)
        }
        events += SyncEvent(millis(today, 8, 41), SyncEvent.COMPLETED_REMINDER, 45)
        events += SyncEvent(millis(today, 9, 12), SyncEvent.AUTO_STEP_RESET, 45)
        events += SyncEvent(millis(today, 9, 58), SyncEvent.COMPLETED_REMINDER, 45)
        return events.sortedBy { it.timestampMillis }
    }

    fun snapshot(settings: SyncSettings = this.settings, nextReminderInMinutes: Long = 23): SyncSnapshot = SyncSnapshot(
        generatedAtMillis = now - 2 * 60_000L,
        zoneId = zone.id,
        settings = settings,
        status = SyncStatus(
            nextAlarmAtMillis = now + nextReminderInMinutes * 60_000L,
            nextAlarmKind = WatchStatusCalculator.KIND_REMINDER,
            pendingReminderAtMillis = 0L,
            snoozeUntilMillis = 0L,
        ),
        events = events(),
    )

    /** Zustand der App mit Beispieldaten, wie ihn das ViewModel zusammensetzt. */
    fun state(snapshot: SyncSnapshot? = snapshot(), sync: SyncState = SyncState(lastAttemptAtMillis = now - 60_000L)): AppUiState {
        if (snapshot == null) return AppUiState(data = WatchData(loaded = true, nowMillis = now), sync = sync)
        val settings = snapshot.settings
        return AppUiState(
            data = WatchData(
                loaded = true,
                snapshot = snapshot,
                receivedAtMillis = now - 60_000L,
                settings = settings,
                zone = zone,
                dashboard = StatsCalculator.dashboard(snapshot, now, SettingsRules.clampDailyGoal(settings.dailyGoal)),
                watchStatus = WatchStatusCalculator.compute(snapshot, settings, now, zone),
                nowMillis = now,
            ),
            sync = sync,
        )
    }

    // --- Lizenz ---

    /** Testphase seit gut einem Tag: noch 2 Tage, endet übermorgen früh. */
    val trial: LicenseStatus = LicenseRules.status(LicenseRecord(trialStartMillis = now - 26 * HOUR_MILLIS), now)

    val expired: LicenseStatus = LicenseStatus.Expired(endedAtMillis = now - 3 * HOUR_MILLIS)

    /** Angebote, wie Play sie liefert (Preise je nach Sprache formatiert, wie es Play tut). */
    fun billingReady(
        german: Boolean = false,
        owned: Set<Unlock> = emptySet(),
        pending: Set<Unlock> = emptySet(),
    ): BillingState = BillingState(
        availability = BillingAvailability.READY,
        offers = mapOf(
            Unlock.LIFETIME to BillingOffer(Unlock.LIFETIME, if (german) "5,00 €" else "€5.00"),
            Unlock.SUBSCRIPTION to BillingOffer(Unlock.SUBSCRIPTION, if (german) "1,00 €" else "€1.00", billingPeriod = "P1M"),
        ),
        owned = owned,
        pending = pending,
        checkedAtMillis = now - 60_000L,
    )

    fun license(status: LicenseStatus = trial, billing: BillingState = billingReady(), restoring: Boolean = false) =
        LicenseUiState(status = status, billing = billing, restoring = restoring, nowMillis = now)

    private const val HOUR_MILLIS = 60 * 60_000L

    private fun millis(date: LocalDate, hour: Int, minute: Int): Long =
        LocalDateTime.of(date.year, date.month, date.dayOfMonth, hour, minute).atZone(zone).toInstant().toEpochMilli()
}
