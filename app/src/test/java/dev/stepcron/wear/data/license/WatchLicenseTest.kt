package dev.stepcron.wear.data.license

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.stepcron.shared.license.LicenseInfo
import dev.stepcron.shared.license.LicenseRecord
import dev.stepcron.shared.license.LicenseRules
import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.shared.license.Unlock
import dev.stepcron.wear.domain.AlarmKind
import dev.stepcron.wear.domain.PlannedAlarm
import dev.stepcron.wear.service.EngineHarness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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

/**
 * Lizenz der Uhr mit echtem DataStore und echter Engine (EngineHarness): Testbeginn, Play-Abfragen, Meldungen des
 * Handys und was daraus für Erinnerungen und Abgleich folgt. Montag, 2026-11-09, 09:00, Standardplan Mo–Fr 8–17 Uhr.
 */
class WatchLicenseTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var harness: EngineHarness
    private val license: WatchLicense get() = harness.watchLicense

    @Before
    fun setUp() {
        harness = EngineHarness(folder)
    }

    @After
    fun tearDown() {
        harness.close()
    }

    // ---------------------------------------------------------------------------------------------
    // Testphase
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `the trial starts once and keeps its start`() = runBlocking {
        license.startTrial()
        assertEquals(harness.at("09:00"), harness.licenseRepository.current().trialStartMillis)
        assertEquals(1, harness.licenseChanges)

        harness.now = harness.at("10:00")
        license.startTrial()

        assertEquals(harness.at("09:00"), harness.licenseRepository.current().trialStartMillis)
        assertEquals(1, harness.licenseChanges)
        assertEquals(0, harness.licenseResyncs)
        assertEquals(LicenseStatus.Trial(harness.at("09:00") + LicenseRules.TRIAL_MILLIS, 3), license.status())
    }

    @Test
    fun `the trial ends after three days and the engine stops reminding`() = runBlocking {
        license.startTrial()
        harness.engine.setEnabled(true)
        assertEquals(PlannedAlarm(harness.at("09:45"), AlarmKind.REMINDER), harness.alarms.planned)

        harness.now = harness.at("09:00", THURSDAY)
        assertTrue(license.status() is LicenseStatus.Expired)
        harness.engine.resync()

        assertNull(harness.alarms.planned)
        assertTrue(harness.settingsRepository.currentSettings().enabled)
    }

    // ---------------------------------------------------------------------------------------------
    // Play-Abfrage der Uhr
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a purchase found after the trial unlocks and plans reminders again`() = runBlocking {
        expireTrial()
        harness.engine.setEnabled(true)
        assertNull(harness.alarms.planned)

        license.applyOwnCheck(setOf(Unlock.LIFETIME), harness.now)

        assertEquals(LicenseStatus.Unlocked(Unlock.LIFETIME), license.status())
        assertEquals(1, harness.licenseResyncs)
        assertEquals(PlannedAlarm(harness.at("09:45", THURSDAY), AlarmKind.REMINDER), harness.alarms.planned)
    }

    @Test
    fun `a check without purchases keeps reminders paused without a resync`() = runBlocking {
        expireTrial()
        val changes = harness.licenseChanges

        license.applyOwnCheck(emptySet(), harness.now)

        assertTrue(license.status() is LicenseStatus.Expired)
        assertEquals(harness.now, harness.licenseRepository.current().ownCheckedAtMillis)
        assertEquals(0, harness.licenseResyncs)
        assertEquals(changes + 1, harness.licenseChanges)
    }

    @Test
    fun `an ended subscription pauses reminders`() = runBlocking {
        expireTrial()
        license.applyOwnCheck(setOf(Unlock.SUBSCRIPTION), harness.now)
        harness.engine.setEnabled(true)
        assertEquals(PlannedAlarm(harness.at("09:45", THURSDAY), AlarmKind.REMINDER), harness.alarms.planned)

        harness.now = harness.at("09:10", THURSDAY)
        license.applyOwnCheck(emptySet(), harness.now)

        assertEquals(2, harness.licenseResyncs)
        assertNull(harness.alarms.planned)
    }

    // ---------------------------------------------------------------------------------------------
    // Meldungen des Handys
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `the phone's purchase unlocks the watch, an older message does not undo it`() = runBlocking {
        expireTrial()
        harness.engine.setEnabled(true)

        license.applyPeerInfo(LicenseInfo(trialStartMillis = 0L, unlock = Unlock.SUBSCRIPTION.name, checkedAtMillis = harness.now))
        assertEquals(LicenseStatus.Unlocked(Unlock.SUBSCRIPTION), license.status())
        assertEquals(1, harness.licenseResyncs)
        assertEquals(PlannedAlarm(harness.at("09:45", THURSDAY), AlarmKind.REMINDER), harness.alarms.planned)

        // Überholt von einer älteren Nachricht: bleibt freigeschaltet.
        license.applyPeerInfo(LicenseInfo(unlock = null, checkedAtMillis = harness.now - 60_000L))
        assertEquals(LicenseStatus.Unlocked(Unlock.SUBSCRIPTION), license.status())
        assertEquals(1, harness.licenseResyncs)
    }

    @Test
    fun `an earlier trial start on the phone counts`() = runBlocking {
        license.startTrial()
        harness.engine.setEnabled(true)
        assertEquals(PlannedAlarm(harness.at("09:45"), AlarmKind.REMINDER), harness.alarms.planned)

        // Auf dem Handy läuft die Testphase schon seit vier Tagen.
        license.applyPeerInfo(LicenseInfo(trialStartMillis = harness.now - 4 * LicenseRules.DAY_MILLIS))

        assertEquals(harness.now - 4 * LicenseRules.DAY_MILLIS, harness.licenseRepository.current().trialStartMillis)
        assertTrue(license.status() is LicenseStatus.Expired)
        assertEquals(1, harness.licenseResyncs)
        assertNull(harness.alarms.planned)
    }

    // ---------------------------------------------------------------------------------------------
    // Play-Abfrage im Hintergrund
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a subscription not checked by the watch for a day asks play in the background, at most hourly`() = runBlocking {
        var requests = 0
        val license = WatchLicense(harness.licenseRepository, clock = { harness.now }, requestPlayCheck = { requests++ })
        val now = harness.now
        harness.licenseRepository.update {
            LicenseRecord(
                trialStartMillis = now - 40 * LicenseRules.DAY_MILLIS,
                peerUnlock = Unlock.SUBSCRIPTION.name,
                peerCheckedAtMillis = now - 2 * LicenseRules.DAY_MILLIS,
            )
        }

        assertTrue(license.isActive(now))
        assertTrue(license.isActive(now + 30 * 60_000L))
        assertEquals(1, requests)
        assertTrue(license.isActive(now + 61 * 60_000L))
        assertEquals(2, requests)

        // Nach der eigenen Abfrage ist für einen Tag Ruhe.
        license.applyOwnCheck(setOf(Unlock.SUBSCRIPTION), now + 62 * 60_000L)
        assertTrue(license.isActive(now + 3 * 60 * 60_000L))
        assertEquals(2, requests)
    }

    @Test
    fun `only a subscription without a fresh own check needs a background check`() {
        val now = 100 * LicenseRules.DAY_MILLIS
        val subscription = LicenseRecord(peerUnlock = Unlock.SUBSCRIPTION.name, peerCheckedAtMillis = now)

        assertTrue(WatchLicense.needsPlayCheck(subscription, now))
        assertTrue(WatchLicense.needsPlayCheck(subscription.copy(ownCheckedAtMillis = now - LicenseRules.DAY_MILLIS), now))
        assertFalse(WatchLicense.needsPlayCheck(subscription.copy(ownCheckedAtMillis = now - 60_000L), now))
        // Uhrzeit zurückgestellt: Die eigene Abfrage liegt in der Zukunft.
        assertTrue(WatchLicense.needsPlayCheck(subscription.copy(ownCheckedAtMillis = now + 60_000L), now))
        // Mit Einmalkauf bzw. ganz ohne Freischaltung gibt es nichts zu bestätigen.
        assertFalse(WatchLicense.needsPlayCheck(subscription.copy(ownUnlock = Unlock.LIFETIME.name), now))
        assertFalse(WatchLicense.needsPlayCheck(LicenseRecord(trialStartMillis = 1L), now))
        assertTrue(WatchLicense.needsPlayCheck(LicenseRecord(ownUnlock = Unlock.SUBSCRIPTION.name, ownCheckedAtMillis = 1L), now))
    }

    // ---------------------------------------------------------------------------------------------
    // Speicher
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `an unreadable stored license counts as empty`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            val file = folder.newFile("broken.preferences_pb").apply { delete() }
            val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            dataStore.edit { it[stringPreferencesKey("license_record_v1")] = "{kaputt" }
            val repository = LicenseRepository(dataStore)

            assertEquals(LicenseRecord(), repository.current())
            val stored = repository.update { LicenseRules.withTrialStarted(it, 1_000L) }
            assertEquals(LicenseRecord(trialStartMillis = 1_000L), stored)
            assertEquals(stored, repository.current())
        } finally {
            scope.cancel()
        }
    }

    /** Testphase vor vier Tagen begonnen; die Uhr steht auf Donnerstag 09:00. */
    private suspend fun expireTrial() {
        harness.now = harness.at("09:00", THURSDAY)
        harness.licenseRepository.update { it.copy(trialStartMillis = harness.now - 4 * LicenseRules.DAY_MILLIS) }
        assertTrue(license.status() is LicenseStatus.Expired)
    }

    private companion object {
        const val THURSDAY = "2026-11-12"
    }
}
