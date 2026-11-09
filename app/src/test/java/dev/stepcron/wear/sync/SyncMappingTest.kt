package dev.stepcron.wear.sync

import dev.stepcron.shared.sync.SettingsUpdate
import dev.stepcron.shared.sync.SyncDay
import dev.stepcron.shared.sync.SyncEvent
import dev.stepcron.shared.sync.SyncStatus
import dev.stepcron.wear.data.db.MovementEvent
import dev.stepcron.wear.data.db.MovementEventType
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.ReminderState
import dev.stepcron.wear.data.settings.VibrationLevel
import dev.stepcron.wear.domain.ActiveWindow
import dev.stepcron.wear.domain.AlarmKind
import dev.stepcron.wear.domain.WeeklySchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class SyncMappingTest {

    private val now = 1_800_000_000_000L

    private val customSettings = ReminderSettings(
        enabled = true,
        intervalMinutes = 30,
        schedule = WeeklySchedule.DEFAULT
            .with(DayOfWeek.TUESDAY) { it.copy(enabled = false) }
            .with(DayOfWeek.WEDNESDAY) { it.copy(window = ActiveWindow(7 * 60 + 30, 15 * 60 + 45)) },
        autoStepEnabled = false,
        pausedUntil = now + 3_600_000L,
        snoozeMinutes = 15,
        stepThreshold = 50,
        vibration = VibrationLevel.LIGHT,
        respectQuietModes = false,
        onlyWhenWorn = false,
        dailyGoal = 10,
    )

    // ---------------------------------------------------------------------------------------------
    // Einstellungen
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `settings survive the round trip through the protocol`() {
        val update = SyncMapping.toSettingsUpdate(SyncMapping.toSyncSettings(customSettings))
        val change = SyncMapping.toSettingsChange(update)

        assertEquals(
            SettingsChange(
                enabled = true,
                intervalMinutes = 30,
                schedule = customSettings.schedule,
                autoStepEnabled = false,
                snoozeMinutes = 15,
                stepThreshold = 50,
                vibration = VibrationLevel.LIGHT,
                respectQuietModes = false,
                onlyWhenWorn = false,
                dailyGoal = 10,
                pausedUntilMillis = now + 3_600_000L,
            ),
            change,
        )
    }

    @Test
    fun `sync settings list the days monday first and name the vibration level`() {
        val sync = SyncMapping.toSyncSettings(customSettings)

        assertEquals(7, sync.schedule.size)
        assertEquals(SyncDay(enabled = true, startMinute = 480, endMinute = 1020), sync.schedule[0])
        assertEquals(SyncDay(enabled = false, startMinute = 480, endMinute = 1020), sync.schedule[1])
        assertEquals(SyncDay(enabled = true, startMinute = 450, endMinute = 945), sync.schedule[2])
        assertEquals(SyncDay(enabled = false, startMinute = 480, endMinute = 1020), sync.schedule[6])
        assertEquals("LIGHT", sync.vibration)
        assertEquals(now + 3_600_000L, sync.pausedUntilMillis)
        VibrationLevel.entries.forEach { level ->
            val name = SyncMapping.toSyncSettings(customSettings.copy(vibration = level)).vibration
            assertEquals(level, VibrationLevel.fromName(name))
        }
    }

    @Test
    fun `an empty update changes nothing`() {
        assertEquals(SettingsChange(), SyncMapping.toSettingsChange(SettingsUpdate()))
    }

    @Test
    fun `numbers from the phone are clamped like stored values`() {
        fun change(update: SettingsUpdate) = SyncMapping.toSettingsChange(update)

        assertEquals(5, change(SettingsUpdate(intervalMinutes = 3)).intervalMinutes)
        assertEquals(5, change(SettingsUpdate(intervalMinutes = -40)).intervalMinutes)
        assertEquals(35, change(SettingsUpdate(intervalMinutes = 33)).intervalMinutes)
        assertEquals(120, change(SettingsUpdate(intervalMinutes = Int.MAX_VALUE)).intervalMinutes)
        assertEquals(10, change(SettingsUpdate(snoozeMinutes = 12)).snoozeMinutes)
        assertEquals(5, change(SettingsUpdate(snoozeMinutes = Int.MIN_VALUE)).snoozeMinutes)
        assertEquals(30, change(SettingsUpdate(snoozeMinutes = Int.MAX_VALUE)).snoozeMinutes)
        assertEquals(20, change(SettingsUpdate(stepThreshold = 1)).stepThreshold)
        assertEquals(100, change(SettingsUpdate(stepThreshold = 5_000)).stepThreshold)
        assertEquals(2, change(SettingsUpdate(dailyGoal = 0)).dailyGoal)
        assertEquals(16, change(SettingsUpdate(dailyGoal = 99)).dailyGoal)
        // Nur gesetzte Felder: das Intervall allein lässt die übrigen Zahlen unverändert.
        val intervalOnly = change(SettingsUpdate(intervalMinutes = 60))
        assertEquals(SettingsChange(intervalMinutes = 60), intervalOnly)
    }

    @Test
    fun `broken schedule windows are repaired instead of crashing`() {
        val days = listOf(
            SyncDay(enabled = true, startMinute = -50, endMinute = 99_999),
            SyncDay(enabled = true, startMinute = 600, endMinute = 300),
            SyncDay(enabled = true, startMinute = 1439, endMinute = 1440),
            SyncDay(enabled = false, startMinute = Int.MIN_VALUE, endMinute = Int.MAX_VALUE),
            SyncDay(enabled = true, startMinute = 480, endMinute = 480),
            SyncDay(enabled = false, startMinute = 0, endMinute = 0),
            SyncDay(enabled = true, startMinute = 540, endMinute = 1020),
        )
        val schedule = SyncMapping.toSettingsChange(SettingsUpdate(schedule = days)).schedule!!

        assertEquals(ActiveWindow(0, 1440), schedule.days[0].window)
        assertEquals(ActiveWindow(600, 615), schedule.days[1].window)
        assertEquals(ActiveWindow(1425, 1440), schedule.days[2].window)
        assertEquals(ActiveWindow(0, 1440), schedule.days[3].window)
        assertEquals(false, schedule.days[3].enabled)
        assertEquals(ActiveWindow(480, 495), schedule.days[4].window)
        assertEquals(ActiveWindow(0, 15), schedule.days[5].window)
        assertEquals(ActiveWindow(540, 1020), schedule.days[6].window)
    }

    @Test
    fun `a schedule without exactly seven days is ignored`() {
        val day = SyncDay(enabled = true, startMinute = 480, endMinute = 1020)

        assertNull(SyncMapping.toSchedule(emptyList()))
        assertNull(SyncMapping.toSchedule(List(6) { day }))
        assertNull(SyncMapping.toSchedule(List(8) { day }))
        assertNull(SyncMapping.toSettingsChange(SettingsUpdate(schedule = List(5) { day })).schedule)
        assertEquals(WeeklySchedule.everyDay(ActiveWindow(480, 1020)), SyncMapping.toSchedule(List(7) { day }))
    }

    @Test
    fun `an unknown vibration level leaves the vibration unchanged`() {
        fun vibration(name: String) = SyncMapping.toSettingsChange(SettingsUpdate(vibration = name)).vibration

        assertEquals(VibrationLevel.MEDIUM, vibration("MEDIUM"))
        assertNull(vibration("ULTRA"))
        assertNull(vibration("light"))
        assertNull(vibration(""))
    }

    @Test
    fun `a pause ends at zero or in the past and is limited to the longest vacation`() {
        assertEquals(PauseChange.Resume, SyncMapping.toPauseChange(0L, now))
        assertEquals(PauseChange.Resume, SyncMapping.toPauseChange(-5L, now))
        assertEquals(PauseChange.Resume, SyncMapping.toPauseChange(now - 1, now))
        assertEquals(PauseChange.Resume, SyncMapping.toPauseChange(now, now))
        assertEquals(PauseChange.PauseUntil(now + 1), SyncMapping.toPauseChange(now + 1, now))
        assertEquals(
            PauseChange.PauseUntil(now + SyncMapping.MAX_PAUSE_MILLIS),
            SyncMapping.toPauseChange(Long.MAX_VALUE, now),
        )
        assertEquals(61L * 24 * 3_600_000L, SyncMapping.MAX_PAUSE_MILLIS)
    }

    // ---------------------------------------------------------------------------------------------
    // Historie, Status, Wiederherstellung
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `events survive the round trip and get a new id on import`() {
        MovementEventType.entries.forEachIndexed { index, type ->
            val timestamp = now - index * 60_000L
            val event = MovementEvent(id = 42L + index, timestamp = timestamp, eventType = type, intervalMinutes = 45)
            val sync = SyncMapping.toSyncEvent(event)

            assertEquals(SyncEvent(timestampMillis = timestamp, type = type.name, intervalMinutes = 45), sync)
            assertEquals(event.copy(id = 0L), SyncMapping.toMovementEvent(sync, now))
        }
    }

    @Test
    fun `unknown event types and impossible timestamps are dropped on import`() {
        val events = listOf(
            SyncEvent(timestampMillis = now - 1_000L, type = SyncEvent.MANUAL_RESET, intervalMinutes = 45),
            SyncEvent(timestampMillis = now - 2_000L, type = "JUMPED", intervalMinutes = 45),
            SyncEvent(timestampMillis = now - 3_000L, type = "missed", intervalMinutes = 45),
            SyncEvent(timestampMillis = -1L, type = SyncEvent.MISSED, intervalMinutes = 45),
            SyncEvent(now + 2 * SyncMapping.FUTURE_TOLERANCE_MILLIS, type = SyncEvent.MISSED, intervalMinutes = 45),
            SyncEvent(timestampMillis = Long.MAX_VALUE, type = SyncEvent.SNOOZED, intervalMinutes = 45),
            SyncEvent(timestampMillis = now + 60_000L, type = SyncEvent.SNOOZED, intervalMinutes = 30),
        )

        assertEquals(
            listOf(
                now - 1_000L to MovementEventType.MANUAL_RESET,
                now + 60_000L to MovementEventType.SNOOZED,
            ),
            SyncMapping.toMovementEvents(events, now).map { it.timestamp to it.eventType },
        )
    }

    @Test
    fun `the protocol knows exactly the event types of the watch`() {
        assertEquals(MovementEventType.entries.map { it.name }.toSet(), SyncEvent.KNOWN_TYPES)
        val movementTypes = MovementEventType.entries.filter { it.isMovement }.map { it.name }.toSet()
        assertEquals(movementTypes, SyncEvent.MOVEMENT_TYPES)
    }

    @Test
    fun `the status shows the planned alarm, an open reminder and the snooze`() {
        val state = ReminderState(
            nextAlarmAt = now + 600_000L,
            nextAlarmKind = AlarmKind.REMINDER,
            cycleStartAt = now - 1_000L,
            snoozeUntil = now + 600_000L,
            pendingReminderAt = now - 300_000L,
        )

        assertEquals(
            SyncStatus(
                nextAlarmAtMillis = now + 600_000L,
                nextAlarmKind = "REMINDER",
                pendingReminderAtMillis = now - 300_000L,
                snoozeUntilMillis = now + 600_000L,
            ),
            SyncMapping.toSyncStatus(state),
        )
        assertEquals(SyncStatus(0L, null, 0L, 0L), SyncMapping.toSyncStatus(ReminderState()))
    }

    @Test
    fun `the restore id is the path after the restore prefix`() {
        assertEquals("1700000000000", SyncMapping.restoreId("/deskbreak/restore/1700000000000"))
        assertEquals("a/b", SyncMapping.restoreId("/deskbreak/restore/a/b"))
        assertNull(SyncMapping.restoreId("/deskbreak/restore/"))
        assertNull(SyncMapping.restoreId("/deskbreak/snapshot"))
        assertNull(SyncMapping.restoreId(null))
    }
}
