package dev.stepcron.mobile.license

import dev.stepcron.shared.license.LicenseRecord
import dev.stepcron.shared.license.Unlock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LicenseStoreTest {

    private val dir: File = Files.createTempDirectory("license-store").toFile()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun withoutFileThereIsNoRecord() {
        assertNull(LicenseStore(dir).read())
    }

    @Test
    fun writtenRecordIsReadBack() {
        val record = LicenseRecord(
            trialStartMillis = 1_000L,
            ownUnlock = Unlock.SUBSCRIPTION.name,
            ownCheckedAtMillis = 2_000L,
            peerUnlock = Unlock.LIFETIME.name,
            peerCheckedAtMillis = 3_000L,
        )
        LicenseStore(dir).write(record)
        // Eine neue Instanz liest dieselbe Datei (wie nach einem Neustart).
        assertEquals(record, LicenseStore(dir).read())
    }

    @Test
    fun writingReplacesTheOldRecordAndLeavesNoTemporaryFile() {
        val store = LicenseStore(dir)
        store.write(LicenseRecord(trialStartMillis = 1L))
        store.write(LicenseRecord(trialStartMillis = 2L))
        assertEquals(2L, store.read()?.trialStartMillis)
        assertFalse(File(dir, LicenseStore.FILE_NAME + ".tmp").exists())
    }

    @Test
    fun brokenFileReadsAsNoRecord() {
        File(dir, LicenseStore.FILE_NAME).writeText("{ kaputt")
        assertNull(LicenseStore(dir).read())
    }

    @Test
    fun missingDirectoryIsCreated() {
        val nested = File(dir, "a/b")
        LicenseStore(nested).write(LicenseRecord(trialStartMillis = 5L))
        assertEquals(5L, LicenseStore(nested).read()?.trialStartMillis)
    }
}
