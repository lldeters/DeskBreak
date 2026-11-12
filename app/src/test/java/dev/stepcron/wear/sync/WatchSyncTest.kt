package dev.stepcron.wear.sync

import dev.stepcron.shared.sync.SyncCodec
import dev.stepcron.shared.sync.SyncSnapshot
import dev.stepcron.wear.data.MovementRepository
import dev.stepcron.wear.data.db.FakeMovementDao
import dev.stepcron.wear.service.EngineHarness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.Collections

/** Verzögerter und sofortiger Abgleich mit einer Attrappe des Data Layers (echte Zeit, kurze Verzögerung). */
class WatchSyncTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var harness: EngineHarness
    private lateinit var scope: CoroutineScope
    private lateinit var sync: WatchSync
    private val channel = FakeChannel()

    @Before
    fun setUp() {
        harness = EngineHarness(folder)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val builder = SnapshotBuilder(
            settingsRepository = harness.settingsRepository,
            history = MovementRepository { FakeMovementDao() },
            licenseRepository = harness.licenseRepository,
            clock = { harness.now },
            zoneProvider = { harness.zone },
        )
        sync = WatchSync(builder, channel, scope, debounceMillis = DEBOUNCE_MILLIS)
    }

    @After
    fun tearDown() {
        scope.cancel()
        harness.close()
    }

    @Test
    fun `a burst of requests is published once with the state at publishing time`() = runBlocking {
        harness.settingsRepository.updateSettings { it.copy(dailyGoal = 10) } // DataStore-Datei anlegen
        repeat(10) { sync.requestPublish() }
        // Änderung innerhalb der Wartezeit: Sie steckt schon im einen Snapshot.
        harness.settingsRepository.updateSettings { it.copy(dailyGoal = 12) }

        awaitPublished(1)
        delay(3 * DEBOUNCE_MILLIS)

        assertEquals(1, channel.published.size)
        assertEquals(12, channel.published.single().settings.dailyGoal)
    }

    @Test
    fun `a request after a publish publishes again`() = runBlocking {
        sync.requestPublish()
        awaitPublished(1)
        sync.requestPublish()
        awaitPublished(2)
    }

    @Test
    fun `publishing now replaces a waiting request`() = runBlocking {
        sync.requestPublish()

        assertTrue(sync.publishNow())
        assertEquals(1, channel.published.size)
        delay(3 * DEBOUNCE_MILLIS)
        assertEquals(1, channel.published.size)
    }

    @Test
    fun `without a known phone only explicit requests publish`() = runBlocking {
        channel.hasPhone = false
        sync.requestPublish()
        delay(3 * DEBOUNCE_MILLIS)
        assertEquals(0, channel.published.size)

        assertTrue(sync.publishNow())
        assertEquals(1, channel.published.size)
    }

    @Test
    fun `data layer failures are swallowed`() = runBlocking {
        channel.failure = IOException("kein Handy")
        assertFalse(sync.publishNow())
        sync.requestPublish()
        delay(3 * DEBOUNCE_MILLIS)

        channel.failure = null
        assertTrue(sync.publishNow())
        assertEquals(1, channel.published.size)
    }

    private suspend fun awaitPublished(count: Int) {
        withTimeout(TIMEOUT_MILLIS) {
            while (channel.published.size < count) delay(POLL_MILLIS)
        }
        assertEquals(count, channel.published.size)
    }

    private class FakeChannel : SnapshotChannel {
        @Volatile var hasPhone = true

        @Volatile var failure: Exception? = null
        val published: MutableList<SyncSnapshot> = Collections.synchronizedList(mutableListOf())

        override suspend fun hasPhone(): Boolean {
            failure?.let { throw it }
            return hasPhone
        }

        override suspend fun publishSnapshot(bytes: ByteArray) {
            failure?.let { throw it }
            published += checkNotNull(SyncCodec.decodeSnapshot(bytes))
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 200L
        const val POLL_MILLIS = 10L
        const val TIMEOUT_MILLIS = 5_000L
    }
}
