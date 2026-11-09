package dev.stepcron.wear.sync

import dev.stepcron.shared.sync.SettingsUpdate
import dev.stepcron.shared.sync.SyncDay
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.VibrationLevel
import dev.stepcron.wear.domain.ActiveWindow
import dev.stepcron.wear.domain.AlarmKind.CYCLE_START
import dev.stepcron.wear.domain.AlarmKind.REMINDER
import dev.stepcron.wear.domain.PlannedAlarm
import dev.stepcron.wear.domain.WeeklySchedule
import dev.stepcron.wear.service.EngineHarness
import dev.stepcron.wear.service.EngineHarness.Companion.TUESDAY
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Änderungen vom Handy laufen durch eine echte ReminderEngine (Attrappen für Alarme usw., echter DataStore).
 * Ausgangslage: ausgeschaltet, Standardplan Mo–Fr 8–17 Uhr, Intervall 45 Minuten; Montag 09:00.
 */
class SettingsUpdateApplierTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var harness: EngineHarness
    private lateinit var applier: SettingsUpdateApplier

    @Before
    fun setUp() {
        harness = EngineHarness(folder)
        applier = SettingsUpdateApplier(harness.settingsRepository, harness.engine, clock = { harness.now })
    }

    @After
    fun tearDown() {
        harness.close()
    }

    private suspend fun settings(): ReminderSettings = harness.settingsRepository.currentSettings()

    private fun at(time: String, date: String = EngineHarness.MONDAY) = harness.at(time, date)

    @Test
    fun `a full update configures and enables the watch through the engine`() = runBlocking {
        val everyDay = List(7) { SyncDay(enabled = true, startMinute = 7 * 60, endMinute = 18 * 60) }
        val changed = applier.apply(
            SettingsUpdate(
                enabled = true,
                intervalMinutes = 30,
                schedule = everyDay,
                autoStepEnabled = false,
                snoozeMinutes = 15,
                stepThreshold = 50,
                vibration = "LIGHT",
                respectQuietModes = false,
                onlyWhenWorn = false,
                dailyGoal = 10,
                pausedUntilMillis = 0L,
            ),
        )

        assertTrue(changed)
        val settings = settings()
        assertTrue(settings.enabled)
        assertEquals(30, settings.intervalMinutes)
        assertEquals(WeeklySchedule.everyDay(ActiveWindow.ofHours(7, 18)), settings.schedule)
        assertFalse(settings.autoStepEnabled)
        assertEquals(15, settings.snoozeMinutes)
        assertEquals(50, settings.stepThreshold)
        assertEquals(VibrationLevel.LIGHT, settings.vibration)
        assertFalse(settings.respectQuietModes)
        assertFalse(settings.onlyWhenWorn)
        assertEquals(10, settings.dailyGoal)
        assertEquals(0L, settings.pausedUntil)
        // Eingeschaltet wurde zuletzt: Der erste Zyklus läuft gleich mit dem neuen Intervall.
        assertEquals(PlannedAlarm(at("09:30"), REMINDER), harness.alarms.planned)
        assertEquals(at("09:00"), harness.settingsRepository.currentState().cycleStartAt)
        assertFalse(harness.stepTracking.running)
    }

    @Test
    fun `values equal to the stored ones are not set again`() = runBlocking {
        harness.engine.setEnabled(true)
        val changesBefore = harness.stateChanges
        harness.now = at("09:20")

        val changed = applier.apply(
            SettingsUpdate(enabled = true, intervalMinutes = 45, vibration = "STRONG", pausedUntilMillis = 0L),
        )

        assertFalse(changed)
        assertEquals(changesBefore, harness.stateChanges)
        // Kein erneutes Einschalten: Der laufende Zyklus bleibt.
        assertEquals(PlannedAlarm(at("09:45"), REMINDER), harness.alarms.planned)
        assertEquals(at("09:00"), harness.settingsRepository.currentState().cycleStartAt)
    }

    @Test
    fun `bad values are clamped or ignored`() = runBlocking {
        val fiveDays = List(5) { SyncDay(enabled = true, startMinute = 0, endMinute = 60) }
        applier.apply(
            SettingsUpdate(intervalMinutes = 3, snoozeMinutes = 999, vibration = "ULTRA", schedule = fiveDays),
        )

        val settings = settings()
        assertEquals(5, settings.intervalMinutes)
        assertEquals(30, settings.snoozeMinutes)
        assertEquals(VibrationLevel.STRONG, settings.vibration)
        assertEquals(WeeklySchedule.DEFAULT, settings.schedule)
    }

    @Test
    fun `a schedule change from the phone is handled like one on the watch`() = runBlocking {
        harness.engine.setEnabled(true)
        val mondayOff = SyncMapping.toSyncSettings(settings()).schedule
            .mapIndexed { index, day -> if (index == 0) day.copy(enabled = false) else day }

        applier.apply(SettingsUpdate(schedule = mondayOff))

        assertFalse(settings().schedule.days[0].enabled)
        assertEquals(PlannedAlarm(at("08:00", TUESDAY), CYCLE_START), harness.alarms.planned)
        assertFalse(harness.stepTracking.running)
    }

    @Test
    fun `a future pause pauses the reminders`() = runBlocking {
        harness.engine.setEnabled(true)

        assertTrue(applier.apply(SettingsUpdate(pausedUntilMillis = at("11:00"))))

        assertEquals(at("11:00"), settings().pausedUntil)
        assertEquals(PlannedAlarm(at("11:00"), CYCLE_START), harness.alarms.planned)
        assertFalse(harness.stepTracking.running)
    }

    @Test
    fun `zero or a past time ends a running pause`() = runBlocking {
        harness.engine.setEnabled(true)
        harness.engine.pauseUntil(at("11:00"))
        harness.now = at("09:30")

        assertTrue(applier.apply(SettingsUpdate(pausedUntilMillis = 0L)))

        assertEquals(0L, settings().pausedUntil)
        assertEquals(PlannedAlarm(at("10:15"), REMINDER), harness.alarms.planned)

        harness.engine.pauseUntil(at("11:00"))
        harness.now = at("10:00")
        assertTrue(applier.apply(SettingsUpdate(pausedUntilMillis = at("09:59"))))
        assertEquals(0L, settings().pausedUntil)
        assertEquals(PlannedAlarm(at("10:45"), REMINDER), harness.alarms.planned)
    }

    @Test
    fun `ending a pause that is not running changes nothing`() = runBlocking {
        harness.engine.setEnabled(true)
        val changesBefore = harness.stateChanges
        harness.now = at("09:20")

        assertFalse(applier.apply(SettingsUpdate(pausedUntilMillis = 0L)))

        assertEquals(changesBefore, harness.stateChanges)
        assertEquals(PlannedAlarm(at("09:45"), REMINDER), harness.alarms.planned)
    }

    @Test
    fun `a pause is limited to the longest vacation`() = runBlocking {
        harness.engine.setEnabled(true)

        applier.apply(SettingsUpdate(pausedUntilMillis = Long.MAX_VALUE))

        assertEquals(harness.now + SyncMapping.MAX_PAUSE_MILLIS, settings().pausedUntil)
    }

    @Test
    fun `enabling and pausing in one update keeps the pause`() = runBlocking {
        applier.apply(SettingsUpdate(enabled = true, pausedUntilMillis = at("11:00")))

        val settings = settings()
        assertTrue(settings.enabled)
        assertEquals(at("11:00"), settings.pausedUntil)
        assertEquals(PlannedAlarm(at("11:00"), CYCLE_START), harness.alarms.planned)
    }

    @Test
    fun `switching off drops a pause from the same update`() = runBlocking {
        harness.engine.setEnabled(true)
        harness.engine.pauseUntil(at("11:00"))

        applier.apply(SettingsUpdate(enabled = false, pausedUntilMillis = at("12:00")))

        val settings = settings()
        assertFalse(settings.enabled)
        assertEquals(0L, settings.pausedUntil)
        assertNull(harness.alarms.planned)
    }

    @Test
    fun `a pause for a switched off watch is ignored`() = runBlocking {
        assertFalse(applier.apply(SettingsUpdate(pausedUntilMillis = at("11:00"))))

        assertEquals(0L, settings().pausedUntil)
        assertEquals(0, harness.stateChanges)
    }
}
