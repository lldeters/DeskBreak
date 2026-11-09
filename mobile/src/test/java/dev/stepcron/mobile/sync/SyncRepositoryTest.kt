package dev.stepcron.mobile.sync

import dev.stepcron.mobile.TestData
import dev.stepcron.shared.sync.RestoreResult
import dev.stepcron.shared.sync.SettingsUpdate
import dev.stepcron.shared.sync.SyncBackup
import dev.stepcron.shared.sync.SyncCodec
import dev.stepcron.shared.sync.SyncPaths
import dev.stepcron.shared.sync.SyncSettings
import dev.stepcron.shared.sync.SyncSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/** Repository mit einer Attrappe des Data Layers: Abgleich, Einstellungen mit Bestätigung, Wiederherstellung. */
class SyncRepositoryTest {

    private val dir: File = Files.createTempDirectory("sync-repository").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gateway = FakeGateway()
    private val timing = SyncTiming(
        syncWaitMillis = 400L,
        sendDebounceMillis = 30L,
        confirmTimeoutMillis = 400L,
        restoreTimeoutMillis = 400L,
        clockSkewMillis = 2_000L,
        foregroundRefreshMillis = 0L,
    )

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun repository(): SyncRepository = SyncRepository(gateway, SnapshotCache(dir), scope, timing = timing)

    private fun now() = System.currentTimeMillis()

    private fun <T> awaitValue(flow: Flow<T>, predicate: (T) -> Boolean): T =
        runBlocking { withTimeout(3_000L) { flow.first(predicate) } }

    private fun bytes(snapshot: SyncSnapshot) = SyncCodec.encodeSnapshot(snapshot)

    /** Lässt die „Uhr" auf REQUEST_SYNC mit einem Stand antworten, wie es der Vordergrund-Listener zustellt. */
    private fun watchAnswers(repository: SyncRepository, snapshot: () -> SyncSnapshot) {
        gateway.onRequestSync = { repository.onSnapshotReceived(bytes(snapshot())) }
    }

    @Test
    fun startsWithTheCachedSnapshot() {
        val cached = TestData.snapshot(generatedAtMillis = 1_000L)
        SnapshotCache(dir).write(bytes(cached), receivedAtMillis = 2_000L)
        val state = awaitValue(repository().snapshot) { it.loaded }
        assertEquals(cached, state.snapshot)
        assertEquals(2_000L, state.receivedAtMillis)
    }

    @Test
    fun withoutCacheTheStateIsLoadedButEmpty() {
        val state = awaitValue(repository().snapshot) { it.loaded }
        assertNull(state.snapshot)
    }

    @Test
    fun refreshWithoutWatchReportsNoWatch() = runBlocking {
        gateway.nodes = WatchNodes(emptyList(), emptyList())
        val repository = repository()
        repository.refresh().join()
        assertEquals(SyncProblem.NoWatch, repository.sync.value.problem)
        assertFalse(repository.sync.value.syncing)
        assertTrue(gateway.sent.isEmpty())
    }

    @Test
    fun missingPlayServicesAreReported() = runBlocking {
        gateway.availability = SyncProblem.PlayServicesMissing
        val repository = repository()
        repository.refresh().join()
        assertEquals(SyncProblem.PlayServicesMissing, repository.sync.value.problem)
    }

    @Test
    fun gatewayErrorsBecomeProblems() = runBlocking {
        gateway.findError = SyncException(SyncProblem.WearApiUnavailable)
        val repository = repository()
        repository.refresh().join()
        assertEquals(SyncProblem.WearApiUnavailable, repository.sync.value.problem)
        assertFalse(repository.sync.value.syncing)
    }

    @Test
    fun connectedWatchWithoutTheAppGetsTheRequestButIsReported() = runBlocking {
        gateway.nodes = WatchNodes(withApp = emptyList(), connected = listOf(WatchNode("n1", "Watch")))
        val repository = repository()
        repository.refresh().join()
        assertEquals(SyncProblem.AppMissingOnWatch, repository.sync.value.problem)
        assertEquals(listOf(SyncPaths.REQUEST_SYNC to "n1"), gateway.sent.map { it.path to it.nodeId })
    }

    @Test
    fun watchWithoutDataReportsNoDataYet() = runBlocking {
        val repository = repository()
        repository.refresh().join()
        assertEquals(SyncProblem.NoDataYet, repository.sync.value.problem)
    }

