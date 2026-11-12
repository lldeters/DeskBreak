package dev.stepcron.mobile.ui

import android.content.Intent
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import dev.stepcron.shared.sync.DeepLinks

/** Seiten, die von außen über einen Deep Link geöffnet werden können (siehe [DeepLinks]). */
enum class LinkTarget { UNLOCK }

/**
 * Ein angekommener Deep Link, bis die Navigation ihn übernommen hat. Bewusst ohne `data`: Jeder Intent ist eine
 * eigene Anfrage, auch wenn die Uhr zweimal hintereinander dieselbe Seite öffnet.
 */
class LinkRequest(val target: LinkTarget)

/**
 * Ziel eines Intents an die App: `deskbreak://unlock` (ACTION_VIEW, z. B. „Am Handy freischalten" auf der Uhr) →
 * [LinkTarget.UNLOCK]. Alles andere – Start über das App-Symbol, unbekannte Adressen, ein aus „Zuletzt verwendet"
 * wieder geöffneter alter Link – ergibt `null`; die App startet dann wie gewohnt.
 */
fun linkTarget(intent: Intent?): LinkTarget? {
    if (intent == null || intent.action != Intent.ACTION_VIEW) return null
    // Android liefert beim Öffnen aus „Zuletzt verwendet" den ursprünglichen Intent noch einmal.
    if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
    val uri = intent.data ?: return null
    return linkTarget(uri.scheme, uri.host)
}

/** Schema und Host eines Deep Links → Ziel (Groß-/Kleinschreibung egal, wie bei Android). */
internal fun linkTarget(scheme: String?, host: String?): LinkTarget? = when {
    !scheme.equals(DeepLinks.SCHEME, ignoreCase = true) -> null
    host.equals(DeepLinks.HOST_UNLOCK, ignoreCase = true) -> LinkTarget.UNLOCK
    else -> null
}

/** Route der Seite „DeskBreak freischalten" (eine Seite über den drei Seiten der Navigationsleiste). */
internal const val UNLOCK_ROUTE = "unlock"

/**
 * Öffnet [target] auf Wunsch eines Deep Links. Darunter liegt immer genau Heute (die Startseite), damit Zurück dort
 * landet – egal, ob die App gerade auf Verlauf, Einstellungen oder schon auf der Seite selbst war.
 */
internal fun NavController.openFromLink(target: LinkTarget) {
    val route = when (target) {
        LinkTarget.UNLOCK -> UNLOCK_ROUTE
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}
