package dev.stepcron.billing

import android.app.Activity
import android.net.Uri
import dev.stepcron.shared.license.LicenseProducts
import dev.stepcron.shared.license.Unlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Zugang zu Google Play Billing für die Freischaltung – in den Apps [PlayBillingGateway], in Tests und
 * Screenshots eine Attrappe. Alle Aufrufe sind vom Main-Thread aus erlaubt.
 *
 * Ablauf: [refresh] verbindet sich und fragt Angebote und Käufe ab ([state]). [launchPurchase] öffnet den
 * Kaufdialog von Play; das Ergebnis kommt als [PurchaseEvent] über [events] und aktualisiert [state]. Gekaufte
 * Produkte bestätigt (acknowledge) die Umsetzung selbst – sonst erstattet Play sie nach drei Tagen. Solange das
 * aussteht oder eine Zahlung offen ist ([BillingState.hasOpenPurchase]), sollten die Apps [refresh] bald wiederholen.
 */
interface BillingGateway {

    val state: StateFlow<BillingState>

    /** Einmalige Ereignisse des Kaufablaufs, z. B. für einen Hinweis in der Oberfläche. */
    val events: Flow<PurchaseEvent>

    /** Verbindet sich bei Bedarf und fragt Angebote und Käufe neu ab. Mehrfach aufrufbar, wirft nicht. */
    suspend fun refresh()

    /**
     * Öffnet den Kaufdialog von Play für [unlock]. `false`, wenn das gerade nicht geht (keine Verbindung, Angebot
     * unbekannt) – dann steht der Grund in [state].
     */
    fun launchPurchase(activity: Activity, unlock: Unlock): Boolean

    /** Beendet die Verbindung zu Play. */
    fun close()
}

/** Stand der Verbindung zu Play. */
enum class BillingAvailability {
    /** Noch nicht verbunden bzw. Verbindung läuft. */
    CONNECTING,

    READY,

    /** Play Billing gibt es hier nicht (kein Play Store, Gerät/Konto nicht unterstützt). */
    UNAVAILABLE,

    /** Vorübergehender Fehler (z. B. offline, Play-Dienst nicht erreichbar); [BillingGateway.refresh] versucht es neu. */
    ERROR,
}

/** Ein kaufbares Angebot, wie Play es liefert. */
data class BillingOffer(
    val unlock: Unlock,
    /** Lokalisierter Preis von Play, z. B. „5,00 €" – nie selbst formatieren, Play kennt Währung und Land. */
    val formattedPrice: String,
    /** Abrechnungszeitraum im ISO-8601-Format (z. B. „P1M") beim Abo, sonst `null`. */
    val billingPeriod: String? = null,
)

data class BillingState(
    val availability: BillingAvailability = BillingAvailability.CONNECTING,
    /** Angebote, die Play kennt; fehlt eines, ist es in der Play Console (noch) nicht aktiv. */
    val offers: Map<Unlock, BillingOffer> = emptyMap(),
    /** Gültige, bezahlte Käufe der letzten erfolgreichen Abfrage; `null` = noch keine erfolgreiche Abfrage. */
    val owned: Set<Unlock>? = null,
    /** Begonnene, noch nicht bezahlte Käufe (z. B. Barzahlung im Laden), die Play noch abschließen muss. */
    val pending: Set<Unlock> = emptySet(),
    /**
     * Bezahlte Käufe, die noch nicht bei Play bestätigt (acknowledge) sind – die Bestätigung läuft oder schlug fehl und
     * wird beim nächsten [BillingGateway.refresh] wiederholt. Sie zählen schon zu [owned].
     */
    val unacknowledged: Set<Unlock> = emptySet(),
    /** Zeitpunkt der letzten erfolgreichen Kauf-Abfrage (Epoch-Millis), 0 = noch keine. */
    val checkedAtMillis: Long = 0L,
    /** Grund, falls die letzte Aktion fehlschlug. */
    val problem: BillingProblem? = null,
) {
    /** Ein Kauf ist noch offen (Zahlung ausstehend oder unbestätigt): bald erneut bei Play nachfragen. */
    val hasOpenPurchase: Boolean get() = pending.isNotEmpty() || unacknowledged.isNotEmpty()
}

/** Gründe, aus denen Play etwas nicht konnte – für eine verständliche Meldung. */
enum class BillingProblem {
    /** Keine Internetverbindung bzw. Zeitüberschreitung. */
    NETWORK,

    /** Play-Dienst gerade nicht erreichbar. */
    SERVICE_UNAVAILABLE,

    /** Billing hier nicht verfügbar (Konto, Land oder Gerät). */
    BILLING_UNAVAILABLE,

    /** Produkt in Play nicht gefunden (Play Console prüfen). */
    PRODUCT_UNAVAILABLE,

    /** Bereits gekauft – nur die Käufe neu einlesen. */
    ALREADY_OWNED,

    /** Fehler in der Einbindung (z. B. App nicht aus Play installiert, falsche Produkt-ID). */
    DEVELOPER_ERROR,

    UNKNOWN,
}

/** Ergebnis eines Kaufablaufs. */
sealed interface PurchaseEvent {
    /** Bezahlt und bestätigt – die Freischaltung gilt. */
    data class Purchased(val unlock: Unlock) : PurchaseEvent

    /** Kauf begonnen, Zahlung steht noch aus; Play meldet den Abschluss später. */
    data class Pending(val unlock: Unlock) : PurchaseEvent

    /** Abgebrochen von der Nutzerin bzw. dem Nutzer. */
    data object Cancelled : PurchaseEvent

    data class Failed(val problem: BillingProblem) : PurchaseEvent
}

/** Links in den Play Store. */
object PlayLinks {
    /** Abo-Verwaltung in Play (kündigen, Zahlungsmethode ändern). */
    fun manageSubscription(packageName: String): Uri =
        Uri.parse("https://play.google.com/store/account/subscriptions?sku=${LicenseProducts.SUBSCRIPTION}&package=$packageName")

    /** Seite der App im Play Store. */
    fun storePage(packageName: String): Uri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
}