    @Test
    fun refreshTakesTheStoredSnapshotAndThenTheAnswer() = runBlocking {
        gateway.stored += bytes(TestData.snapshot(generatedAtMillis = 1_000L))
        val repository = repository()
        val answer = TestData.snapshot(generatedAtMillis = now(), events = listOf(TestData.done(5L)))
        watchAnswers(repository) { answer }
        repository.refresh().join()
        assertNull(repository.sync.value.problem)
        assertEquals(answer, repository.snapshot.value.snapshot)
        assertEquals(answer, SyncCodec.decodeSnapshot(SnapshotCache(dir).read()!!.bytes))
    }

    @Test
    fun olderStoredSnapshotDoesNotReplaceTheCache() = runBlocking {
        val cached = TestData.snapshot(generatedAtMillis = 2_000L)
        SnapshotCache(dir).write(bytes(cached), 3_000L)
        gateway.stored += bytes(TestData.snapshot(generatedAtMillis = 1_000L))
        val repository = repository()
        repository.refresh().join()
        assertEquals(cached, repository.snapshot.value.snapshot)
        assertEquals(SyncProblem.WatchDidNotAnswer, repository.sync.value.problem)
    }

    @Test
    fun newerFormatFromTheWatchIsReported() = runBlocking {
        val repository = repository()
        watchAnswers(repository) { TestData.snapshot(generatedAtMillis = now()).copy(version = 2) }
        repository.refresh().join()
        assertEquals(SyncProblem.DataTooNew(2), repository.sync.value.problem)
        assertNull(repository.snapshot.value.snapshot)
    }

    @Test
    fun duplicateEventsAreIgnored() = runBlocking {
        val repository = repository()
        val data = bytes(TestData.snapshot(generatedAtMillis = 10L))
        assertTrue(repository.ingestSnapshot(data))
        assertFalse(repository.ingestSnapshot(data))
    }

    @Test
    fun settingsChangesShowAtOnceAndGoOutAsOneDiff() = runBlocking {
        val repository = repository()
        val initial = TestData.snapshot(generatedAtMillis = 10L)
        repository.ingestSnapshot(bytes(initial))
        val confirmed = initial.settings.copy(intervalMinutes = 50, dailyGoal = 9)
        watchAnswers(repository) { TestData.snapshot(generatedAtMillis = now(), settings = confirmed) }

        repository.changeSettings(SettingsUpdate(intervalMinutes = 50))
        repository.changeSettings(SettingsUpdate(dailyGoal = 9))
        // Ein unveränderter Wert kommt nicht mit.
        repository.changeSettings(SettingsUpdate(snoozeMinutes = 10))
        assertEquals(50, repository.currentSettings()?.intervalMinutes)
        assertEquals(9, awaitValue(repository.settings) { it?.dailyGoal == 9 }?.dailyGoal)

        awaitValue(repository.settingsSend) { it is SettingsSendState.Confirmed }

        val updates = gateway.sent.filter { it.path == SyncPaths.UPDATE_SETTINGS }
        assertEquals(1, updates.size)
        assertEquals(SettingsUpdate(intervalMinutes = 50, dailyGoal = 9), SyncCodec.decodeSettingsUpdate(updates.single().data))
        // Danach wird um einen neuen Stand gebeten.
        assertEquals(SyncPaths.REQUEST_SYNC, gateway.sent.last().path)
        assertEquals(confirmed, repository.currentSettings())
    }

    @Test
    fun aSnapshotWithOldValuesFromBeforeTheChangeDoesNotConfirm() = runBlocking {
        val repository = repository()
        val initial = TestData.snapshot(generatedAtMillis = 10L)
        repository.ingestSnapshot(bytes(initial))
        // Die Uhr schickt prompt einen Stand, der aber noch die alten Werte zeigt (kurz vor der Änderung erzeugt).
        watchAnswers(repository) { TestData.snapshot(generatedAtMillis = now() - 1_000L) }
        repository.changeSettings(SettingsUpdate(enabled = false))
        val state = awaitValue(repository.settingsSend) { it == SettingsSendState.Unconfirmed }
        assertEquals(SettingsSendState.Unconfirmed, state)
        // Ohne Bestätigung zeigt die Anzeige wieder den Stand der Uhr.
        assertEquals(true, repository.currentSettings()?.enabled)
    }

