package dev.stepcron.wear.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.stepcron.wear.domain.ActiveWindow
import dev.stepcron.wear.domain.DaySchedule
import dev.stepcron.wear.domain.WeeklySchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.DayOfWeek

class SettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepository

    private val legacyStart = intPreferencesKey("active_start_hour")
    private val legacyEnd = intPreferencesKey("active_end_hour")
    private val scheduleKey = stringPreferencesKey("weekly_schedule_v1")

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + Job())
        // Der Dateiname muss auf .preferences_pb enden.
        val file = folder.newFile("settings.preferences_pb").apply { delete() }
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        repository = SettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun raw(): Preferences = runBlocking { dataStore.data.first() }

    @Test
    fun `fresh install uses the default schedule and persists it on the first write`() = runBlocking {
        assertEquals(WeeklySchedule.DEFAULT, repository.currentSettings().schedule)

        // Erster Start: markPermissionsRequested schreibt die Einstellungen.
        repository.updateSettings { it.copy(permissionsRequested = true) }

        assertEquals(WeeklySchedule.DEFAULT, ScheduleCodec.decode(raw()[scheduleKey]!!))
        assertEquals(WeeklySchedule.DEFAULT, repository.currentSettings().schedule)
    }

    @Test
    fun `legacy window becomes every day and is migrated by an unrelated write`() = runBlocking {
        dataStore.edit {
            it[legacyStart] = 9
            it[legacyEnd] = 18
        }
        val expected = WeeklySchedule.everyDay(ActiveWindow.ofHours(9, 18))
        assertEquals(expected, repository.currentSettings().schedule)

        repository.updateSettings { it.copy(intervalMinutes = 30) }

        val prefs = raw()
        assertNotNull(prefs[scheduleKey])
        assertNull(prefs[legacyStart])
        assertNull(prefs[legacyEnd])
        assertEquals(expected, repository.currentSettings().schedule)
        assertEquals(30, repository.currentSettings().intervalMinutes)
    }

    @Test
    fun `legacy values are sanitized like before`() = runBlocking {
        dataStore.edit {
            it[legacyStart] = 30
            it[legacyEnd] = 5
        }
        // Alte Bereinigung: Start höchstens 23, Ende nach dem Start, höchstens 24.
        assertEquals(WeeklySchedule.everyDay(ActiveWindow.ofHours(23, 24)), repository.currentSettings().schedule)
    }

    @Test
    fun `only a legacy start uses the old default end`() = runBlocking {
        dataStore.edit { it[legacyStart] = 10 }
        assertEquals(WeeklySchedule.everyDay(ActiveWindow.ofHours(10, 17)), repository.currentSettings().schedule)
    }

    @Test
    fun `corrupt schedule text falls back to the legacy window or the default`() = runBlocking {
        dataStore.edit {
            it[scheduleKey] = "kaputt"
            it[legacyStart] = 7
            it[legacyEnd] = 16
        }
        assertEquals(WeeklySchedule.everyDay(ActiveWindow.ofHours(7, 16)), repository.currentSettings().schedule)

        dataStore.edit {
            it.remove(legacyStart)
            it.remove(legacyEnd)
        }
        assertEquals(WeeklySchedule.DEFAULT, repository.currentSettings().schedule)
    }

    @Test
    fun `out of range stored minutes are clamped instead of crashing`() = runBlocking {
        dataStore.edit { it[scheduleKey] = List(7) { "1,2000,-5" }.joinToString(";") }
        val schedule = repository.currentSettings().schedule
        assertEquals(ActiveWindow(1425, 1440), schedule[DayOfWeek.MONDAY].window)
    }

    @Test
    fun `reminder style settings round trip and default for older installs`() = runBlocking {
        val defaults = repository.currentSettings()
        assertEquals(10, defaults.snoozeMinutes)
        assertEquals(30, defaults.stepThreshold)
        assertEquals(VibrationLevel.STRONG, defaults.vibration)

        repository.updateSettings { it.copy(snoozeMinutes = 20, stepThreshold = 50, vibration = VibrationLevel.OFF) }
        val stored = repository.currentSettings()
        assertEquals(20, stored.snoozeMinutes)
        assertEquals(50, stored.stepThreshold)
        assertEquals(VibrationLevel.OFF, stored.vibration)

        // Unbekannte Stufe (z. B. aus einer neueren Version) fällt auf „Stark" zurück.
        dataStore.edit { it[stringPreferencesKey("vibration_level")] = "LOUD" }
        assertEquals(VibrationLevel.STRONG, repository.currentSettings().vibration)
    }

    @Test
    fun `custom schedule round trips`() = runBlocking {
        val custom = WeeklySchedule.DEFAULT
            .with(DayOfWeek.FRIDAY) { it.copy(window = ActiveWindow(8 * 60, 13 * 60 + 30)) }
            .with(DayOfWeek.SATURDAY) { DaySchedule(enabled = true, window = ActiveWindow(10 * 60 + 15, 12 * 60)) }
        repository.updateSettings { it.copy(schedule = custom) }
        assertEquals(custom, repository.currentSettings().schedule)
        assertFalse(repository.currentSettings().schedule[DayOfWeek.SUNDAY].enabled)
    }
}
