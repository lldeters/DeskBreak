package dev.stepcron.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.stepcron.mobile.ui.DeskBreakPhoneRoot
import dev.stepcron.mobile.ui.LinkRequest
import dev.stepcron.mobile.ui.linkTarget
import dev.stepcron.mobile.ui.theme.DeskBreakPhoneTheme

/**
 * Einzige Activity der Handy-App. Nimmt auch Deep Links an (`deskbreak://unlock`, siehe Manifest): beim Kaltstart in
 * [onCreate], läuft die App schon, wegen `launchMode="singleTop"` in [onNewIntent] – ohne zweite Activity darüber.
 */
class MainActivity : ComponentActivity() {

    private val app get() = application as DeskBreakPhoneApp

    /** Deep Link, den die Navigation noch nicht geöffnet hat. */
    private var linkRequest by mutableStateOf<LinkRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Nach Drehen oder Prozessende stellt die Navigation die zuletzt offene Seite selbst wieder her; den Link
        // dann nicht noch einmal öffnen.
        if (savedInstanceState == null) receive(intent)
        setContent {
            DeskBreakPhoneTheme {
                DeskBreakPhoneRoot(
                    linkRequest = linkRequest,
                    onLinkHandled = { handled -> if (linkRequest === handled) linkRequest = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent?) {
        val target = linkTarget(intent) ?: return
        linkRequest = LinkRequest(target)
    }

    /** Im Vordergrund: Listener des Data Layers an, um einen frischen Stand bitten und Käufe bei Play abfragen. */
    override fun onStart() {
        super.onStart()
        app.repository.onForeground()
        app.license.onForeground()
    }

    override fun onStop() {
        app.repository.onBackground()
        super.onStop()
    }
}