    @Test
    fun unchangedValuesSendNothing() = runBlocking {
        val repository = repository()
        repository.ingestSnapshot(bytes(TestData.snapshot(generatedAtMillis = 10L)))
        repository.changeSettings(SettingsUpdate(intervalMinutes = 45, enabled = true))
        Thread.sleep(150)
        assertEquals(SettingsSendState.Idle, repository.settingsSend.value)
        assertTrue(gateway.sent.isEmpty())
    }

    @Test
    fun changesWithoutSnapshotAreIgnored() = runBlocking {
        val repository = repository()
        repository.awaitLoaded()
        repository.changeSettings(SettingsUpdate(enabled = false))
        assertEquals(SettingsSendState.Idle, repository.settingsSend.value)
        assertNull(repository.currentSettings())
    }

    @Test
    fun failedSendRevertsTheDisplayAndCanBeRetried() = runBlocking {
        val repository = repository()
        repository.ingestSnapshot(bytes(TestData.snapshot(generatedAtMillis = 10L)))
        gateway.sendError = SyncException(SyncProblem.NoWatch)
        repository.changeSettings(SettingsUpdate(dailyGoal = 12))
        val failed = awaitValue(repository.settingsSend) { it is SettingsSendState.Failed } as SettingsSendState.Failed
        assertEquals(SyncProblem.NoWatch, failed.problem)
        assertEquals(SettingsUpdate(dailyGoal = 12), failed.update)
        assertEquals(8, repository.currentSettings()?.dailyGoal)

        gateway.sendError = null
        repository.retrySettings()
        assertEquals(12, repository.currentSettings()?.dailyGoal)
        awaitValue(repository.settingsSend) { it == SettingsSendState.AwaitingConfirmation }
        assertEquals(1, gateway.sent.count { it.path == SyncPaths.UPDATE_SETTINGS })
    }

    @Test
    fun missingConfirmationBecomesUnconfirmed() = runBlocking {
        val repository = repository()
        repository.ingestSnapshot(bytes(TestData.snapshot(generatedAtMillis = 10L)))
        repository.changeSettings(SettingsUpdate(intervalMinutes = 30))
        awaitValue(repository.settingsSend) { it == SettingsSendState.Unconfirmed }
        assertEquals(45, repository.currentSettings()?.intervalMinutes)

        // Kommt der Stand später doch noch, gilt die Änderung als bestätigt.
        val later = TestData.snapshot(generatedAtMillis = now(), settings = TestData.settings().copy(intervalMinutes = 30))
        repository.onSnapshotReceived(bytes(later))
        awaitValue(repository.settingsSend) { it is SettingsSendState.Confirmed }
        assertEquals(30, repository.currentSettings()?.intervalMinutes)
    }

    @Test
    fun restoreWaitsForTheWatchAndCleansUp() = runBlocking {
        val repository = repository()
        val backup = backup()
        gateway.onPutRestore = { id, data ->
            assertEquals(backup, SyncCodec.decodeBackup(data))
            repository.onRestoreResultReceived(SyncCodec.encodeRestoreResult(RestoreResult(id, success = true, restoredEvents = 2)))
        }
        repository.startRestore(backup)
        assertEquals(RestoreState.Done(2), awaitValue(repository.restore) { it is RestoreState.Done })
        awaitValue(gateway.deletedFlow) { it.isNotEmpty() }
        assertEquals(gateway.putIds, gateway.deleted.toList())
    }

    @Test
    fun restoreWithoutAnswerTimesOutAndRemovesTheItem() = runBlocking {
        val repository = repository()
        repository.startRestore(backup())
        val failed = awaitValue(repository.restore) { it is RestoreState.Failed }
        assertEquals(RestoreState.Failed(SyncProblem.WatchDidNotAnswer), failed)
        awaitValue(gateway.deletedFlow) { it.isNotEmpty() }
        assertEquals(gateway.putIds, gateway.deleted.toList())
    }

