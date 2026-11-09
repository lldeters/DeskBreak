package dev.stepcron.mobile.stats

import dev.stepcron.mobile.TestData
import dev.stepcron.mobile.TestData.at
import dev.stepcron.mobile.TestData.done
import dev.stepcron.mobile.TestData.event
import dev.stepcron.mobile.TestData.missed
import dev.stepcron.mobile.TestData.snoozed
import dev.stepcron.shared.sync.SyncCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ExportTest {

    @Test
    fun csvHasHeaderAndOneLinePerEventOldestFirstInTheWatchZone() {
        val snapshot = TestData.snapshot(
            generatedAtMillis = 0L,
            events = listOf(
                snoozed(at("2026-11-09", 10, 5)),
                done(at("2026-11-08", 23, 59)),
                event("MANUAL_RESET", at("2026-11-09", 9, 0), interval = 30),
                missed(at("2026-11-09", 10, 0)),
            ),
        )
        val expected = listOf(
            "date,time,type,interval_minutes",
            "2026-11-08,23:59,COMPLETED_REMINDER,45",
            "2026-11-09,09:00,MANUAL_RESET,30",
            "2026-11-09,10:00,MISSED,45",
            "2026-11-09,10:05,SNOOZED,45",
        ).joinToString("\r\n", postfix = "\r\n")
        assertEquals(expected, CsvExport.build(snapshot))
    }

    @Test
    fun csvUsesTheGivenZone() {
        val millis = at("2026-11-09", 0, 30)
        assertEquals(
            "date,time,type,interval_minutes\r\n2026-11-08,23:30,MISSED,45\r\n",
            CsvExport.build(listOf(missed(millis)), ZoneOffset.UTC),
        )
    }

    @Test
    fun csvWithoutEventsIsOnlyTheHeader() {
        assertEquals("date,time,type,interval_minutes\r\n", CsvExport.build(emptyList(), TestData.zone))
    }

    @Test
    fun csvEscapesFieldsWithSeparatorsQuotesAndLineBreaks() {
        assertEquals("MISSED", CsvExport.escape("MISSED"))
        assertEquals("\"A,B\"", CsvExport.escape("A,B"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.escape("say \"hi\""))
        assertEquals("\"x\ny\"", CsvExport.escape("x\ny"))
        val csv = CsvExport.build(listOf(event("NEW,TYPE", at("2026-11-09", 8))), TestData.zone)
        assertEquals("date,time,type,interval_minutes\r\n2026-11-09,08:00,\"NEW,TYPE\",45\r\n", csv)
    }

    @Test
    fun fileNamesCarryTheDate() {
        assertEquals("deskbreak-2026-11-09.csv", CsvExport.fileName(LocalDate.parse("2026-11-09")))
        assertEquals("deskbreak-backup-2026-11-09.json", BackupFiles.fileName(LocalDate.parse("2026-11-09")))
    }

    @Test
    fun backupFromSnapshotRoundTripsAndIsSummarized() {
        val snapshot = TestData.snapshot(
            generatedAtMillis = 5L,
            events = listOf(done(at("2026-11-02", 9)), missed(at("2026-11-09", 23, 30))),
        )
        val backup = BackupFiles.fromSnapshot(snapshot, nowMillis = 1234L)
        assertEquals(backup, SyncCodec.decodeBackup(SyncCodec.encodeBackup(backup)))
        val summary = BackupFiles.summarize(backup)
        assertEquals(2, summary.eventCount)
        assertEquals(LocalDate.parse("2026-11-02"), summary.firstDate)
        assertEquals(LocalDate.parse("2026-11-09"), summary.lastDate)
        assertEquals(1234L, summary.createdAtMillis)
    }

    @Test
    fun emptyBackupHasNoDateRange() {
        val summary = BackupFiles.summarize(BackupFiles.fromSnapshot(TestData.snapshot(0L), 0L))
        assertEquals(0, summary.eventCount)
        assertNull(summary.firstDate)
        assertNull(summary.lastDate)
    }
}
