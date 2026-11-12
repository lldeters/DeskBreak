package dev.stepcron.wear.ui

import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.shared.license.Unlock
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.ReminderState
import dev.stepcron.wear.domain.AlarmKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Der Hauptbildschirm mit Lizenz: Montag, 2026-11-09, Standardplan Mo–Fr 8–17 Uhr. */
class HomeUiStateLicenseTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val enabled = ReminderSettings(enabled = true)
    private val scheduled = ReminderState(
        nextAlarmAt = at("10:45"),
        nextAlarmKind = AlarmKind.REMINDER,
        cycleStartAt = at("10:00"),
        pendingReminderAt = at("10:00"),
    )
    private val expired = LicenseStatus.Expired(endedAtMillis = at("09:00"))

    private fun at(time: String): Long = LocalDateTime.parse("2026-11-09T$time").atZone(zone).toInstant().toEpochMilli()

    private fun create(settings: ReminderSettings, license: LicenseStatus?) =
        HomeUiState.create(settings, scheduled, at("10:15"), SystemStatus(), zone, license = license)

    @Test
    fun `after the trial switched on reminders are locked`() {
        val state = create(enabled, expired)

        assertEquals(HomeStatus.Locked, state.status)
        assertTrue(state.licenseExpired)
        assertFalse(state.reminderPending)
        // Die Einstellung selbst bleibt an.
        assertTrue(state.enabled)
    }

    @Test
    fun `after the trial switched off reminders stay off, the card still shows`() {
        val state = create(ReminderSettings(enabled = false), expired)

        assertEquals(HomeStatus.Disabled, state.status)
        assertTrue(state.licenseExpired)
    }

    @Test
    fun `during the trial, when unlocked or not yet known the status is the usual one`() {
        listOf(
            null,
            LicenseStatus.Trial(endsAtMillis = at("12:00"), daysLeft = 1),
            LicenseStatus.Unlocked(Unlock.SUBSCRIPTION),
        ).forEach { license ->
            val state = create(enabled, license)
            assertTrue("$license", state.status is HomeStatus.Scheduled)
            assertFalse(state.licenseExpired)
            assertTrue(state.reminderPending)
            assertEquals(license, state.license)
        }
    }
}
