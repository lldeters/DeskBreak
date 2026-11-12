package dev.stepcron.mobile.license

import dev.stepcron.billing.BillingProblem
import dev.stepcron.mobile.sync.SyncException
import dev.stepcron.mobile.sync.SyncProblem
import dev.stepcron.mobile.sync.SyncRepositoryTest
import dev.stepcron.shared.license.LicenseCodec
import dev.stepcron.shared.license.LicenseInfo
import dev.stepcron.shared.license.LicenseRecord
import dev.stepcron.shared.license.LicenseRules
import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.shared.license.Unlock
import dev.stepcron.shared.sync.SyncPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong

/** Lizenz auf dem Handy mit Attrappen von Play Billing und Data Layer: Testphase, Play-Abfragen, Uhr. */
class LicenseRepositoryTest {

    private val dir: File = Files.createTempDirectory("license-repository").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gateway = SyncRepositoryTest.FakeGateway()
    private val now = AtomicLong(START)
    private val clock = { now.get() }
    private val billing = FakeBilling(clock)
    private val timing = LicenseTiming(
        staleAfterMillis = LicenseRules.DAY_MILLIS,
        backgroundRetryMillis = HOUR,
        playTimeoutMillis = 2_000L,
        sendTimeoutMillis = 2_000L,
    )

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun repository(): LicenseRepository =
        LicenseRepository(LicenseStore(dir), billing, gateway, scope, clock = clock, timing = timing)

    /** Repository mit schon gespeichertem Stand [record]; kehrt zurück, wenn er gelesen ist. */
    private fun repository(record: LicenseRecord): LicenseRepository {
        LicenseStore(dir).write(record)
        return repository().also { runBlocking { it.awaitLoaded() } }
    }

    private fun <T> awaitValue(flow: Flow<T>, predicate: (T) -> Boolean): T =
        runBlocking { withTimeout(3_000L) { flow.first(predicate) } }

    private fun eventually(condition: () -> Boolean) = runBlocking {
        withTimeout(3_000L) { while (!condition()) delay(10L) }
    }

    /** Was das Handy der Uhr gemeldet hat. */
    private fun licenseMessages(): List<LicenseInfo> =
        gateway.sent.filter { it.path == SyncPaths.LICENSE }.map { LicenseCodec.decodeInfo(it.data)!! }

    // --- Testphase ---

    @Test
    fun firstStartBeginsTheTrialSavesItAndTellsTheWatch() {
        val repository = repository()
        val record = awaitValue(repository.record) { it != null }!!
        assertEquals(START, record.trialStartMillis)
        assertEquals(START, LicenseStore(dir).read()?.trialStartMillis)
        eventually { licenseMessages().isNotEmpty() }
        assertEquals(LicenseInfo(trialStartMillis = START), licenseMessages().single())
    }

    @Test
    fun laterStartsKeepTheTrialStartAndStayQuiet() {
        val repository = repository(LicenseRecord(trialStartMillis = START - LicenseRules.DAY_MILLIS))
        assertEquals(START - LicenseRules.DAY_MILLIS, repository.record.value?.trialStartMillis)
        Thread.sleep(QUIET_MILLIS)
        assertTrue(licenseMessages().isEmpty())
    }

    @Test
    fun trialStartInTheFutureIsCorrectedAndReported() {
        // Gespeicherter Beginn liegt in der Zukunft (Uhrzeit zurückgestellt): gilt ab jetzt.
        val repository = repository(LicenseRecord(trialStartMillis = START + LicenseRules.DAY_MILLIS))
        assertEquals(START, repository.record.value?.trialStartMillis)
        assertEquals(START, LicenseStore(dir).read()?.trialStartMillis)
        eventually { licenseMessages().isNotEmpty() }
        assertEquals(START, licenseMessages().last().trialStartMillis)
    }

    // --- Google Play ---

    @Test
    fun successfulPlayQueryIsSavedAndSentToTheWatch() {
        val repository = repository(LicenseRecord(trialStartMillis = START))
        billing.owns(Unlock.SUBSCRIPTION)
        now.addAndGet(1_000L)
        repository.refreshPlay()
        val record = awaitValue(repository.record) { it?.ownUnlock == Unlock.SUBSCRIPTION.name }!!
        assertEquals(START + 1_000L, record.ownCheckedAtMillis)
        assertEquals(record, LicenseStore(dir).read())
        assertEquals(LicenseStatus.Unlocked(Unlock.SUBSCRIPTION), LicenseRules.status(record, now.get()))
        eventually { licenseMessages().isNotEmpty() }
        assertEquals(LicenseInfo(START, Unlock.SUBSCRIPTION.name, START + 1_000L), licenseMessages().last())
    }

    @Test
    fun failedPlayQueryChangesNothing() {
        val before = LicenseRecord(trialStartMillis = START, ownUnlock = Unlock.LIFETIME.name, ownCheckedAtMillis = START - HOUR)
        val repository = repository(before)
        billing.fails(BillingProblem.NETWORK)
        repository.refreshPlay()
        eventually { billing.refreshes.get() == 1 }
        Thread.sleep(QUIET_MILLIS)
        assertEquals(before, repository.record.value)
        assertTrue(licenseMessages().isEmpty())
    }

    @Test
    fun purchaseReportedByPlayUnlocksWithoutAQuery() {
        val repository = repository(LicenseRecord(trialStartMillis = START - 5 * LicenseRules.DAY_MILLIS))
        assertTrue(LicenseRules.status(repository.record.value!!, now.get()) is LicenseStatus.Expired)
        billing.purchased(Unlock.LIFETIME)
        val record = awaitValue(repository.record) { it?.ownUnlock == Unlock.LIFETIME.name }!!
        assertEquals(LicenseStatus.Unlocked(Unlock.LIFETIME), LicenseRules.status(record, now.get()))
        eventually { licenseMessages().any { it.unlock == Unlock.LIFETIME.name } }
    }

