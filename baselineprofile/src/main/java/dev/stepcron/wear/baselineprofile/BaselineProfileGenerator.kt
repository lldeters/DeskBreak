package dev.stepcron.wear.baselineprofile

import android.content.res.Resources
import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Erzeugt das Baseline-Profil von DeskBreak auf einer per ADB verbundenen Uhr:
 *
 *     ./gradlew :app:generateBaselineProfile
 *
 * Das Ergebnis landet in `app/src/main/generated/baselineProfiles/` und gehört eingecheckt. Voraussetzung ist
 * Wear OS 4 (API 33) oder neuer; ältere Uhren nur mit Root.
 *
 * Durchlaufen wird, was nach dem Öffnen flüssig sein soll: Start, Scrollen der Startliste, Auswertung, Zeitplan
 * mit Wochentag und Uhrzeit-Auswahl, Pause und die Einstellungen der Erinnerung. Der Durchlauf ändert nichts an
 * den Einstellungen der App (kein Schalter, keine Bestätigung). Ziele, die es im aktuellen Zustand nicht gibt
 * (z. B. „Pausieren" bei ausgeschalteten Erinnerungen), werden übersprungen.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        // Ohne die Berechtigungsdialoge des ersten Starts (auf älteren Uhren gibt es die erste nicht).
        device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
        device.executeShellCommand("pm grant $packageName android.permission.ACTIVITY_RECOGNITION")

        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), TIMEOUT_MILLIS)

        scrollListDownAndUp()

        openFromList(string("home_stats_label")) { scrollListDownAndUp() }

        openFromList(string("home_schedule_label")) {
            openFromList(stringArray("weekday_long").firstOrNull()) {
                // Uhrzeit-Auswahl nur öffnen, nichts übernehmen.
                openFromList(string("schedule_start_label")) { device.waitForIdle() }
            }
        }

        openFromList(string("home_pause_label")) {
            openFromList(string("pause_vacation")) { device.waitForIdle() }
        }

        openFromList(string("home_reminder_settings_label")) { scrollListDownAndUp() }
    }

    /**
     * Scrollt die Liste bis zum Eintrag mit [label], öffnet ihn, führt [onScreen] aus und geht wieder zurück.
     * Fehlt der Eintrag, passiert nichts.
     */
    private fun MacrobenchmarkScope.openFromList(label: String?, onScreen: () -> Unit) {
        if (label == null) return
        val item = device.findObject(By.text(label))
            ?: list()?.scrollUntil(Direction.DOWN, Until.findObject(By.text(label)))
        if (item == null) {
            Log.w(TAG, "\"$label\" nicht gefunden - Schritt uebersprungen")
            list()?.fling(Direction.UP)
            return
        }
        item.click()
        device.waitForIdle()
        onScreen()
        device.pressBack()
        device.waitForIdle()
        list()?.fling(Direction.UP)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.scrollListDownAndUp() {
        val list = list() ?: return
        list.fling(Direction.DOWN)
        device.waitForIdle()
        list.fling(Direction.UP)
        device.waitForIdle()
    }

    /** Die scrollbare Liste des aktuellen Bildschirms; mit Rand, damit keine Systemgeste (Zurückwischen) auslöst. */
    private fun MacrobenchmarkScope.list(): UiObject2? =
        device.findObject(By.pkg(packageName).scrollable(true))?.apply { setGestureMargin(device.displayWidth / 5) }

    /** Text der App in der Sprache der Uhr – so findet der Durchlauf die Einträge auf Deutsch wie auf Englisch. */
    private fun MacrobenchmarkScope.string(name: String): String? {
        val resources = targetResources()
        val id = resources.getIdentifier(name, "string", packageName)
        return if (id != 0) resources.getString(id) else null
    }

    private fun MacrobenchmarkScope.stringArray(name: String): List<String> {
        val resources = targetResources()
        val id = resources.getIdentifier(name, "array", packageName)
        return if (id != 0) resources.getStringArray(id).asList() else emptyList()
    }

    private fun MacrobenchmarkScope.targetResources(): Resources =
        InstrumentationRegistry.getInstrumentation().context.packageManager.getResourcesForApplication(packageName)

    private companion object {
        const val TAG = "BaselineProfile"
        const val TARGET_PACKAGE = "dev.stepcron.wear"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
