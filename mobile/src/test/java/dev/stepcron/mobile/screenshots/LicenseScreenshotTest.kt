package dev.stepcron.mobile.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.stepcron.billing.BillingAvailability
import dev.stepcron.billing.BillingProblem
import dev.stepcron.billing.BillingState
import dev.stepcron.mobile.R
import dev.stepcron.mobile.ui.license.LicenseUiState
import dev.stepcron.mobile.ui.license.NoLicenseActions
import dev.stepcron.mobile.ui.license.UnlockScreen
import dev.stepcron.mobile.ui.settings.SettingsScreen
import dev.stepcron.mobile.ui.theme.DeskBreakPhoneTheme
import dev.stepcron.mobile.ui.today.TodayActions
import dev.stepcron.mobile.ui.today.TodayScreen
import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.shared.license.Unlock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Screenshots der Freischaltung (wie [ScreenshotTest] nur mit `-Pscreenshots`): die Seite „Freischalten" in allen
 * Zuständen (mit Titelleiste wie in der App, damit man sieht, was ohne Scrollen sichtbar ist), dazu Heute mit
 * Hinweis während bzw. nach der Testphase und die Lizenz-Karte der Einstellungen. Billing kommt als fester
 * [BillingState] statt aus Google Play.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class LicenseScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // --- Freischalten ---

    @Test
    fun unlockTrial() = unlockShot("unlock_trial", SampleData.license())

    @Test
    @Config(qualifiers = "w411dp-h1300dp-xxhdpi")
    fun unlockTrialFull() = unlockShot("unlock_trial_full", SampleData.license())

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun unlockTrialDark() = unlockShot("unlock_trial_dark", SampleData.license())

    @Test
    @Config(qualifiers = "de-w411dp-h1300dp-xxhdpi")
    fun unlockTrialGerman() = unlockShot("unlock_trial_de_full", SampleData.license(billing = SampleData.billingReady(german = true)))

    @Test
    @Config(qualifiers = "w411dp-h1300dp-xxhdpi")
    fun unlockExpired() = unlockShot("unlock_expired_full", SampleData.license(status = SampleData.expired))

    @Test
    @Config(qualifiers = "de-w411dp-h891dp-night-xxhdpi")
    fun unlockExpiredGermanDark() = unlockShot(
        "unlock_expired_de_dark",
        SampleData.license(status = SampleData.expired, billing = SampleData.billingReady(german = true)),
    )

    @Test
    fun unlockConnecting() = unlockShot("unlock_connecting", SampleData.license(billing = BillingState()))

    @Test
    fun unlockUnavailable() = unlockShot(
        "unlock_unavailable",
        SampleData.license(
            billing = BillingState(availability = BillingAvailability.UNAVAILABLE, problem = BillingProblem.BILLING_UNAVAILABLE),
        ),
    )

    @Test
    @Config(qualifiers = "de-w411dp-h891dp-xxhdpi")
    fun unlockOfflineGerman() = unlockShot(
        "unlock_offline_de",
        SampleData.license(billing = BillingState(availability = BillingAvailability.ERROR, problem = BillingProblem.NETWORK)),
    )

    @Test
    fun unlockProductsMissing() = unlockShot(
        "unlock_products_missing",
        SampleData.license(billing = BillingState(availability = BillingAvailability.READY, owned = emptySet(), checkedAtMillis = 1L)),
    )

    @Test
    @Config(qualifiers = "w411dp-h1300dp-xxhdpi")
    fun unlockPending() = unlockShot(
        "unlock_pending_full",
        SampleData.license(billing = SampleData.billingReady(pending = setOf(Unlock.LIFETIME))),
    )

    @Test
    fun unlockLifetime() = unlockShot(
        "unlock_lifetime",
        SampleData.license(status = LicenseStatus.Unlocked(Unlock.LIFETIME), billing = SampleData.billingReady(owned = setOf(Unlock.LIFETIME))),
    )

    @Test
    @Config(qualifiers = "w411dp-h1100dp-xxhdpi")
    fun unlockSubscription() = unlockShot(
        "unlock_subscription_full",
        SampleData.license(
            status = LicenseStatus.Unlocked(Unlock.SUBSCRIPTION),
            billing = SampleData.billingReady(owned = setOf(Unlock.SUBSCRIPTION)),
        ),
    )

    @Test
    @Config(qualifiers = "de-w411dp-h1100dp-night-xxhdpi")
    fun unlockSubscriptionGermanDark() = unlockShot(
        "unlock_subscription_de_dark_full",
        SampleData.license(
            status = LicenseStatus.Unlocked(Unlock.SUBSCRIPTION),
            billing = SampleData.billingReady(german = true, owned = setOf(Unlock.SUBSCRIPTION)),
        ),
    )

    @Test
    @Config(qualifiers = "w360dp-h740dp-xxhdpi", fontScale = 1.3f)
    fun unlockSmallPhoneLargeFont() = unlockShot("unlock_small_font130", SampleData.license())

    @Test
    @Config(qualifiers = "de-w360dp-h1900dp-xxhdpi", fontScale = 1.3f)
    fun unlockSmallPhoneLargeFontGerman() = unlockShot(
        "unlock_small_font130_de_full",
        SampleData.license(billing = SampleData.billingReady(german = true)),
    )

    @Test
    @Config(qualifiers = "de-w360dp-h1900dp-xxhdpi", fontScale = 1.3f)
    fun unlockSmallPhoneLargeFontGermanMonthly() = unlockShot(
        "unlock_small_font130_de_monthly_full",
        // Nur das Abo: es ist vorgewählt, Knopf und Satz darunter zeigen die längsten Texte.
        SampleData.license(
            billing = SampleData.billingReady(german = true).let { it.copy(offers = it.offers - Unlock.LIFETIME) },
        ),
    )

    // --- Heute ---

    @Test
    fun todayTrial() = shot("today_trial") {
        TodayScreen(state = SampleData.state().copy(license = SampleData.license()), actions = TodayActions())
    }

    @Test
    @Config(qualifiers = "de-w411dp-h891dp-night-xxhdpi")
    fun todayTrialGermanDark() = shot("today_trial_de_dark") {
        TodayScreen(state = SampleData.state().copy(license = SampleData.license()), actions = TodayActions())
    }

    @Test
    fun todayExpired() = shot("today_expired") {
        TodayScreen(state = SampleData.state().copy(license = SampleData.license(status = SampleData.expired)), actions = TodayActions())
    }

    @Test
    @Config(qualifiers = "de-w360dp-h891dp-xxhdpi", fontScale = 1.3f)
    fun todayExpiredGermanLargeFont() = shot("today_expired_de_small_font130") {
        TodayScreen(state = SampleData.state().copy(license = SampleData.license(status = SampleData.expired)), actions = TodayActions())
    }

    @Test
    fun todayEmptyTrial() = shot("today_empty_trial") {
        TodayScreen(state = SampleData.state(snapshot = null).copy(license = SampleData.license()), actions = TodayActions())
    }

    // --- Einstellungen: Lizenz-Karte (unten auf der Seite) ---

    @Test
    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    fun settingsTrial() = shot("settings_license_trial_full") {
        SettingsScreen(state = SampleData.state().copy(license = SampleData.license()), actions = SettingsScreenshotTest.NoActions)
    }

    @Test
    @Config(qualifiers = "de-w411dp-h2600dp-night-xxhdpi")
    fun settingsSubscriptionGermanDark() = shot("settings_license_subscription_de_dark_full") {
        val license = SampleData.license(status = LicenseStatus.Unlocked(Unlock.SUBSCRIPTION), restoring = true)
        SettingsScreen(state = SampleData.state().copy(license = license), actions = SettingsScreenshotTest.NoActions)
    }

    @Test
    @Config(qualifiers = "w411dp-h1700dp-xxhdpi")
    fun settingsLifetimeWithoutWatch() = shot("settings_license_lifetime_empty") {
        val license = SampleData.license(status = LicenseStatus.Unlocked(Unlock.LIFETIME))
        SettingsScreen(state = SampleData.state(snapshot = null).copy(license = license), actions = SettingsScreenshotTest.NoActions)
    }

    /** Die Seite wie in der App: Titelleiste mit Zurück, darunter der Inhalt. */
    @OptIn(ExperimentalMaterial3Api::class)
    private fun unlockShot(name: String, license: LicenseUiState) = shot(name) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                val unlocked = license.status is LicenseStatus.Unlocked
                TopAppBar(
                    title = { Text(stringResource(if (unlocked) R.string.title_license else R.string.title_unlock)) },
                    navigationIcon = {
                        IconButton(onClick = {}) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
        ) { padding ->
            UnlockScreen(state = license, actions = NoLicenseActions, modifier = Modifier.padding(padding))
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(SampleData.zone))
        // Uhr von Hand: etwas Zeit vergehen lassen, damit Ringe und Kreisel fertig gezeichnet sind.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DeskBreakPhoneTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
