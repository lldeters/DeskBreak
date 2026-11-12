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
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeSource
import androidx.wear.compose.material.TimeText
import dev.stepcron.billing.BillingOffer
import dev.stepcron.shared.license.LicenseRules
import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.shared.license.Unlock
import dev.stepcron.wear.domain.WeeklySchedule
import dev.stepcron.wear.ui.HomeStatus
import dev.stepcron.wear.ui.HomeUiState
import dev.stepcron.wear.ui.home.HomeScreen
import dev.stepcron.wear.ui.license.LicenseScreen
import dev.stepcron.wear.ui.license.LicenseUiState
import dev.stepcron.wear.ui.license.PhoneLinkState
import dev.stepcron.wear.ui.theme.DeskBreakTheme
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots der Freischaltung auf der Uhr – zum Prüfen der Texte (vor allem Deutsch auf kleinen runden Uhren), nur
 * mit `-Pscreenshots`:
 *
 *     ./gradlew :app:testDebugUnitTest -Pscreenshots --tests '*wear.screenshots.LicenseScreenshotTest*'
 *
 * Schreibt `license-<sprache>-<nr>-<zustand>.png` nach `app/build/screenshots/license/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w192dp-h192dp-round-xhdpi")
class LicenseScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "de-w192dp-h192dp-round-xhdpi")
    fun germanSmall() = licenseSet("de-small")

    @Test
    @Config(qualifiers = "en-w192dp-h192dp-round-xhdpi")
    fun englishSmall() = licenseSet("en-small")

    @Test
    @Config(qualifiers = "de-w227dp-h227dp-round-xhdpi")
    fun germanLarge() = licenseSet("de-large")

    private fun licenseSet(variant: String) {
        val shots = listOf(
            Shot("license-$variant-1-home-trial") { Home(LicenseStatus.Trial(NOW + 2 * LicenseRules.DAY_MILLIS, 2)) },
            Shot("license-$variant-2-home-expired") { Home(LicenseStatus.Expired(NOW - LicenseRules.DAY_MILLIS)) },
            Shot("license-$variant-3-home-expired-bottom", scrollToIndex = LAST) {
                Home(LicenseStatus.Expired(NOW - LicenseRules.DAY_MILLIS))
            },
            Shot("license-$variant-4-screen-expired") { License(expiredWithOffers) },
            Shot("license-$variant-5-screen-expired-offers", scrollToIndex = 4) { License(expiredWithOffers) },
            Shot("license-$variant-6-screen-expired-bottom", scrollToIndex = LAST) { License(expiredWithOffers) },
            Shot("license-$variant-7-screen-phone-opened") {
                License(expiredWithOffers.copy(offers = emptyList(), phoneLink = PhoneLinkState.OPENED))
            },
            Shot("license-$variant-8-screen-subscription") {
                License(LicenseUiState(loading = false, status = LicenseStatus.Unlocked(Unlock.SUBSCRIPTION)))
            },
            Shot("license-$variant-9-screen-lifetime") {
                License(LicenseUiState(loading = false, status = LicenseStatus.Unlocked(Unlock.LIFETIME)))
            },
        )
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        var current by mutableStateOf(shots.first())
        compose.setContent {
            DeskBreakTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                val list = compose.onNode(hasScrollToIndexAction())
                if (shot.scrollToIndex == LAST) {
                    // Die Länge der Liste ist von außen nicht ablesbar: vom Ende her den ersten gültigen Index nehmen.
                    (MAX_ITEMS downTo 0).first { runCatching { list.performScrollToIndex(it) }.isSuccess }
                } else {
                    list.performScrollToIndex(shot.scrollToIndex)
                }
                compose.waitForIdle()
            }
            capture(shot.name)
        }
    }

    private class Shot(val name: String, val scrollToIndex: Int? = null, val content: @Composable () -> Unit)

    private fun capture(name: String) {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmap.setHasAlpha(false)
        val base = System.getProperty("screenshots.dir") ?: "build/screenshots"
        val dir = File(base, "license").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Home(license: LicenseStatus) {
        val active = license.isActive
        HomeScreen(
            state = HomeUiState(
                loading = false,
                enabled = true,
                intervalMinutes = 45,
                schedule = WeeklySchedule.DEFAULT,
                today = WeeklySchedule.DEFAULT[DayOfWeek.TUESDAY],
                dailyGoal = 8,
                todayMovements = 3,
                status = if (active) {
                    HomeStatus.Scheduled(atMillis = NOW + 23 * 60_000L, remainingMinutes = 23, progress = 0.5f, snoozed = false)
                } else {
                    HomeStatus.Locked
                },
                license = license,
            ),
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

    @Composable
    private fun License(state: LicenseUiState) {
        LicenseScreen(state = state, onUnlockOnPhone = {}, onBuy = {}, onCheckPurchases = {}, onManageSubscription = {})
    }

    private object FixedTime : TimeSource {
        override val currentTime: String
            @Composable get() = "10:22"
    }

    private companion object {
        const val NOW = 1_800_000_000_000L

        /** Bis ans Ende der Liste scrollen. */
        const val LAST = -1
        const val MAX_ITEMS = 40

        val expiredWithOffers = LicenseUiState(
            loading = false,
            status = LicenseStatus.Expired(NOW - LicenseRules.DAY_MILLIS),
            offers = listOf(
                BillingOffer(Unlock.LIFETIME, "5,00 €"),
                BillingOffer(Unlock.SUBSCRIPTION, "1,00 €", "P1M"),
            ),
        )
    }
}
