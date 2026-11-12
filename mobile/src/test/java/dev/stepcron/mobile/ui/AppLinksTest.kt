package dev.stepcron.mobile.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.stepcron.mobile.MainActivity
import dev.stepcron.shared.sync.DeepLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Deep Link `deskbreak://unlock` (von der Uhr): welcher Intent wohin führt, ob das Manifest ihn an [MainActivity]
 * zustellt, und dass nach dem Öffnen Zurück immer auf Heute landet. Robolectric (SDK 34) mit schlichter
 * [Application], damit keine Verbindung zu Uhr oder Google Play aufgebaut wird.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppLinksTest {

    @get:Rule
    val compose = createComposeRule()

    // --- Intent → Ziel ---

    @Test
    fun unlockLinkOpensUnlock() {
        assertEquals(LinkTarget.UNLOCK, linkTarget(view(DeepLinks.UNLOCK)))
        assertEquals(LinkTarget.UNLOCK, linkTarget(view("DeskBreak://Unlock")))
        // Wie von RemoteActivityHelper: mit Kategorie BROWSABLE und eigener Task.
        val remote = view(DeepLinks.UNLOCK).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals(LinkTarget.UNLOCK, linkTarget(remote))
    }

    @Test
    fun otherIntentsOpenNothing() {
        assertNull(linkTarget(null))
        assertNull(linkTarget(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)))
        assertNull(linkTarget(Intent(Intent.ACTION_VIEW)))
        assertNull(linkTarget(view("deskbreak://settings")))
        assertNull(linkTarget(view("https://unlock")))
        assertNull(linkTarget(Intent(Intent.ACTION_SEND, Uri.parse(DeepLinks.UNLOCK))))
    }

    @Test
    fun relaunchFromRecentsDoesNotReopenLink() {
        val fromHistory = view(DeepLinks.UNLOCK).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(linkTarget(fromHistory))
    }

    @Test
    fun schemeAndHost() {
        assertEquals(LinkTarget.UNLOCK, linkTarget(DeepLinks.SCHEME, DeepLinks.HOST_UNLOCK))
        assertNull(linkTarget(DeepLinks.SCHEME, null))
        assertNull(linkTarget(null, DeepLinks.HOST_UNLOCK))
        assertNull(linkTarget("market", DeepLinks.HOST_UNLOCK))
    }

    // --- Manifest ---

    @Test
    fun manifestDeliversLinkToMainActivity() {
        val context = RuntimeEnvironment.getApplication()
        val intent = view(DeepLinks.UNLOCK).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.packageName)
        val matches = context.packageManager.queryIntentActivities(intent, 0)
        assertEquals(listOf(MainActivity::class.java.name), matches.map { it.activityInfo.name })
        assertEquals(true, matches.single().activityInfo.exported)
    }

    @Test
    fun runningAppGetsLinkInSameActivity() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, info.launchMode)
    }

    // --- Navigation ---

    @Test
    fun fromTodayBackLandsOnToday() {
        val nav = navHost()
        open(nav)
        assertEquals(listOf(TODAY, UNLOCK_ROUTE), routes(nav))
        back(nav)
        assertEquals(listOf(TODAY), routes(nav))
    }

    @Test
    fun fromAnotherTabBackLandsOnToday() {
        val nav = navHost()
        compose.runOnIdle { nav.navigate(SETTINGS) }
        open(nav)
        assertEquals(listOf(TODAY, UNLOCK_ROUTE), routes(nav))
        back(nav)
        assertEquals(listOf(TODAY), routes(nav))
    }

    @Test
    fun alreadyOnUnlockStaysSingle() {
        val nav = navHost()
        // In der App von den Einstellungen aus geöffnet, dann kommt der Link der Uhr.
        compose.runOnIdle {
            nav.navigate(SETTINGS)
            nav.navigate(UNLOCK_ROUTE)
        }
        open(nav)
        assertEquals(listOf(TODAY, UNLOCK_ROUTE), routes(nav))
        open(nav)
        assertEquals(listOf(TODAY, UNLOCK_ROUTE), routes(nav))
    }

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    /** Dieselben Routen wie in der App (Heute ist Start), ohne Inhalt. */
    private fun navHost(): NavHostController {
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = TODAY) {
                composable(TODAY) {}
                composable(HISTORY) {}
                composable(SETTINGS) {}
                composable(UNLOCK_ROUTE) {}
            }
        }
        compose.waitForIdle()
        return nav
    }

    private fun open(nav: NavHostController) = compose.runOnIdle { nav.openFromLink(LinkTarget.UNLOCK) }

    private fun back(nav: NavHostController) = compose.runOnIdle { nav.navigateUp() }

    private fun routes(nav: NavHostController): List<String?> = compose.runOnIdle {
        nav.currentBackStack.value.mapNotNull { it.destination.route }
    }

    private companion object {
        const val TODAY = "today"
        const val HISTORY = "history"
        const val SETTINGS = "settings"
    }
}
