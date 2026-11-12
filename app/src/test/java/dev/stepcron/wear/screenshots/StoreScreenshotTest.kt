package dev.stepcron.wear.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeSource
import androidx.wear.compose.material.TimeText
import dev.stepcron.wear.R
import dev.stepcron.wear.data.db.MovementEvent
import dev.stepcron.wear.data.db.MovementEventType
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.domain.DailyCounts
import dev.stepcron.wear.domain.HourlyCounts
import dev.stepcron.wear.domain.WeeklySchedule
import dev.stepcron.wear.ui.HomeStatus
import dev.stepcron.wear.ui.HomeUiState
import dev.stepcron.wear.ui.StatsUiState
import dev.stepcron.wear.ui.exercise.ExerciseScreen
import dev.stepcron.wear.ui.home.HomeScreen
import dev.stepcron.wear.ui.stats.StatsScreen
import dev.stepcron.wear.ui.theme.DeskBreakTheme
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock

/**
 * Screenshots der Uhr für den Play Store – nur mit `-Pscreenshots` (siehe app/build.gradle.kts):
 *
 *     ./gradlew :app:testDebugUnitTest -Pscreenshots --tests '*StoreScreenshotTest*'
 *
 * Schreibt `wear-<sprache>-<nr>-<seite>.png` nach `app/build/screenshots/store/`: quadratisch 454 × 454 px
 * (227 dp, große runde Uhr bei xhdpi), schwarzer Hintergrund ohne Maske und ohne Uhrenrahmen – so verlangt es
 * Play für Wear OS (1:1, mindestens 384 × 384). Beispielstand wie bei den Handy-Bildern: Dienstag, 10:22 Uhr.
 * Die fertigen Bilder liegen unter `docs/play-store/assets/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w227dp-h227dp-round-xhdpi")
class StoreScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "de-w227dp-h227dp-round-xhdpi")
    fun german() = watchSet("de")

    @Test
    @Config(qualifiers = "en-w227dp-h227dp-round-xhdpi")
    fun english() = watchSet("en")

    /** Je Test nur ein `setContent`: Die Seiten wechseln über einen Zustand, `key` setzt jede Seite frisch auf. */
    private fun watchSet(language: String) {
        val shots = listOf(
            WatchShot("wear-$language-1-home") { Home() },
            WatchShot("wear-$language-2-stats-week", scrollToIndex = STATS_WEEK_ITEM, offsetPx = 60f) { Stats() },
            WatchShot("wear-$language-3-stats-month", scrollToIndex = STATS_MONTH_ITEM, offsetPx = 40f) { Stats() },
            WatchShot("wear-$language-4-exercise", elapsedMillis = 23_000L) {
                // Erste Bewegungsidee der App in der jeweiligen Sprache („10 Kniebeugen" / „10 squats").
                ExerciseScreen(exercise = stringArrayResource(R.array.exercises).first(), onCompleted = {}, onBack = {})
            },
        )
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZONE))
        // Robolectric bildet ein Handy-Android nach, dessen Standard-Theme eine Titelleiste hat; auf der Uhr gibt es
        // keine.
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        var current by mutableStateOf(shots.first())
        compose.setContent {
            DeskBreakTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    // Wie DeskBreakNavHost: gemeinsamer Scaffold mit der Uhrzeit oben – hier fest auf 10:22.
                    Scaffold(timeText = { TimeText(timeSource = FixedTime) }) {
                        key(current.name) { current.content() }
                    }
                }
            }
        }
        shots.forEach { shot ->
            current = shot
            compose.waitForIdle()
            if (shot.scrollToIndex != null) {
                // Der Eintrag steht danach oben; ein Stück zurück, damit er unter der Uhrzeit in der Mitte steht.
                val list = compose.onNode(hasScrollToIndexAction())
                list.performScrollToIndex(shot.scrollToIndex)
                list.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> scrollBy(0f, -shot.offsetPx) }
                compose.waitForIdle()
            }
            if (shot.elapsedMillis > 0L) {
                // Die Übung rechnet mit SystemClock.elapsedRealtime(); danach einen Tick laufen lassen.
                ShadowSystemClock.advanceBy(Duration.ofMillis(shot.elapsedMillis))
                compose.mainClock.advanceTimeBy(500L)
                compose.waitForIdle()
            }
            capture(shot.name)
        }
    }

    /**
     * Eine Seite. Mit [scrollToIndex] wird die Liste zu diesem Eintrag gescrollt und dann um [offsetPx] zurück, damit
     * er mittig unter der Uhrzeit steht; [elapsedMillis] lässt so viel Zeit seit dem Aufbau vergehen (für den
     * Fortschrittsring der Übung).
     */
    private class WatchShot(
        val name: String,
        val scrollToIndex: Int? = null,
        val offsetPx: Float = 0f,
        val elapsedMillis: Long = 0L,
        val content: @Composable () -> Unit,
    )

    private fun capture(name: String) {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        // Ohne Alphakanal: Play lehnt transparente Hintergründe bei Wear-OS-Screenshots ab.
        bitmap.setHasAlpha(false)
        val base = System.getProperty("screenshots.dir") ?: "build/screenshots"
        val dir = File(base, "store").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private object FixedTime : TimeSource {
        override val currentTime: String
            @Composable get() = "10:22"
    }

    private companion object {
        /**
         * Karten der Auswertung („7 Tage" und „5 Wochen") als Index für die Scroll-Aktion der Liste. Die
         * ScalingLazyColumn zählt dabei einen Eintrag mehr als StatsScreen (dort Index 5 und 6).
         */
        const val STATS_WEEK_ITEM = 6
        const val STATS_MONTH_ITEM = 7

        /** Pausen und verpasste Erinnerungen der Beispiel-Werktage, im Wechsel. */
        val MOVEMENTS = listOf(9, 7, 5, 8, 3)
        val MISSED = listOf(1, 2, 3, 0, 4)

        val ZONE: ZoneId = ZoneId.of("Europe/Berlin")
        val TODAY: LocalDate = LocalDate.of(2026, 11, 10)
        val NOW: Long = millis(TODAY, 10, 22)

        fun millis(date: LocalDate, hour: Int, minute: Int): Long =
            LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(ZONE).toInstant().toEpochMilli()
    }

    // --- Seiten mit Beispieldaten ---

    @Composable
    private fun Home() {
        val state = HomeUiState(
            loading = false,
            enabled = true,
            intervalMinutes = 45,
            schedule = WeeklySchedule.DEFAULT,
            today = WeeklySchedule.DEFAULT[DayOfWeek.TUESDAY],
            dailyGoal = 8,
            todayMovements = 3,
            status = HomeStatus.Scheduled(
                atMillis = NOW + 23 * 60_000L,
                remainingMinutes = 23,
                progress = 22f / 45f,
                snoozed = false,
            ),
            stepTrackingActive = true,
        )
        HomeScreen(
            state = state,
            onEnabledChange = {},
            onManualReset = {},
            onOpenPause = {},
            onResume = {},
            onOpenStats = {},
            onIntervalStep = {},
            onGoalStep = {},
            onOpenSchedule = {},
            onOpenReminderSettings = {},
            onAutoStepChange = {},
            onRequestPermissions = {},
            onOpenExactAlarmSettings = {},
            onOpenLicense = {},
        )
    }

    private fun event(hour: Int, minute: Int, type: MovementEventType) =
        MovementEvent(timestamp = millis(TODAY, hour, minute), eventType = type, intervalMinutes = 45)

    @Composable
    private fun Stats() {
        val settings = ReminderSettings(enabled = true, intervalMinutes = 45, dailyGoal = 8)
        val todayEvents = listOf(
            event(8, 41, MovementEventType.COMPLETED_REMINDER),
            event(9, 12, MovementEventType.AUTO_STEP_RESET),
            event(9, 58, MovementEventType.COMPLETED_REMINDER),
        )
        // Fünf Wochen Werktage mit wechselndem Erfolg (wie die Beispieldaten der Handy-Screenshots).
        val daily = (34 downTo 1).mapNotNull { offset ->
            val date = TODAY.minusDays(offset.toLong())
            if (date.dayOfWeek >= DayOfWeek.SATURDAY) return@mapNotNull null
            val variant = (offset * 7 + date.dayOfMonth) % 5
            DailyCounts(date, movements = MOVEMENTS[variant], snoozes = 0, missed = MISSED[variant])
        } + DailyCounts(TODAY, movements = 3, snoozes = 0, missed = 0)
        val hourly = listOf(
            HourlyCounts(8, 18, 1), HourlyCounts(9, 21, 0), HourlyCounts(10, 17, 2), HourlyCounts(11, 15, 2),
            HourlyCounts(12, 14, 3), HourlyCounts(13, 12, 4), HourlyCounts(14, 9, 8), HourlyCounts(15, 7, 6),
            HourlyCounts(16, 4, 5),
        )
        val state = StatsUiState.create(
            todayEvents = todayEvents,
            dailyCounts = daily,
            hourlyCounts = hourly,
            streak = 3,
            settings = settings,
            nowMillis = NOW,
            today = TODAY,
            zone = ZONE,
        )
        StatsScreen(
            state = state,
            today = settings.schedule[DayOfWeek.TUESDAY],
            showDebugTools = false,
            onSeedDemoData = {},
            onClearHistory = {},
        )
    }
}
