package dev.stepcron.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import dev.stepcron.shared.license.LicenseProducts
import dev.stepcron.shared.license.Unlock

// Reine Abbildungen der Antworten von Play auf BillingState – ohne Android, daher als JVM-Test prüfbar.
// PlayBillingGateway übersetzt die Klassen der Billing Library (Purchase, ProductDetails) in die kleinen
// Datentypen hier; die Library-Klassen selbst lassen sich ohne Android-Laufzeit nicht bauen.

/** Ein Kauf, wie ihn Play meldet, auf das Nötige reduziert. */
internal data class PurchaseSnapshot(
    /** Produkt-IDs des Kaufs (bei uns immer genau eine). */
    val products: List<String>,
    /** [Purchase.PurchaseState]: PURCHASED, PENDING oder UNSPECIFIED_STATE. */
    val state: Int,
    val acknowledged: Boolean,
    val token: String,
)

/** Zusammenfassung einer Kauf-Abfrage bzw. -Aktualisierung. */
internal data class PurchaseSummary(
    /** Bezahlt (auch wenn noch nicht bestätigt – das holt das Gateway nach). */
    val owned: Set<Unlock> = emptySet(),
    /** Begonnen, Zahlung steht aus. */
    val pending: Set<Unlock> = emptySet(),
    /** Tokens der bezahlten, aber noch nicht bestätigten Käufe. */
    val toAcknowledge: List<String> = emptyList(),
)

/** Eine Preisphase eines Abo-Angebots. */
internal data class PricingPhaseInfo(
    val formattedPrice: String,
    /** ISO 8601, z. B. „P1M". */
    val billingPeriod: String,
    /** [ProductDetails.RecurrenceMode]. */
    val recurrenceMode: Int,
)

/** Ein Abo-Angebot (Basis-Abo oder ein Angebot darauf) mit seinen Preisphasen. */
internal data class SubscriptionOfferInfo(
    val basePlanId: String,
    /** `null` beim Basis-Abo selbst, sonst die ID eines Angebots (z. B. Rabatt, Probezeit). */
    val offerId: String?,
    val offerToken: String,
    val phases: List<PricingPhaseInfo>,
)

/** Eine Kaufoption bzw. ein Angebot eines Einmalkaufs. */
internal data class OneTimeOfferInfo(
    val formattedPrice: String,
    /** `null` beim regulären Preis, sonst die ID eines Angebots (z. B. Rabatt). */
    val offerId: String?,
    val offerToken: String?,
    /** Leihe statt Kauf – für eine dauerhafte Freischaltung ungeeignet. */
    val rental: Boolean,
)

/** Ein gewähltes, kaufbares Angebot: was angezeigt wird und das Token für den Kaufdialog. */
internal data class ChosenOffer(
    val offer: BillingOffer,
    /** Token für den Kaufdialog; `null` = das Standard-Angebot des Produkts. */
    val offerToken: String?,
)

internal object BillingMapping {

    /**
     * Grund zu [responseCode]; `null` bei Erfolg und beim Abbruch durch die Nutzerin bzw. den Nutzer. Das veraltete
     * SERVICE_TIMEOUT melden ältere Play-Store-Versionen noch.
     */
    @Suppress("DEPRECATION")
    fun problemFor(responseCode: Int): BillingProblem? = when (responseCode) {
        BillingResponseCode.OK, BillingResponseCode.USER_CANCELED -> null
        // Laut Play meist ein Netzwerkproblem irgendwo zwischen Gerät und Play.
        BillingResponseCode.NETWORK_ERROR, BillingResponseCode.SERVICE_UNAVAILABLE -> BillingProblem.NETWORK
        BillingResponseCode.SERVICE_DISCONNECTED, BillingResponseCode.SERVICE_TIMEOUT -> BillingProblem.SERVICE_UNAVAILABLE
        BillingResponseCode.BILLING_UNAVAILABLE, BillingResponseCode.FEATURE_NOT_SUPPORTED -> BillingProblem.BILLING_UNAVAILABLE
        BillingResponseCode.ITEM_UNAVAILABLE -> BillingProblem.PRODUCT_UNAVAILABLE
        BillingResponseCode.ITEM_ALREADY_OWNED -> BillingProblem.ALREADY_OWNED
        BillingResponseCode.DEVELOPER_ERROR -> BillingProblem.DEVELOPER_ERROR
        else -> BillingProblem.UNKNOWN
    }

