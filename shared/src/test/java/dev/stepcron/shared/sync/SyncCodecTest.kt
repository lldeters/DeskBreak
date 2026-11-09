package dev.stepcron.shared.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCodecTest {

    private val settings = SyncSettings(
        enabled = true,
        intervalMinutes = 45,
        schedule = List(7) { day -> SyncDay(enabled = day < 5, startMinute = 8 * 60, endMinute = 17 * 60) },
        autoStepEnabled = true,
        snoozeMinutes = 10,
        stepThreshold = 30,
        vibration = "STRONG",
        respectQuietModes = true,
        onlyWhenWorn = true,
        dailyGoal = 8,
        pausedUntilMillis = 0L,
    )

    private val events = listOf(
        SyncEvent(1_000L, SyncEvent.COMPLETED_REMINDER, 45),
        SyncEvent(2_000L, SyncEvent.MISSED, 45),
        SyncEvent(3_000L, SyncEvent.AUTO_STEP_RESET, 30),
    )

    @Test
    fun `snapshot round trips through gzip`() {
        val snapshot = SyncSnapshot(
            generatedAtMillis = 5_000L,
            zoneId = "Europe/Berlin",
            settings = settings,
            status = SyncStatus(nextAlarmAtMillis = 9_000L, nextAlarmKind = "REMINDER", pendingReminderAtMillis = 0L, snoozeUntilMillis = 0L),
            events = events,
        )
        val bytes = SyncCodec.encodeSnapshot(snapshot)
        // gzip-Kopf
        assertEquals(0x1f.toByte(), bytes[0])
        assertEquals(snapshot, SyncCodec.decodeSnapshot(bytes))
    }

    @Test
    fun `broken or foreign data decodes to null`() {
        assertNull(SyncCodec.decodeSnapshot(byteArrayOf(1, 2, 3)))
        assertNull(SyncCodec.decodeSettingsUpdate("kein json".toByteArray()))
        assertNull(SyncCodec.decodeBackup("{}".toByteArray()))
        assertNull(SyncCodec.decodeRestoreResult(ByteArray(0)))
    }

    @Test
    fun `a newer incompatible version is rejected, unknown fields are ignored`() {
        val backup = SyncBackup(createdAtMillis = 1L, zoneId = "UTC", settings = settings, events = events)
        val text = SyncCodec.encodeBackup(backup).toString(Charsets.UTF_8)
        assertEquals(backup, SyncCodec.decodeBackup(text.replaceFirst("{", "{\"futureField\": 42,").toByteArray()))
        assertNull(SyncCodec.decodeBackup(text.replace("\"version\": 1", "\"version\": 99").toByteArray()))
    }

    @Test
    fun `a backup needs seven days`() {
        val broken = SyncBackup(createdAtMillis = 1L, zoneId = "UTC", settings = settings.copy(schedule = settings.schedule.take(3)), events = events)
        assertNull(SyncCodec.decodeBackup(SyncCodec.encodeBackup(broken)))
    }

    @Test
    fun `settings updates carry only what is set`() {
        val update = SettingsUpdate(dailyGoal = 10)
        val decoded = SyncCodec.decodeSettingsUpdate(SyncCodec.encodeSettingsUpdate(update))!!
        assertEquals(10, decoded.dailyGoal)
        assertNull(decoded.enabled)
        assertNull(decoded.schedule)
        assertFalse(decoded.isEmpty)
        assertTrue(SettingsUpdate().isEmpty)
    }

    @Test
    fun `restore results round trip`() {
        val result = RestoreResult(restoreId = "abc", success = true, restoredEvents = 3)
        assertEquals(result, SyncCodec.decodeRestoreResult(SyncCodec.encodeRestoreResult(result)))
    }

    @Test
    fun `movement types`() {
        assertTrue(events[0].isMovement)
        assertFalse(events[1].isMovement)
        assertFalse(SyncEvent(1L, "SOMETHING_NEW", 45).isMovement)
    }
}