    @Test
    fun theSamePlayResultIsTakenOverOnlyOnce() {
        val repository = repository(LicenseRecord(trialStartMillis = START))
        billing.purchased(Unlock.SUBSCRIPTION)
        eventually { licenseMessages().size == 1 }
        // Nur „ausstehend" ändert sich, nicht die Abfrage selbst: keine neue Meldung.
        billing.state.value = billing.state.value.copy(pending = setOf(Unlock.LIFETIME))
        Thread.sleep(QUIET_MILLIS)
        assertEquals(1, licenseMessages().size)
        assertEquals(Unlock.SUBSCRIPTION.name, repository.record.value?.ownUnlock)
    }

    @Test
    fun unreachableWatchIsNoProblem() {
        gateway.sendError = SyncException(SyncProblem.NoWatch)
        val repository = repository(LicenseRecord(trialStartMillis = START))
        billing.owns(Unlock.LIFETIME)
        repository.refreshPlay()
        awaitValue(repository.record) { it?.ownUnlock == Unlock.LIFETIME.name }
        // Danach geht es weiter wie gewohnt.
        gateway.sendError = null
        now.addAndGet(1_000L)
        repository.refreshPlay()
        eventually { licenseMessages().any { it.checkedAtMillis == START + 1_000L } }
    }

    @Test
    fun checkPurchasesReportsWhatPlayFound() = runBlocking {
        val repository = repository(LicenseRecord(trialStartMillis = START))
        billing.owns(Unlock.LIFETIME, Unlock.SUBSCRIPTION)
        assertEquals(PurchaseCheck.Found(Unlock.LIFETIME), repository.checkPurchases())
        now.addAndGet(1_000L)
        billing.owns()
        assertEquals(PurchaseCheck.NothingFound, repository.checkPurchases())
        assertEquals(null, repository.record.value?.ownUnlock)
        billing.fails(BillingProblem.NETWORK)
        assertEquals(PurchaseCheck.Failed(BillingProblem.NETWORK), repository.checkPurchases())
    }

    // --- Hintergrund: Abo-Bestätigung frisch halten ---

    @Test
    fun freshCheckIsNotRepeatedInTheBackground() = runBlocking {
        val repository = repository(LicenseRecord(trialStartMillis = START, ownCheckedAtMillis = START - HOUR))
        assertFalse(repository.refreshPlayIfStale())
        assertEquals(0, billing.refreshes.get())
    }

    @Test
    fun staleCheckAsksPlayAndTellsTheWatchBeforeReturning() = runBlocking {
        val repository = repository(
            LicenseRecord(
                trialStartMillis = START - 40 * LicenseRules.DAY_MILLIS,
                ownUnlock = Unlock.SUBSCRIPTION.name,
                ownCheckedAtMillis = START - 2 * LicenseRules.DAY_MILLIS,
            ),
        )
        billing.owns(Unlock.SUBSCRIPTION)
        assertTrue(repository.refreshPlayIfStale())
        assertEquals(1, billing.refreshes.get())
        assertEquals(START, repository.record.value?.ownCheckedAtMillis)
        // Schon gemeldet, wenn die Funktion zurückkehrt (der Dienst wartet genau darauf).
        assertTrue(licenseMessages().any { it.unlock == Unlock.SUBSCRIPTION.name && it.checkedAtMillis == START })
    }

    @Test
    fun failingPlayIsNotAskedAgainRightAway() = runBlocking {
        val repository = repository(LicenseRecord(trialStartMillis = START))
        billing.fails(BillingProblem.SERVICE_UNAVAILABLE)
        assertTrue(repository.refreshPlayIfStale())
        now.addAndGet(HOUR / 2)
        assertFalse(repository.refreshPlayIfStale())
        now.addAndGet(HOUR)
        assertTrue(repository.refreshPlayIfStale())
        assertEquals(2, billing.refreshes.get())
    }

    // --- Uhr ---

    @Test
    fun watchInfoIsMergedSavedAndNotSentBack() = runBlocking {
        val repository = repository(LicenseRecord(trialStartMillis = START))
        val watch = LicenseInfo(trialStartMillis = START - LicenseRules.DAY_MILLIS, unlock = Unlock.LIFETIME.name, checkedAtMillis = START - 10L)
        repository.onWatchLicense(watch)
        val record = repository.record.value!!
        assertEquals(START - LicenseRules.DAY_MILLIS, record.trialStartMillis)
        assertEquals(Unlock.LIFETIME.name, record.peerUnlock)
        assertEquals(record, LicenseStore(dir).read())
        assertEquals(LicenseStatus.Unlocked(Unlock.LIFETIME), LicenseRules.status(record, now.get()))
        // Die eigene Meldung bleibt die eigene Sicht (nicht die weitergereichte der Uhr).
        assertEquals(LicenseInfo(trialStartMillis = START - LicenseRules.DAY_MILLIS), repository.licenseForWatch())
        Thread.sleep(QUIET_MILLIS)
        assertTrue(licenseMessages().isEmpty())
    }

    private companion object {
        const val START = 1_800_000_000_000L
        const val HOUR = 60 * 60_000L

        /** So lange wird gewartet, um zu prüfen, dass etwas *nicht* passiert. */
        const val QUIET_MILLIS = 150L
    }
}
