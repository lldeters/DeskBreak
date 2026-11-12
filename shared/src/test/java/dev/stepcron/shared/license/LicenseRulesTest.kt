package dev.stepcron.shared.license

import dev.stepcron.shared.license.LicenseRules.DAY_MILLIS
import dev.stepcron.shared.license.LicenseRules.SUBSCRIPTION_TRUST_MILLIS
import dev.stepcron.shared.license.LicenseRules.TRIAL_MILLIS
import dev.stepcron.shared.sync.SyncCodec
import dev.stepcron.shared.sync.SyncSettings
import dev.stepcron.shared.sync.SyncSnapshot
import dev.stepcron.shared.sync.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseRulesTest {

    private val start = 1_800_000_000_000L

    @Test
    fun `not started counts as full trial`() {
        assertEquals(LicenseStatus.Trial(start + TRIAL_MILLIS, 3), LicenseRules.status(LicenseRecord(), start))
    }

    @Test
    fun `trial counts down whole days rounded up`() {
        val record = LicenseRecord(trialStartMillis = start)
        assertEquals(3, (LicenseRules.status(record, start + 1) as LicenseStatus.Trial).daysLeft)
        assertEquals(2, (LicenseRules.status(record, start + DAY_MILLIS) as LicenseStatus.Trial).daysLeft)
        assertEquals(1, (LicenseRules.status(record, start + TRIAL_MILLIS - 1) as LicenseStatus.Trial).daysLeft)
    }

    @Test
    fun `trial ends after three days`() {
        val status = LicenseRules.status(LicenseRecord(trialStartMillis = start), start + TRIAL_MILLIS)
        assertEquals(LicenseStatus.Expired(start + TRIAL_MILLIS), status)
        assertFalse(status.isActive)
    }

    @Test
    fun `starting the trial keeps an existing start`() {
        val started = LicenseRules.withTrialStarted(LicenseRecord(), start)
        assertEquals(start, started.trialStartMillis)
        assertEquals(started, LicenseRules.withTrialStarted(started, start + DAY_MILLIS))
    }

    @Test
    fun `a start in the future is moved to now`() {
        val record = LicenseRecord(trialStartMillis = start + 10 * DAY_MILLIS)
        assertEquals(start, LicenseRules.withTrialStarted(record, start).trialStartMillis)
        // Auch ohne Speichern zählt die Testphase höchstens ab jetzt.
        assertEquals(start + TRIAL_MILLIS, (LicenseRules.status(record, start) as LicenseStatus.Trial).endsAtMillis)
    }

    @Test
    fun `lifetime unlock counts forever`() {
        val record = LicenseRules.withOwnCheck(LicenseRecord(trialStartMillis = start), listOf(Unlock.LIFETIME), start)
        assertEquals(LicenseStatus.Unlocked(Unlock.LIFETIME), LicenseRules.status(record, start + 1000 * DAY_MILLIS))
    }

    @Test
    fun `lifetime wins over subscription`() {
        val record = LicenseRules.withOwnCheck(LicenseRecord(), listOf(Unlock.SUBSCRIPTION, Unlock.LIFETIME), start)
        assertEquals(Unlock.LIFETIME.name, record.ownUnlock)
    }

    @Test
    fun `subscription needs confirmation within the trust period`() {
        val record = LicenseRules.withOwnCheck(LicenseRecord(trialStartMillis = start), listOf(Unlock.SUBSCRIPTION), start)
        assertEquals(LicenseStatus.Unlocked(Unlock.SUBSCRIPTION), LicenseRules.status(record, start + SUBSCRIPTION_TRUST_MILLIS))
        assertTrue(LicenseRules.status(record, start + SUBSCRIPTION_TRUST_MILLIS + 1) is LicenseStatus.Expired)
    }

    @Test
    fun `an empty check removes an unlock`() {
        val bought = LicenseRules.withOwnCheck(LicenseRecord(trialStartMillis = start), listOf(Unlock.SUBSCRIPTION), start)
        val cancelled = LicenseRules.withOwnCheck(bought, emptyList(), start + 40 * DAY_MILLIS)
        assertNull(cancelled.ownUnlock)
        assertTrue(LicenseRules.status(cancelled, start + 40 * DAY_MILLIS) is LicenseStatus.Expired)
    }

    @Test
    fun `peer info merges the earliest trial start and the peer unlock`() {
        val watch = LicenseRecord(trialStartMillis = start + DAY_MILLIS)
        val merged = LicenseRules.withPeerInfo(watch, LicenseInfo(trialStartMillis = start, unlock = "LIFETIME", checkedAtMillis = start))
        assertEquals(start, merged.trialStartMillis)
        assertEquals(LicenseStatus.Unlocked(Unlock.LIFETIME), LicenseRules.status(merged, start + 10 * DAY_MILLIS))
    }

    @Test
    fun `peer without trial start keeps ours`() {
        val merged = LicenseRules.withPeerInfo(LicenseRecord(trialStartMillis = start), LicenseInfo())
        assertEquals(start, merged.trialStartMillis)
    }

    @Test
    fun `older peer info does not override newer`() {
        val record = LicenseRecord(peerUnlock = "SUBSCRIPTION", peerCheckedAtMillis = start + 10)
        val merged = LicenseRules.withPeerInfo(record, LicenseInfo(unlock = null, checkedAtMillis = start))
        assertEquals("SUBSCRIPTION", merged.peerUnlock)
        val revoked = LicenseRules.withPeerInfo(record, LicenseInfo(unlock = null, checkedAtMillis = start + 20))
        assertNull(revoked.peerUnlock)
    }

    @Test
    fun `unknown unlock names are ignored`() {
        val merged = LicenseRules.withPeerInfo(LicenseRecord(), LicenseInfo(unlock = "PLATINUM", checkedAtMillis = start))
        assertNull(merged.peerUnlock)
        assertNull(LicenseRules.effectiveUnlock(LicenseRecord(ownUnlock = "PLATINUM", ownCheckedAtMillis = start), start))
    }

    @Test
    fun `info reports only the own check`() {
        val record = LicenseRecord(trialStartMillis = start, ownUnlock = null, ownCheckedAtMillis = start, peerUnlock = "LIFETIME")
        assertEquals(LicenseInfo(trialStartMillis = start, unlock = null, checkedAtMillis = start), LicenseRules.infoFor(record))
    }

    @Test
    fun `products map to unlocks`() {
        Unlock.entries.forEach { assertEquals(it, LicenseProducts.unlockFor(LicenseProducts.productId(it))) }
        assertNull(LicenseProducts.unlockFor("something_else"))
    }

    @Test
    fun `codec round trips and rejects garbage`() {
        val record = LicenseRecord(trialStartMillis = start, ownUnlock = "LIFETIME", ownCheckedAtMillis = start)
        assertEquals(record, LicenseCodec.decodeRecord(LicenseCodec.encodeRecord(record)))
        val info = LicenseInfo(trialStartMillis = start, unlock = "SUBSCRIPTION", checkedAtMillis = start)
        assertEquals(info, LicenseCodec.decodeInfo(LicenseCodec.encodeInfo(info)))
        assertNull(LicenseCodec.decodeRecord("not json"))
        assertNull(LicenseCodec.decodeInfo(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `snapshot carries the license and old snapshots read without it`() {
        val settings = SyncSettings(true, 45, emptyList(), true, 10, 30, "STRONG", true, true, 8, 0L)
        val status = SyncStatus(0L, null, 0L, 0L)
        val info = LicenseInfo(trialStartMillis = start, unlock = "LIFETIME", checkedAtMillis = start)
        val snapshot = SyncSnapshot(generatedAtMillis = start, zoneId = "UTC", settings = settings, status = status, events = emptyList(), license = info)
        assertEquals(info, SyncCodec.decodeSnapshot(SyncCodec.encodeSnapshot(snapshot))?.license)
        assertNull(SyncCodec.decodeSnapshot(SyncCodec.encodeSnapshot(snapshot.copy(license = null)))?.license)
    }
}
