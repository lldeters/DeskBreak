package dev.stepcron.mobile.sync

import dev.stepcron.mobile.TestData
import dev.stepcron.shared.sync.SyncBackup
import dev.stepcron.shared.sync.SyncCodec
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SnapshotCacheTest {

    private val dir: File = Files.createTempDirectory("snapshot-cache").toFile()

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun emptyCacheReadsNull() {
        assertNull(SnapshotCache(dir).read())
    }

    @Test
    fun roundTripKeepsBytesAndReceiveTime() {
        val bytes = SyncCodec.encodeSnapshot(TestData.snapshot(generatedAtMillis = 42L, events = listOf(TestData.done(1L))))
        val cache = SnapshotCache(dir)
        cache.write(bytes, receivedAtMillis = 1234L)
        val entry = SnapshotCache(dir).read()!!
        assertArrayEquals(bytes, entry.bytes)
        assertEquals(1234L, entry.receivedAtMillis)
        assertEquals(TestData.snapshot(generatedAtMillis = 42L, events = listOf(TestData.done(1L))), SyncCodec.decodeSnapshot(entry.bytes))
        // Keine Hilfsdatei bleibt liegen.
        assertEquals(listOf(SnapshotCache.FILE_NAME), dir.list()!!.toList())
    }

    @Test
    fun writeReplacesTheOldEntry() {
        val cache = SnapshotCache(dir)
        cache.write(byteArrayOf(1, 2, 3), 1L)
        cache.write(byteArrayOf(4), 2L)
        assertEquals(SnapshotCache.Entry(byteArrayOf(4), 2L), cache.read())
    }

    @Test
    fun createsAMissingDirectory() {
        val nested = File(dir, "a/b")
        SnapshotCache(nested).write(byteArrayOf(9), 3L)
        assertEquals(SnapshotCache.Entry(byteArrayOf(9), 3L), SnapshotCache(nested).read())
    }

    @Test
    fun corruptOrTruncatedFilesReadNull() {
        val cache = SnapshotCache(dir)
        val file = File(dir, SnapshotCache.FILE_NAME)
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        assertNull(cache.read())
        cache.write(ByteArray(100) { it.toByte() }, 5L)
        file.writeBytes(file.readBytes().copyOf(50))
        assertNull(cache.read())
    }

    @Test
    fun clearRemovesTheEntry() {
        val cache = SnapshotCache(dir)
        cache.write(byteArrayOf(1), 1L)
        cache.clear()
        assertNull(cache.read())
        assertFalse(File(dir, SnapshotCache.FILE_NAME).exists())
    }

    @Test
    fun decodingDistinguishesNewerFormatsFromBrokenData() {
        val snapshot = TestData.snapshot(generatedAtMillis = 1L)
        assertEquals(Decoded.Ok(snapshot), SyncDecoding.snapshot(SyncCodec.encodeSnapshot(snapshot)))
        assertEquals(Decoded.TooNew(2), SyncDecoding.snapshot(SyncCodec.encodeSnapshot(snapshot.copy(version = 2))))
        assertEquals(Decoded.Invalid, SyncDecoding.snapshot(byteArrayOf(1, 2, 3)))
        val sixDays = snapshot.copy(settings = snapshot.settings.copy(schedule = snapshot.settings.schedule.take(6)))
        assertEquals(Decoded.Invalid, SyncDecoding.snapshot(SyncCodec.encodeSnapshot(sixDays)))

        val backup = SyncBackup(createdAtMillis = 1L, zoneId = TestData.ZONE_ID, settings = snapshot.settings, events = emptyList())
        assertEquals(Decoded.Ok(backup), SyncDecoding.backup(SyncCodec.encodeBackup(backup)))
        assertEquals(Decoded.TooNew(3), SyncDecoding.backup(SyncCodec.encodeBackup(backup.copy(version = 3))))
        assertEquals(Decoded.Invalid, SyncDecoding.backup("{}".toByteArray()))
        assertEquals(Decoded.Invalid, SyncDecoding.backup("not json".toByteArray()))
        assertTrue(SyncDecoding.backup(ByteArray(0)) is Decoded.Invalid)
    }
}
