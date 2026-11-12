package dev.stepcron.wear.ui

import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.shared.license.Unlock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die Bitte um exakte Alarme auf dem Startbildschirm (ab Android 14 bei neuen Installationen nicht erlaubt). */
class HomeUiStateExactAlarmTest {

    private val denied = SystemStatus(exactAlarmsAllowed = false)
    private val trial = LicenseStatus.Trial(endsAtMillis = 0L, daysLeft = 3)

    private fun state(
        system: SystemStatus = denied,
        enabled: Boolean = true,
        license: LicenseStatus? = trial,
        loading: Boolean = false,
    ) = HomeUiState(loading = loading, enabled = enabled, system = system, license = license)

    @Test
    fun `without exact alarms the home screen asks for them, also with reminders still off`() {
        assertTrue(state().needsExactAlarms)
        // Ein neuer Nutzer hat die Erinnerungen noch aus: Der Eintrag steht schon da und springt beim Einschalten nicht.
        assertTrue(state(enabled = false).needsExactAlarms)
        assertTrue(state(license = null).needsExactAlarms)
        assertTrue(state(license = LicenseStatus.Unlocked(Unlock.LIFETIME)).needsExactAlarms)
    }

    @Test
    fun `allowed exact alarms need nothing`() {
        assertFalse(state(system = SystemStatus()).needsExactAlarms)
    }

    @Test
    fun `not while loading and not after the trial without unlock`() {
        assertFalse(state(loading = true).needsExactAlarms)
        assertFalse(state(license = LicenseStatus.Expired(endedAtMillis = 0L)).needsExactAlarms)
    }

    @Test
    fun `exact alarms are separate from the runtime permissions`() {
        val state = state()

        assertFalse(state.needsPermissions)
        assertTrue(state.needsExactAlarms)
    }
}
