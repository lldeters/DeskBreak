package dev.stepcron.mobile.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.settings.SettingsEdits
import dev.stepcron.mobile.settings.VibrationLevel
import dev.stepcron.mobile.sync.RestoreState
import dev.stepcron.mobile.sync.SettingsSendState
import dev.stepcron.mobile.sync.SyncProblem
import dev.stepcron.mobile.ui.LocalUiState
import dev.stepcron.mobile.ui.settings.ScheduleTimeContent
import dev.stepcron.mobile.ui.settings.SettingsActions
import dev.stepcron.mobile.ui.settings.SettingsScreen
import dev.stepcron.mobile.ui.theme.DeskBreakPhoneTheme
import dev.stepcron.shared.sync.SettingsUpdate
import dev.stepcron.shared.sync.SyncDay
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots der Einstellungen (wie [ScreenshotTest] nur mit `-Pscreenshots`). Die meisten Bilder sind so hoch,
 * dass die ganze Seite draufpasst (`_full`); dazu der erste Bildschirm in normaler Höhe mit Statusanzeige.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2300dp-xxhdpi")
class SettingsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settings() = shot("settings_full") { SettingsScreen(state = SampleData.state(), actions = NoActions) }

    @Test
    @Config(qualifiers = "w411dp-h2300dp-night-xxhdpi")
    fun settingsDark() = shot("settings_full_dark") { SettingsScreen(state = SampleData.state(), actions = NoActions) }

    @Test
    @Config(qualifiers = "de-w411dp-h2300dp-xxhdpi")
    fun settingsGerman() = shot("settings_full_de") { SettingsScreen(state = SampleData.state(), actions = NoActions) }

    @Test
    @Config(qualifiers = "w360dp-h2700dp-xxhdpi")
    fun settingsSmallPhone() = shot("settings_full_small") { SettingsScreen(state = SampleData.state(), actions = NoActions) }

    @Test
    @Config(qualifiers = "w411dp-h2800dp-xxhdpi", fontScale = 1.3f)
    fun settingsLargeFont() = shot("settings_full_font130") { SettingsScreen(state = SampleData.state(), actions = NoActions) }

    @Test
    @Config(qualifiers = "w360dp-h3300dp-xxhdpi", fontScale = 1.3f)
    fun settingsSmallPhoneLargeFont() = shot("settings_full_small_font130") {
        SettingsScreen(state = SampleData.state(), actions = NoActions)
    }

    /** Erster Bildschirm, während eine Änderung gesendet wird. */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsSending() = shot("settings_sending") {
        SettingsScreen(state = SampleData.state().copy(settingsSend = SettingsSendState.AwaitingConfirmation), actions = NoActions)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsSaved() = shot("settings_saved") {
        val saved = SettingsSendState.Confirmed(System.currentTimeMillis())
        SettingsScreen(state = SampleData.state().copy(settingsSend = saved), actions = NoActions)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun settingsFailedDark() = shot("settings_failed_dark") {
        val failed = SettingsSendState.Failed(SyncProblem.NoWatch, SettingsUpdate(intervalMinutes = 50))
        SettingsScreen(state = SampleData.state().copy(settingsSend = failed), actions = NoActions)
    }

    @Test
    @Config(qualifiers = "de-w411dp-h891dp-xxhdpi")
    fun settingsUnconfirmedGerman() = shot("settings_unconfirmed_de") {
        SettingsScreen(state = SampleData.state().copy(settingsSend = SettingsSendState.Unconfirmed), actions = NoActions)
    }

    /** Pausiert, Auto-Schritte aus, Restore läuft, ein Export arbeitet. */
    @Test
    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    fun settingsPaused() = shot("settings_full_paused") {
        val settings = SampleData.settings.copy(pausedUntilMillis = SampleData.now + 90 * 60_000L, autoStepEnabled = false)
        val state = SampleData.state(snapshot = SampleData.snapshot(settings = settings)).copy(
            restore = RestoreState.WaitingForWatch,
            local = LocalUiState(fileBusy = true),
        )
        SettingsScreen(state = state, actions = NoActions)
    }

    /** Erinnerungen aus, kein Tag aktiv, Wiederherstellung fehlgeschlagen. */
    @Test
    @Config(qualifiers = "de-w411dp-h2600dp-xxhdpi")
    fun settingsOffGerman() = shot("settings_full_off_de") {
        val settings = SampleData.settings.copy(
            enabled = false,
            schedule = SampleData.settings.schedule.map { it.copy(enabled = false) },
            vibration = VibrationLevel.LIGHT.name,
        )
        val state = SampleData.state(snapshot = SampleData.snapshot(settings = settings))
            .copy(restore = RestoreState.Failed(SyncProblem.Timeout))
        SettingsScreen(state = state, actions = NoActions)
    }

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi")
    fun settingsEmpty() = shot("settings_empty") {
        val state = SampleData.state(snapshot = null).let { it.copy(sync = it.sync.copy(problem = SyncProblem.NoWatch)) }
        SettingsScreen(state = state, actions = NoActions)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun timeDialog() = dialogShot("settings_time_dialog") {
        ScheduleTimeContent(
            dayName = "Friday",
            day = SyncDay(enabled = true, startMinute = 8 * 60, endMinute = 15 * 60),
            allDaysMatch = { start, end -> SettingsEdits.allDaysHaveWindow(SampleData.settings, start, end) },
            onConfirm = { _, _, _ -> },
            onDismiss = {},
        )
    }

    @Test
    @Config(qualifiers = "de-w360dp-h740dp-night-xxhdpi")
    fun timeDialogInvalidGerman() = dialogShot("settings_time_dialog_de_dark") {
        ScheduleTimeContent(
            dayName = "Freitag",
            day = SyncDay(enabled = true, startMinute = 17 * 60, endMinute = 17 * 60),
            allDaysMatch = { _, _ -> false },
            onConfirm = { _, _, _ -> },
            onDismiss = {},
            initiallyEditingEnd = true,
        )
    }

    private fun dialogShot(name: String, content: @Composable () -> Unit) = shot(name) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(max = 380.dp),
            ) { content() }
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(SampleData.zone))
        // Uhr von Hand: etwas Zeit vergehen lassen, damit Kreisel als Bogen statt als Punkt erscheinen.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DeskBreakPhoneTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Tut nichts – für Bilder reicht der Zustand. */
    internal object NoActions : SettingsActions {
        override fun syncNow() = Unit
        override fun retrySettings() = Unit
        override fun setEnabled(enabled: Boolean) = Unit
        override fun stepInterval(steps: Int) = Unit
        override fun stepDailyGoal(steps: Int) = Unit
        override fun pauseOneHour() = Unit
        override fun pauseRestOfDay() = Unit
        override fun endPause() = Unit
        override fun setDayEnabled(dayIndex: Int, enabled: Boolean) = Unit
        override fun setDayWindow(dayIndex: Int, startMinute: Int, endMinute: Int) = Unit
        override fun setWindowForAllDays(startMinute: Int, endMinute: Int) = Unit
        override fun stepSnooze(steps: Int) = Unit
        override fun setVibration(level: VibrationLevel) = Unit
        override fun setRespectQuietModes(enabled: Boolean) = Unit
        override fun setOnlyWhenWorn(enabled: Boolean) = Unit
        override fun setAutoSteps(enabled: Boolean) = Unit
        override fun stepStepThreshold(steps: Int) = Unit
        override fun csvFileName() = "deskbreak.csv"
        override fun backupFileName() = "deskbreak.json"
        override fun exportCsv(uri: Uri) = Unit
        override fun exportBackup(uri: Uri) = Unit
        override fun loadBackup(uri: Uri) = Unit
        override fun confirmRestore() = Unit
        override fun cancelRestore() = Unit
        override fun dismissRestoreResult() = Unit
        override fun onNoFileApp() = Unit
    }
}
