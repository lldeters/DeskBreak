package dev.stepcron.wear.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.stepcron.wear.data.MovementLog
import dev.stepcron.wear.data.db.MovementEventType
import dev.stepcron.wear.data.db.MovementEventType.AUTO_STEP_RESET
import dev.stepcron.wear.data.db.MovementEventType.COMPLETED_REMINDER
import dev.stepcron.wear.data.db.MovementEventType.MANUAL_RESET
import dev.stepcron.wear.data.db.MovementEventType.MISSED
import dev.stepcron.wear.data.db.MovementEventType.SNOOZED
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.ReminderState
import dev.stepcron.wear.data.settings.SettingsRepository
import dev.stepcron.wear.domain.ActiveWindow
import dev.stepcron.wear.domain.AlarmKind.CYCLE_START
import dev.stepcron.wear.domain.AlarmKind.REMINDER
import dev.stepcron.wear.domain.AlarmKind.WINDOW_END
import dev.stepcron.wear.domain.PlannedAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Abläufe der Zustandsmaschine mit echtem DataStore und Attrappen für Alarme, Notification, Schritterkennung und
 * Protokoll. Standardplan: Mo–Fr 8–17 Uhr, Intervall 45 Minuten. 2026-11-09 ist ein Montag.
 */
class ReminderEngineTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val zone = ZoneId.of("Europe/Berlin")
    private lateinit var scope: CoroutineScope
    private lateinit var repository: SettingsRepository
    private lateinit var engine: ReminderEngine

    private val alarms = FakeAlarms()
    private val alerts = FakeAlerts()
    private val stepTracking = FakeStepTracking()
    private val log = FakeMovementLog()
    private var now = at("09:00")
    private var stateChanges = 0

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + Job())
        val file = folder.newFile("settings.preferences_pb").apply { delete() }
        repository = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        engine = ReminderEngine(
            settingsRepository = repository,
            movementLog = log,
            alarms = alarms,
            alerts = alerts,
            stepTracking = stepTracking,
            clock = { now },
            zoneProvider = { zone },
            dispatcher = Dispatchers.Unconfined,
            onStateChanged = { stateChanges++ },
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // ---------------------------------------------------------------------------------------------
    // Einschalten und Erinnerung
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `enabling inside the window starts a cycle and plans the first reminder`() = runBlocking {
        engine.setEnabled(true)

        assertEquals(PlannedAlarm(at("09:45"), REMINDER), alarms.planned)
        val state = state()
        assertEquals(at("09:00"), state.cycleStartAt)
        assertEquals(at("09:45"), state.nextAlarmAt)
        assertEquals(REMINDER, state.nextAlarmKind)
        assertTrue(stepTracking.running)
        assertEquals(1, stateChanges)
    }

    @Test
    fun `enabling on a free day plans the next work day`() = runBlocking {
        now = at("10:00", SATURDAY)
        engine.setEnabled(true)

        assertEquals(PlannedAlarm(at("08:00", NEXT_MONDAY), CYCLE_START), alarms.planned)
        assertFalse(stepTracking.running)
    }

    @Test
    fun `a due reminder alerts, stays pending and plans the next one`() = runBlocking {
        engine.setEnabled(true)
        fireNextAlarm()

        assertEquals(at("09:45"), now)
        assertEquals(at("09:45"), alerts.shownFor)
        assertEquals(1, alerts.vibrations)
        assertEquals(at("09:45"), state().pendingReminderAt)
        assertEquals(PlannedAlarm(at("10:30"), REMINDER), alarms.planned)
        assertTrue(log.events.isEmpty())
    }

    @Test
    fun `an unanswered reminder counts as missed when the next one fires`() = runBlocking {
        engine.setEnabled(true)
        fireNextAlarm()
        fireNextAlarm()

        assertEquals(listOf(MISSED to at("09:45")), log.events)
        assertEquals(at("10:30"), state().pendingReminderAt)
        assertEquals(at("10:30"), alerts.shownFor)
    }

    @Test
    fun `done logs a completed reminder once and keeps the rhythm`() = runBlocking {
        engine.setEnabled(true)
        fireNextAlarm()
        now = at("09:50")
        engine.onReminderDone()
        engine.onReminderDone()

        assertEquals(listOf(COMPLETED_REMINDER to at("09:50")), log.events)
        assertEquals(0L, state().pendingReminderAt)
        assertNull(alerts.shownFor)
        assertEquals(PlannedAlarm(at("10:30"), REMINDER), alarms.planned)
    }

    @Test
    fun `snooze moves the next reminder by ten minutes once`() = runBlocking {
        engine.setEnabled(true)
        fireNextAlarm()
        now = at("09:46")
        engine.onReminderSnoozed()

        assertEquals(listOf(SNOOZED to at("09:46")), log.events)
        assertEquals(PlannedAlarm(at("09:56"), REMINDER), alarms.planned)
        assertEquals(at("09:56"), state().snoozeUntil)
        assertEquals(0L, state().pendingReminderAt)

        // Die Snooze-Erinnerung startet den regulären Takt neu.
        fireNextAlarm()
        assertEquals(PlannedAlarm(at("10:41"), REMINDER), alarms.planned)
        assertEquals(0L, state().snoozeUntil)
    }

    @Test
    fun `snooze shortly before the end of the window plans the window end`() = runBlocking {
        now = at("16:00")
        engine.setEnabled(true)
        fireNextAlarm() // 16:45, danach nur noch das Fensterende
        assertEquals(PlannedAlarm(at("17:00"), WINDOW_END), alarms.planned)
        now = at("16:55")
        engine.onReminderSnoozed()

        assertEquals(PlannedAlarm(at("17:00"), WINDOW_END), alarms.planned)
        assertEquals(0L, state().snoozeUntil)
    }

    // ---------------------------------------------------------------------------------------------
    // Bewegung
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `manual reset restarts the interval and debounces double taps`() = runBlocking {
        engine.setEnabled(true)
        now = at("09:20")
        engine.onManualReset()
        assertEquals(listOf(MANUAL_RESET to at("09:20")), log.events)
        assertEquals(PlannedAlarm(at("10:05"), REMINDER), alarms.planned)

        now = at("09:20:30")
        engine.onManualReset()
        assertEquals(1, log.events.size)
        assertEquals(PlannedAlarm(at("10:05:30"), REMINDER), alarms.planned)
    }

    @Test
    fun `manual reset answers a pending reminder`() = runBlocking {
        engine.setEnabled(true)
        fireNextAlarm()
        now = at("09:47")
        engine.onManualReset()

        assertEquals(listOf(MANUAL_RESET to at("09:47")), log.events)
        assertNull(alerts.shownFor)
        assertEquals(0L, state().pendingReminderAt)
        assertEquals(PlannedAlarm(at("10:32"), REMINDER), alarms.planned)
    }

    @Test
    fun `walking answers a pending reminder and restarts the interval`() = runBlocking {
        engine.setEnabled(true)
        fireNextAlarm()
        now = at("09:48")
        engine.onWalkDetected()

        assertEquals(listOf(AUTO_STEP_RESET to at("09:48")), log.events)
        assertNull(alerts.shownFor)
        assertEquals(0L, state().pendingReminderAt)
        assertEquals(PlannedAlarm(at("10:33"), REMINDER), alarms.planned)
    }

    @Test
    fun `continued walking counts once and only extends the timer`() = runBlocking {
        engine.setEnabled(true)
        now = at("09:30")
        engine.onWalkDetected()
        assertEquals(PlannedAlarm(at("10:15"), REMINDER), alarms.planned)

        now = at("09:33")
        engine.onWalkDetected()
        assertEquals(PlannedAlarm(at("10:18"), REMINDER), alarms.planned)

        // Unter einer Minute Verschiebung bleibt der Alarm, wie er ist.
        now = at("09:33:30")
        engine.onWalkDetected()
        assertEquals(PlannedAlarm(at("10:18"), REMINDER), alarms.planned)
        assertEquals(listOf(AUTO_STEP_RESET to at("09:30")), log.events)

        // Nach mehr als fünf Minuten ohne Gehen ist es eine neue Bewegungspause.
        now = at("09:40")
        engine.onWalkDetected()
        assertEquals(listOf(AUTO_STEP_RESET to at("09:30"), AUTO_STEP_RESET to at("09:40")), log.events)
        assertEquals(PlannedAlarm(at("10:25"), REMINDER), alarms.planned)
    }

    @Test
    fun `walking is ignored when automatic step detection is off`() = runBlocking {
        engine.setEnabled(true)
        engine.setAutoStepEnabled(false)
        assertFalse(stepTracking.running)
        now = at("09:30")
        engine.onWalkDetected()

        assertTrue(log.events.isEmpty())
        assertEquals(PlannedAlarm(at("09:45"), REMINDER), alarms.planned)
    }

    @Test
    fun `without permission the step tracker stays stopped`() = runBlocking {
        stepTracking.permitted = false
        engine.setEnabled(true)

        assertFalse(stepTracking.running)
        assertFalse(state().stepTrackingActive)
    }

    @Test
    fun `a finished exercise answers a pending reminder, otherwise it counts as a manual reset`() = runBlocking {
        engine.setEnabled(true)
        fireNextAlarm()
        now = at("09:50")
        engine.onExerciseCompleted()
        assertEquals(listOf(COMPLETED_REMINDER to at("09:50")), log.events)
        assertEquals(PlannedAlarm(at("10:30"), REMINDER), alarms.planned)

        now = at("10:00")
        engine.onExerciseCompleted()
        assertEquals(MANUAL_RESET to at("10:00"), log.events.last())
        assertEquals(PlannedAlarm(at("10:45"), REMINDER), alarms.planned)
    }

    // ---------------------------------------------------------------------------------------------
    // Feierabend und freie Tage
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `window end stops step detection and plans the next morning`() = runBlocking {
        now = at("16:00")
        engine.setEnabled(true)
        fireNextAlarm() // 16:45
        now = at("16:50")
        engine.onReminderDone()
        fireNextAlarm() // 17:00, Fensterende

        assertEquals(at("17:00"), now)
        assertEquals(PlannedAlarm(at("08:00", TUESDAY), CYCLE_START), alarms.planned)
        assertFalse(stepTracking.running)
    }

    @Test
    fun `a reminder shortly before the window end stays answerable until the next morning`() = runBlocking {
        now = at("16:05")
        engine.setEnabled(true)
        fireNextAlarm() // 16:50
        fireNextAlarm() // 17:00: erst zehn Minuten offen, also noch nicht verpasst

        assertEquals(at("16:50"), state().pendingReminderAt)
        assertEquals(PlannedAlarm(at("08:00", TUESDAY), CYCLE_START), alarms.planned)

        now = at("07:30", TUESDAY)
        engine.onReminderDone()
        assertEquals(listOf(COMPLETED_REMINDER to at("07:30", TUESDAY)), log.events)
    }

    @Test
    fun `an unanswered reminder from yesterday counts as missed at the next window start`() = runBlocking {
        now = at("16:05")
        engine.setEnabled(true)
        fireNextAlarm() // 16:50
        fireNextAlarm() // 17:00
        fireNextAlarm() // Di 08:00

        assertEquals(at("08:00", TUESDAY), now)
        assertEquals(listOf(MISSED to at("16:50")), log.events)
        assertNull(alerts.shownFor)
        assertEquals(0L, state().pendingReminderAt)
        assertEquals(PlannedAlarm(at("08:45", TUESDAY), REMINDER), alarms.planned)
        assertTrue(stepTracking.running)
    }

    @Test
    fun `a reminder pending before free days is closed after the grace period`() = runBlocking {
        now = at("16:05", FRIDAY)
        engine.setEnabled(true)
        fireNextAlarm() // Fr 16:50
        fireNextAlarm() // Fr 17:00: Montag ist zu weit weg, also Abschluss nach der Kulanzzeit
        assertEquals(PlannedAlarm(at("17:05", FRIDAY), WINDOW_END), alarms.planned)

        fireNextAlarm() // Fr 17:05
        assertEquals(listOf(MISSED to at("16:50", FRIDAY)), log.events)
        assertNull(alerts.shownFor)
        assertEquals(PlannedAlarm(at("08:00", NEXT_MONDAY), CYCLE_START), alarms.planned)
    }

    // ---------------------------------------------------------------------------------------------
    // Einstellungen
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `making today a free day closes the pending reminder without counting it as missed`() = runBlocking {
        engine.setEnabled(true)
        fireNextAlarm()
        now = at("09:50")
        engine.updateSchedule { schedule -> schedule.with(DayOfWeek.MONDAY) { it.copy(enabled = false) } }

        assertTrue(log.events.isEmpty())
        assertNull(alerts.shownFor)
        assertEquals(0L, state().pendingReminderAt)
        assertEquals(PlannedAlarm(at("08:00", TUESDAY), CYCLE_START), alarms.planned)
        assertFalse(stepTracking.running)
    }

    @Test
    fun `ending today's window early is handled like the end of the day`() = runBlocking {
        engine.setEnabled(true)
        now = at("11:00")
        engine.updateSchedule { schedule ->
            schedule.with(DayOfWeek.MONDAY) { it.copy(window = ActiveWindow.ofHours(8, 10)) }
        }

        assertEquals(PlannedAlarm(at("08:00", TUESDAY), CYCLE_START), alarms.planned)
        assertFalse(stepTracking.running)
    }

    @Test
    fun `extending today's window into the present starts a fresh cycle`() = runBlocking {
        now = at("17:30")
        engine.setEnabled(true)
        assertEquals(PlannedAlarm(at("08:00", TUESDAY), CYCLE_START), alarms.planned)

        engine.updateSchedule { schedule ->
            schedule.with(DayOfWeek.MONDAY) { it.copy(window = ActiveWindow.ofHours(8, 19)) }
        }
        assertEquals(at("17:30"), state().cycleStartAt)
        assertEquals(PlannedAlarm(at("18:15"), REMINDER), alarms.planned)
        assertTrue(stepTracking.running)
    }

    @Test
    fun `moving today's start past the running cycle keeps the cycle and an active snooze`() = runBlocking {
        now = at("09:13")
        engine.setEnabled(true)
        fireNextAlarm() // 09:58
        now = at("09:59")
        engine.onReminderSnoozed()
        assertEquals(PlannedAlarm(at("10:09"), REMINDER), alarms.planned)

        // Der neue Beginn 10:00 liegt nach dem Zyklusstart 09:58, aber das Fenster lief vorher schon.
        now = at("10:05")
        engine.updateSchedule { schedule ->
            schedule.with(DayOfWeek.MONDAY) { it.copy(window = ActiveWindow(10 * 60, 17 * 60)) }
        }
        assertEquals(PlannedAlarm(at("10:09"), REMINDER), alarms.planned)
        assertEquals(at("09:58"), state().cycleStartAt)
        assertEquals(at("10:09"), state().snoozeUntil)
    }

    @Test
    fun `switching today off after the window closes today's open reminder without counting it`() = runBlocking {
        now = at("16:05")
        engine.setEnabled(true)
        fireNextAlarm() // 16:50
        fireNextAlarm() // 17:00, die Erinnerung bleibt bis morgen offen
        now = at("17:01")
        engine.updateSchedule { schedule -> schedule.with(DayOfWeek.MONDAY) { it.copy(enabled = false) } }

        assertNull(alerts.shownFor)
        assertEquals(0L, state().pendingReminderAt)
        assertEquals(PlannedAlarm(at("08:00", TUESDAY), CYCLE_START), alarms.planned)
        fireNextAlarm()
        assertTrue(log.events.isEmpty())
    }

    @Test
    fun `switching today off before the window lets yesterday's open reminder expire`() = runBlocking {
        now = at("16:05")
        engine.setEnabled(true)
        fireNextAlarm() // Mo 16:50
        fireNextAlarm() // Mo 17:00
        now = at("07:00", TUESDAY)
        engine.updateSchedule { schedule -> schedule.with(DayOfWeek.TUESDAY) { it.copy(enabled = false) } }

        // Bis Mittwoch früh bliebe sie zu lange offen: sofort abschließen und als verpasst werten.
        assertEquals(PlannedAlarm(at("07:00", TUESDAY), WINDOW_END), alarms.planned)
        fireNextAlarm()
        assertEquals(listOf(MISSED to at("16:50")), log.events)
        assertEquals(PlannedAlarm(at("08:00", "2026-11-11"), CYCLE_START), alarms.planned)
    }

    @Test
    fun `starting today's window earlier counts yesterday's open reminder as missed`() = runBlocking {
        now = at("16:05")
        engine.setEnabled(true)
        fireNextAlarm() // Mo 16:50
        fireNextAlarm() // Mo 17:00
        now = at("07:30", TUESDAY)
        engine.updateSchedule { schedule ->
            schedule.with(DayOfWeek.TUESDAY) { it.copy(window = ActiveWindow.ofHours(7, 17)) }
        }

        assertEquals(listOf(MISSED to at("16:50")), log.events)
        assertNull(alerts.shownFor)
        assertEquals(0L, state().pendingReminderAt)
        assertEquals(PlannedAlarm(at("08:15", TUESDAY), REMINDER), alarms.planned)
    }

    @Test
    fun `changing the interval replans the running cycle`() = runBlocking {
        engine.setEnabled(true)
        now = at("09:10")
        engine.setIntervalMinutes(30)

        assertEquals(PlannedAlarm(at("09:30"), REMINDER), alarms.planned)
    }

    @Test
    fun `disabling cancels alarm, notification and step detection`() = runBlocking {
        engine.setEnabled(true)
        fireNextAlarm()
        engine.setEnabled(false)

        assertNull(alarms.planned)
        assertNull(alerts.shownFor)
        assertFalse(stepTracking.running)
        val state = state()
        assertEquals(0L, state.nextAlarmAt)
        assertNull(state.nextAlarmKind)
        assertEquals(0L, state.pendingReminderAt)
    }

    @Test
    fun `a stray alarm while disabled does nothing`() = runBlocking {
        engine.onAlarmFired(REMINDER)

        assertNull(alerts.shownFor)
        assertNull(alarms.planned)
        assertTrue(log.events.isEmpty())
    }

    // ---------------------------------------------------------------------------------------------
    // Pause und Urlaub
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a pause closes the open reminder without counting it and plans its end`() = runBlocking {
        engine.setEnabled(true)
        fireNextAlarm() // 09:45
        now = at("09:50")
        engine.pauseUntil(at("10:50"))

        assertNull(alerts.shownFor)
        assertEquals(0L, state().pendingReminderAt)
        assertEquals(PlannedAlarm(at("10:50"), CYCLE_START), alarms.planned)
        assertFalse(stepTracking.running)
        assertTrue(log.events.isEmpty())
    }

    @Test
    fun `the pause ends with a fresh cycle`() = runBlocking {
        engine.setEnabled(true)
        engine.pauseUntil(at("10:00"))
        fireNextAlarm() // 10:00

        assertEquals(PlannedAlarm(at("10:45"), REMINDER), alarms.planned)
        assertEquals(at("10:00"), state().cycleStartAt)
        assertTrue(stepTracking.running)
        assertEquals(0L, repository.currentSettings().pausedUntil)
    }

    @Test
    fun `a pause past the window resumes at the next window start`() = runBlocking {
        now = at("16:00")
        engine.setEnabled(true)
        engine.pauseUntil(at("18:00"))
        assertEquals(PlannedAlarm(at("08:00", TUESDAY), CYCLE_START), alarms.planned)

        // Ein Urlaub bis einschließlich Montag geht am Dienstag weiter, über das Wochenende hinweg am nächsten Werktag.
        now = at("10:00", FRIDAY)
        engine.pauseUntil(at("00:00", "2026-11-17"))
        assertEquals(PlannedAlarm(at("08:00", "2026-11-17"), CYCLE_START), alarms.planned)
        fireNextAlarm()
        assertEquals(PlannedAlarm(at("08:45", "2026-11-17"), REMINDER), alarms.planned)
    }

    @Test
    fun `resuming early starts a fresh cycle now`() = runBlocking {
        engine.setEnabled(true)
        engine.pauseUntil(at("11:00"))
        now = at("09:30")
        engine.resume()

        assertEquals(PlannedAlarm(at("10:15"), REMINDER), alarms.planned)
        assertEquals(0L, repository.currentSettings().pausedUntil)
        assertTrue(stepTracking.running)
    }

    @Test
    fun `movement during a pause is logged but plans no reminder`() = runBlocking {
        engine.setEnabled(true)
        engine.pauseUntil(at("11:00"))
        now = at("09:30")
        engine.onWalkDetected()
        engine.onManualReset()

        assertEquals(listOf(MANUAL_RESET to at("09:30")), log.events)
        assertEquals(PlannedAlarm(at("11:00"), CYCLE_START), alarms.planned)
    }

    @Test
    fun `schedule edits and stray alarms during a pause keep the pause`() = runBlocking {
        engine.setEnabled(true)
        engine.pauseUntil(at("11:00"))
        now = at("09:30")
        engine.updateSchedule { schedule -> schedule.with(DayOfWeek.MONDAY) { it.copy(window = ActiveWindow.ofHours(9, 12)) } }
        assertEquals(PlannedAlarm(at("11:00"), CYCLE_START), alarms.planned)

        engine.onAlarmFired(REMINDER)
        assertNull(alerts.shownFor)
        assertEquals(PlannedAlarm(at("11:00"), CYCLE_START), alarms.planned)

        engine.updateSchedule { schedule -> schedule.with(DayOfWeek.MONDAY) { it.copy(enabled = false) } }
        assertEquals(PlannedAlarm(at("08:00", TUESDAY), CYCLE_START), alarms.planned)
    }

    @Test
    fun `switching reminders off and on ends a pause`() = runBlocking {
        engine.setEnabled(true)
        engine.pauseUntil(at("12:00"))
        engine.setEnabled(false)
        engine.setEnabled(true)

        assertEquals(0L, repository.currentSettings().pausedUntil)
        assertEquals(PlannedAlarm(at("09:45"), REMINDER), alarms.planned)
    }

    @Test
    fun `restoring after a reboot keeps the pause`() = runBlocking {
        engine.setEnabled(true)
        engine.pauseUntil(at("11:00"))
        alarms.planned = null
        now = at("09:30")
        engine.restoreAfterBoot()
        assertEquals(PlannedAlarm(at("11:00"), CYCLE_START), alarms.planned)

        // Ist die Pause während der Ausschaltzeit abgelaufen, geht es mit einem frischen Zyklus weiter.
        alarms.planned = null
        now = at("11:30")
        engine.restoreAfterBoot()
        assertEquals(PlannedAlarm(at("12:15"), REMINDER), alarms.planned)
    }

    // ---------------------------------------------------------------------------------------------
    // Neustart
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `restoring after a reboot keeps a future alarm`() = runBlocking {
        engine.setEnabled(true)
        alarms.planned = null // AlarmManager vergisst Alarme beim Neustart
        now = at("09:20")
        engine.restoreAfterBoot()

        assertEquals(PlannedAlarm(at("09:45"), REMINDER), alarms.planned)
        assertEquals(at("09:00"), state().cycleStartAt)
    }

    @Test
    fun `restoring after a reboot past the alarm starts a fresh cycle`() = runBlocking {
        engine.setEnabled(true)
        alarms.planned = null
        now = at("10:00")
        engine.restoreAfterBoot()

        assertEquals(at("10:00"), state().cycleStartAt)
        assertEquals(PlannedAlarm(at("10:45"), REMINDER), alarms.planned)
    }

    // ---------------------------------------------------------------------------------------------
    // Hilfen
    // ---------------------------------------------------------------------------------------------

    private fun at(time: String, date: String = MONDAY): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    private suspend fun state(): ReminderState = repository.currentState()

    /** Stellt die Uhr auf den geplanten Alarm und löst ihn aus. */
    private suspend fun fireNextAlarm() {
        val alarm = checkNotNull(alarms.planned) { "Kein Alarm geplant" }
        now = alarm.triggerAtMillis
        engine.onAlarmFired(alarm.kind)
    }

    private class FakeAlarms : Alarms {
        var planned: PlannedAlarm? = null

        override fun schedule(alarm: PlannedAlarm): Boolean {
            planned = alarm
            return true
        }

        override fun cancel() {
            planned = null
        }
    }

    private class FakeAlerts : ReminderAlerts {
        /** Zeitpunkt der gerade sichtbaren Erinnerung, `null` wenn keine angezeigt wird. */
        var shownFor: Long? = null
        var vibrations = 0

        override fun showReminder(reminderAtMillis: Long) {
            shownFor = reminderAtMillis
        }

        override fun vibrateReminder() {
            vibrations++
        }

        override fun cancelReminder() {
            shownFor = null
        }
    }

    private class FakeStepTracking : StepTracking {
        var permitted = true
        var running = false

        override fun shouldRun(settings: ReminderSettings, nowMillis: Long, zone: ZoneId): Boolean =
            permitted && settings.wantsStepTracking(nowMillis, zone)

        override fun start() {
            running = true
        }

        override fun stop() {
            running = false
        }
    }

    private class FakeMovementLog : MovementLog {
        val events = mutableListOf<Pair<MovementEventType, Long>>()

        override suspend fun log(type: MovementEventType, timestampMillis: Long, intervalMinutes: Int): Long {
            events += type to timestampMillis
            return events.size.toLong()
        }
    }

    private companion object {
        const val MONDAY = "2026-11-09"
        const val TUESDAY = "2026-11-10"
        const val FRIDAY = "2026-11-13"
        const val SATURDAY = "2026-11-14"
        const val NEXT_MONDAY = "2026-11-16"
    }
}
