package dev.stepcron.mobile.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.stepcron.mobile.ui.history.HistoryScreen
import dev.stepcron.mobile.ui.theme.DeskBreakPhoneTheme
import dev.stepcron.mobile.ui.today.TodayActions
import dev.stepcron.mobile.ui.today.TodayScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Rendert die Bildschirme mit [SampleData] als PNG nach `mobile/build/screenshots/` – zum Anschauen ohne Gerät.
 * Läuft nur mit `./gradlew :mobile:testDebugUnitTest -Pscreenshots` (siehe mobile/build.gradle.kts).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun today() = shot("today") { TodayScreen(state = SampleData.state(), actions = TodayActions()) }

    @Test
    fun todayEmpty() = shot("today_empty") { TodayScreen(state = SampleData.state(snapshot = null), actions = TodayActions()) }

    @Test
    fun history() = shot("history") { HistoryScreen(state = SampleData.state()) }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun todayDark() = shot("today_dark") { TodayScreen(state = SampleData.state(), actions = TodayActions()) }

    @Test
    @Config(qualifiers = "de-w411dp-h891dp-xxhdpi")
    fun todayGerman() = shot("today_de") { TodayScreen(state = SampleData.state(), actions = TodayActions()) }

    @Test
    fun todayPaused() = shot("today_paused") {
        val paused = SampleData.settings.copy(pausedUntilMillis = SampleData.now + 90 * 60_000L)
        TodayScreen(state = SampleData.state(snapshot = SampleData.snapshot(settings = paused)), actions = TodayActions())
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(SampleData.zone))
        compose.setContent {
            DeskBreakPhoneTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