    @Test
    fun restoreRejectedByTheWatch() = runBlocking {
        val repository = repository()
        gateway.onPutRestore = { id, _ ->
            repository.onRestoreResultReceived(SyncCodec.encodeRestoreResult(RestoreResult(id, false, 0, "kaputt")))
        }
        repository.startRestore(backup())
        assertEquals(
            RestoreState.Failed(SyncProblem.RejectedByWatch("kaputt")),
            awaitValue(repository.restore) { it is RestoreState.Failed },
        )
    }

    @Test
    fun restoreNeedsAWatchWithTheApp() = runBlocking {
        gateway.nodes = WatchNodes(withApp = emptyList(), connected = listOf(WatchNode("n1", "Watch")))
        val repository = repository()
        repository.startRestore(backup())
        assertEquals(RestoreState.Failed(SyncProblem.AppMissingOnWatch), awaitValue(repository.restore) { it is RestoreState.Failed })
        assertTrue(gateway.putIds.isEmpty())
        repository.clearRestoreState()
        assertEquals(RestoreState.Idle, repository.restore.value)

        gateway.nodes = WatchNodes(emptyList(), emptyList())
        repository.startRestore(backup())
        assertEquals(RestoreState.Failed(SyncProblem.NoWatch), awaitValue(repository.restore) { it is RestoreState.Failed })
    }

    @Test
    fun resultsForOtherRestoresAreIgnored() = runBlocking {
        val repository = repository()
        gateway.onPutRestore = { _, _ ->
            repository.onRestoreResultReceived(SyncCodec.encodeRestoreResult(RestoreResult("other", true, 1)))
            repository.onRestoreResultReceived(byteArrayOf(1, 2, 3))
        }
        repository.startRestore(backup())
        assertEquals(RestoreState.Failed(SyncProblem.WatchDidNotAnswer), awaitValue(repository.restore) { it is RestoreState.Failed })
    }

    @Test
    fun foregroundRegistersTheListenerAndRequestsData() = runBlocking {
        val repository = repository()
        repository.onForeground()
        assertTrue(gateway.listener === repository)
        awaitValue(repository.sync) { it.lastAttemptAtMillis != null && !it.syncing }
        assertEquals(SyncPaths.REQUEST_SYNC, gateway.sent.first().path)
        repository.onBackground()
        assertNull(gateway.listener)
    }

    private fun backup(settings: SyncSettings = TestData.settings()) = SyncBackup(
        createdAtMillis = 1L,
        zoneId = TestData.ZONE_ID,
        settings = settings,
        events = listOf(TestData.done(1L), TestData.missed(2L)),
    )

    /** Eine gesendete Message. */
    class Sent(val nodeId: String, val path: String, val data: ByteArray)

    /** Attrappe des Data Layers. */
    class FakeGateway : WatchGateway {
        var availability: SyncProblem? = null
        var nodes = WatchNodes(withApp = listOf(WatchNode("w1", "Watch")), connected = listOf(WatchNode("w1", "Watch")))
        var findError: SyncException? = null
        val stored = CopyOnWriteArrayList<ByteArray>()
        val sent = CopyOnWriteArrayList<Sent>()
        var sendError: SyncException? = null
        var onRequestSync: (() -> Unit)? = null
        var onPutRestore: ((String, ByteArray) -> Unit)? = null
        val putIds = CopyOnWriteArrayList<String>()
        val deleted = CopyOnWriteArrayList<String>()
        val deletedFlow = MutableStateFlow<List<String>>(emptyList())

        @Volatile
        var listener: GatewayListener? = null

        override suspend fun checkAvailability(): SyncProblem? = availability

        override suspend fun findWatches(): WatchNodes = findError?.let { throw it } ?: nodes

        override suspend fun readSnapshots(): List<ByteArray> = stored.toList()

        override suspend fun sendMessage(nodeIds: List<String>, path: String, data: ByteArray) {
            sendError?.let { throw it }
            nodeIds.forEach { sent += Sent(it, path, data) }
            if (path == SyncPaths.REQUEST_SYNC) onRequestSync?.invoke()
        }

        override suspend fun putRestore(restoreId: String, backupBytes: ByteArray) {
            putIds += restoreId
            onPutRestore?.invoke(restoreId, backupBytes)
        }

        override suspend fun deleteRestore(restoreId: String) {
            deleted += restoreId
            deletedFlow.value = deleted.toList()
        }

        override fun startListening(listener: GatewayListener) {
            this.listener = listener
        }

        override fun stopListening() {
            listener = null
        }
    }
}