    /**
     * Stand der Verbindung nach der Antwort [responseCode]; [current], wenn die Antwort die Verbindung nicht betrifft
     * (z. B. ein unbekanntes Produkt).
     */
    @Suppress("DEPRECATION")
    fun availabilityAfter(responseCode: Int, current: BillingAvailability): BillingAvailability = when (responseCode) {
        BillingResponseCode.OK -> BillingAvailability.READY
        BillingResponseCode.BILLING_UNAVAILABLE, BillingResponseCode.FEATURE_NOT_SUPPORTED -> BillingAvailability.UNAVAILABLE
        BillingResponseCode.NETWORK_ERROR,
        BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingResponseCode.SERVICE_DISCONNECTED,
        BillingResponseCode.SERVICE_TIMEOUT,
        BillingResponseCode.ERROR,
        -> BillingAvailability.ERROR

        else -> current
    }

    /**
     * Ordnet Käufe den Freischaltungen zu: PURCHASED zählt (auch unbestätigt), PENDING ist offen, alles andere und
     * fremde Produkt-IDs werden übergangen. Ist ein Produkt bezahlt, zählt ein weiterer offener Kauf davon nicht.
     */
    fun summarize(purchases: List<PurchaseSnapshot>): PurchaseSummary {
        val owned = mutableSetOf<Unlock>()
        val pending = mutableSetOf<Unlock>()
        val toAcknowledge = mutableListOf<String>()
        for (purchase in purchases) {
            val unlocks = purchase.products.mapNotNull(LicenseProducts::unlockFor)
            if (unlocks.isEmpty()) continue
            when (purchase.state) {
                Purchase.PurchaseState.PURCHASED -> {
                    owned += unlocks
                    if (!purchase.acknowledged && purchase.token.isNotEmpty()) toAcknowledge += purchase.token
                }

                Purchase.PurchaseState.PENDING -> pending += unlocks
            }
        }
        return PurchaseSummary(owned = owned, pending = pending - owned, toAcknowledge = toAcknowledge.distinct())
    }

    /**
     * [state] nach einem abgeschlossenen bzw. begonnenen Kauf ([summary] aus dem Kaufdialog): Bezahltes kommt zu
     * [BillingState.owned] hinzu (auch vor der ersten vollständigen Abfrage – ein Kauf ist eine Bestätigung von Play)
     * und gilt als jetzt bestätigt; Offenes kommt zu [BillingState.pending]. Ohne beides bleibt [state] unverändert.
     */
    fun afterPurchaseUpdate(state: BillingState, summary: PurchaseSummary, nowMillis: Long): BillingState {
        if (summary.owned.isEmpty() && summary.pending.isEmpty()) return state
        val bought = summary.owned.isNotEmpty()
        return state.copy(
            owned = if (bought) state.owned.orEmpty() + summary.owned else state.owned,
            pending = state.pending - summary.owned + summary.pending,
            checkedAtMillis = if (bought) nowMillis else state.checkedAtMillis,
            problem = null,
        )
    }

    /**
     * Das Monatsabo: das Basis-Abo [LicenseProducts.MONTHLY_BASE_PLAN] ohne Angebot (keine Rabatte oder
     * Probezeiten von Play – die Testphase verwaltet die App selbst). Preis und Zeitraum stammen aus der
     * wiederkehrenden Preisphase. `null`, wenn es das Basis-Abo (noch) nicht gibt.
     */
    fun chooseSubscription(offers: List<SubscriptionOfferInfo>): ChosenOffer? {
        val plan = offers.firstOrNull { it.basePlanId == LicenseProducts.MONTHLY_BASE_PLAN && it.offerId == null }
            ?: return null
        val phase = plan.phases.lastOrNull { it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING }
            ?: plan.phases.lastOrNull()
            ?: return null
        return ChosenOffer(
            offer = BillingOffer(Unlock.SUBSCRIPTION, phase.formattedPrice, phase.billingPeriod),
            offerToken = plan.offerToken,
        )
    }

    /**
     * Der Einmalkauf: die Kaufoption zum regulären Preis (kein Angebot, keine Leihe), sonst [fallback] – das
     * Standard-Angebot, das Play für das Produkt nennt. `null`, wenn es keines gibt.
     */
    fun chooseOneTime(offers: List<OneTimeOfferInfo>, fallback: OneTimeOfferInfo?): ChosenOffer? {
        val chosen = offers.firstOrNull { it.offerId == null && !it.rental }
            ?: fallback?.takeUnless { it.rental }
            ?: return null
        return ChosenOffer(offer = BillingOffer(Unlock.LIFETIME, chosen.formattedPrice), offerToken = chosen.offerToken)
    }
}
