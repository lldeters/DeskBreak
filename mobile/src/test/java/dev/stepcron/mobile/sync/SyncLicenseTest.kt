package dev.stepcron.mobile.sync

import dev.stepcron.mobile.TestData
import dev.stepcron.shared.license.LicenseCodec
import dev.stepcron.shared.license.LicenseInfo
import dev.stepcron.shared.license.Unlock
import dev.stepcron.shared.sync.SyncCodec
import dev.stepcron.shared.sync.SyncPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/** Abgleich und Lizenz: Die Sicht der Uhr aus Snapshots geht an [LicenseLink], die des Handys mit jedem Abgleich raus. */
class SyncLicenseTest {

    private val dir: File = Files.createTempDirectory("sync-license").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gateway = SyncRepositoryTest.FakeGateway()
    private val link = FakeLink()
    private val timing = SyncTiming(syncWaitMillis = 300L, foregroundRefreshMillis = 0L)

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun repository(license: LicenseLink? = link) =
        SyncRepository(gateway, SnapshotCache(dir), scope, timing = timing, license = license)

    private fun eventually(condition: () -> Boolean) = runBlocking {
        withTimeout(3_000L) { while (!condition()) delay(10L) }
    }

    private val watchInfo = LicenseInfo(trialStartMillis = 1_000L, unlock = Unlock.SUBSCRIPTION.name, checkedAtMillis = 2_000L)

    @Test
    fun licenseOfAReceivedSnapshotGoesToTheLink() = runBlocking {
        val repository = repository()
        val snapshot = TestData.snapshot(generatedAtMillis = 5_000L).copy(license = watchInfo)
        assertTrue(repository.ingestSnapshot(SyncCodec.encodeSnapshot(snapshot)))
        assertEquals(listOf(watchInfo), link.received)
    }

    @Test
    fun licenseOfAnOlderSnapshotIsStillOffered() = runBlocking {
        // withPeerInfo entscheidet selbst, was davon gilt – das Repository reicht jede gelesene Sicht weiter.
        val repository = repository()
        repository.ingestSnapshot(SyncCodec.encodeSnapshot(TestData.snapshot(generatedAtMillis = 5_000L)))
        val older = TestData.snapshot(generatedAtMillis = 4_000L).copy(license = watchInfo)
        gateway.stored += SyncCodec.encodeSnapshot(older)
        repository.refresh().join()
        assertEquals(watchInfo, link.received.distinct().single())
    }

    @Test
    fun snapshotFromAnOldWatchWithoutLicenseIsFine() = runBlocking {
        val repository = repository()
        assertTrue(repository.ingestSnapshot(SyncCodec.encodeSnapshot(TestData.snapshot(generatedAtMillis = 5_000L))))
        assertTrue(link.received.isEmpty())
    }

    @Test
    fun cachedSnapshotLicenseIsOfferedAfterARestart() {
        val cached = TestData.snapshot(generatedAtMillis = 5_000L).copy(license = watchInfo)
        SnapshotCache(dir).write(SyncCodec.encodeSnapshot(cached), receivedAtMillis = 6_000L)
        repository()
        eventually { link.received.isNotEmpty() }
        assertEquals(watchInfo, link.received.single())
    }

    @Test
    fun syncSendsThePhoneLicenseBeforeAskingForASnapshot() = runBlocking {
        link.phone = LicenseInfo(trialStartMillis = 3_000L, unlock = Unlock.LIFETIME.name, checkedAtMillis = 4_000L)
        repository().refresh().join()
        val paths = gateway.sent.map { it.path }
        assertEquals(listOf(SyncPaths.LICENSE, SyncPaths.REQUEST_SYNC), paths)
        assertEquals(link.phone, LicenseCodec.decodeInfo(gateway.sent.first().data))
    }

    @Test
    fun withoutLicenseLinkNothingExtraIsSent() = runBlocking {
        repository(license = null).refresh().join()
        assertEquals(listOf(SyncPaths.REQUEST_SYNC), gateway.sent.map { it.path })
    }

    @Test
    fun brokenLinkDoesNotBreakTheSync() = runBlocking {
        link.failing = true
        val repository = repository()
        val snapshot = TestData.snapshot(generatedAtMillis = 5_000L).copy(license = watchInfo)
        assertTrue(repository.ingestSnapshot(SyncCodec.encodeSnapshot(snapshot)))
        assertEquals(snapshot, repository.snapshot.value.snapshot)
    }

    @Test
    fun sendToWatchesNeedsAReachableWatch() = runBlocking {
        gateway.nodes = WatchNodes(emptyList(), emptyList())
        val error = runCatching { gateway.sendToWatches(SyncPaths.LICENSE, ByteArray(1)) }.exceptionOrNull()
        assertEquals(SyncProblem.NoWatch, (error as SyncException).problem)
        assertTrue(gateway.sent.isEmpty())
    }

    private class FakeLink : LicenseLink {
        val received = CopyOnWriteArrayList<LicenseInfo>()

        @Volatile
        var phone: LicenseInfo? = null

        @Volatile
        var failing = false

        override suspend fun onWatchLicense(info: LicenseInfo) {
            if (failing) throw IllegalStateException("kaputt")
            received += info
        }

        override suspend fun licenseForWatch(): LicenseInfo? = phone
    }
}
