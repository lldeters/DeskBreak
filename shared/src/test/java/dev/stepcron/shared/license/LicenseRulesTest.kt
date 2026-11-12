package dev.stepcron.shared.license

import dev.stepcron.shared.license.LicenseRules.CLOCK_TOLERANCE_MILLIS
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
        val merged = LicenseRules.withPeerInfo(watch, LicenseInfo(trialStartMillis = start, unlock = "LIFETIME", checkedAtMillis = start), start)
        assertEquals(start, merged.trialStartMillis)
        assertEquals(LicenseStatus.Unlocked(Unlock.LIFETIME), LicenseRules.status(merged, start + 10 * DAY_MILLIS))
    }

    @Test
    fun `peer without trial start keeps ours`() {
        val merged = LicenseRules.withPeerInfo(LicenseRecord(trialStartMillis = start), LicenseInfo(), start)
        assertEquals(start, merged.trialStartMillis)
    }

    @Test
    fun `older peer info does not override newer`() {
        val record = LicenseRecord(peerUnlock = "SUBSCRIPTION", peerCheckedAtMillis = start + 10)
        val merged = LicenseRules.withPeerInfo(record, LicenseInfo(unlock = null, checkedAtMillis = start), start + 20)
        assertEquals("SUBSCRIPTION", merged.peerUnlock)
        val revoked = LicenseRules.withPeerInfo(record, LicenseInfo(unlock = null, checkedAtMillis = start + 20), start + 20)
        assertNull(revoked.peerUnlock)
    }

    // ---------------------------------------------------------------------------------------------
    // Abweichende Uhrzeit
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a peer report from a clock far ahead does not freeze the peer state`() {
        // Das andere Gerät ging ein Jahr vor und meldete damals ein Abo; inzwischen ist es gekündigt.
        val record = LicenseRecord(trialStartMillis = start, peerUnlock = "SUBSCRIPTION", peerCheckedAtMillis = start + 365 * DAY_MILLIS)
        val cancelled = LicenseRules.withPeerInfo(record, LicenseInfo(trialStartMillis = start, unlock = null, checkedAtMillis = start), start)
        assertNull(cancelled.peerUnlock)
        assertEquals(start, cancelled.peerCheckedAtMillis)
        // Auch eine Meldung ganz ohne Play-Abfrage löst den falschen Stand.
        val unknown = LicenseRules.withPeerInfo(record, LicenseInfo(trialStartMillis = start), start)
        assertNull(unknown.peerUnlock)
        assertEquals(0L, unknown.peerCheckedAtMillis)
        // Danach gilt wieder die übliche Reihenfolge.
        val renewed = LicenseRules.withPeerInfo(cancelled, LicenseInfo(unlock = "SUBSCRIPTION", checkedAtMillis = start + 10), start + 10)
        assertEquals("SUBSCRIPTION", LicenseRules.withPeerInfo(renewed, LicenseInfo(checkedAtMillis = start + 5), start + 10).peerUnlock)
    }

    @Test
    fun `a slightly early peer report still beats an older one`() {
        // Weniger als die Toleranz voraus: gilt als richtige Zeit, ältere Meldungen bleiben übergangen.
        val record = LicenseRecord(peerUnlock = "SUBSCRIPTION", peerCheckedAtMillis = start + CLOCK_TOLERANCE_MILLIS)
        val older = LicenseRules.withPeerInfo(record, LicenseInfo(unlock = null, checkedAtMillis = start), start)
        assertEquals(record, older)
        val beyond = LicenseRules.withPeerInfo(record, LicenseInfo(unlock = null, checkedAtMillis = start), start - 1)
        assertNull(beyond.peerUnlock)
    }

    @Test
    fun `a subscription confirmed far in the future is not trusted`() {
        val slightlyAhead = LicenseRecord(trialStartMillis = start - 10 * DAY_MILLIS, peerUnlock = "SUBSCRIPTION", peerCheckedAtMillis = start + CLOCK_TOLERANCE_MILLIS)
        assertEquals(LicenseStatus.Unlocked(Unlock.SUBSCRIPTION), LicenseRules.status(slightlyAhead, start))
        val farAhead = slightlyAhead.copy(peerCheckedAtMillis = start + CLOCK_TOLERANCE_MILLIS + 1)
        assertTrue(LicenseRules.status(farAhead, start) is LicenseStatus.Expired)
        val ownFarAhead = LicenseRecord(trialStartMillis = start - 10 * DAY_MILLIS, ownUnlock = "SUBSCRIPTION", ownCheckedAtMillis = start + 400 * DAY_MILLIS)
        assertNull(LicenseRules.effectiveUnlock(ownFarAhead, start))
        // Ein Einmalkauf gilt unabhängig vom Zeitpunkt.
        assertEquals(Unlock.LIFETIME, LicenseRules.effectiveUnlock(ownFarAhead.copy(ownUnlock = "LIFETIME"), start))
    }

    // ---------------------------------------------------------------------------------------------
    // Offene Käufe
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `an own check remembers an open purchase until the next check`() {
        val open = LicenseRules.withOwnCheck(LicenseRecord(trialStartMillis = start), emptyList(), start, openPurchase = true)
        assertTrue(open.ownOpenPurchase)
        assertNull(open.ownUnlock)
        val done = LicenseRules.withOwnCheck(open, listOf(Unlock.LIFETIME), start + 1)
        assertFalse(done.ownOpenPurchase)
        assertEquals("LIFETIME", done.ownUnlock)
        // Nur die eigene Sicht: Das andere Gerät erfährt davon nichts.
        assertEquals(LicenseInfo(start, null, start), LicenseRules.infoFor(open))
    }

    @Test
    fun `unknown unlock names are ignored`() {
        val merged = LicenseRules.withPeerInfo(LicenseRecord(), LicenseInfo(unlock = "PLATINUM", checkedAtMillis = start), start)
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
        val open = record.copy(ownOpenPurchase = true)
        assertEquals(open, LicenseCodec.decodeRecord(LicenseCodec.encodeRecord(open)))
        // Gespeichert von einer Version ohne das Feld: kein offener Kauf.
        assertEquals(record, LicenseCodec.decodeRecord("""{"trialStartMillis":$start,"ownUnlock":"LIFETIME","ownCheckedAtMillis":$start}"""))
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
