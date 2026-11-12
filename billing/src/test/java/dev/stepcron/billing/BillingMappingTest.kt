package dev.stepcron.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.ProductDetails.RecurrenceMode
import com.android.billingclient.api.Purchase.PurchaseState
import dev.stepcron.shared.license.LicenseProducts
import dev.stepcron.shared.license.Unlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BillingMappingTest {

    // ---------------------------------------------------------------------------------------------
    // Antwortcodes
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `response codes map to problems`() {
        val expected = mapOf(
            BillingResponseCode.OK to null,
            BillingResponseCode.USER_CANCELED to null,
            BillingResponseCode.NETWORK_ERROR to BillingProblem.NETWORK,
            BillingResponseCode.SERVICE_UNAVAILABLE to BillingProblem.NETWORK,
            BillingResponseCode.SERVICE_DISCONNECTED to BillingProblem.SERVICE_UNAVAILABLE,
            SERVICE_TIMEOUT to BillingProblem.SERVICE_UNAVAILABLE,
            BillingResponseCode.BILLING_UNAVAILABLE to BillingProblem.BILLING_UNAVAILABLE,
            BillingResponseCode.FEATURE_NOT_SUPPORTED to BillingProblem.BILLING_UNAVAILABLE,
            BillingResponseCode.ITEM_UNAVAILABLE to BillingProblem.PRODUCT_UNAVAILABLE,
            BillingResponseCode.ITEM_ALREADY_OWNED to BillingProblem.ALREADY_OWNED,
            BillingResponseCode.DEVELOPER_ERROR to BillingProblem.DEVELOPER_ERROR,
            BillingResponseCode.ERROR to BillingProblem.UNKNOWN,
            BillingResponseCode.ITEM_NOT_OWNED to BillingProblem.UNKNOWN,
            999 to BillingProblem.UNKNOWN,
        )
        expected.forEach { (code, problem) -> assertEquals("Code $code", problem, BillingMapping.problemFor(code)) }
    }

    @Test
    fun `response codes map to availability`() {
        val ready = BillingAvailability.READY
        assertEquals(ready, BillingMapping.availabilityAfter(BillingResponseCode.OK, BillingAvailability.ERROR))
        listOf(BillingResponseCode.BILLING_UNAVAILABLE, BillingResponseCode.FEATURE_NOT_SUPPORTED).forEach {
            assertEquals(BillingAvailability.UNAVAILABLE, BillingMapping.availabilityAfter(it, ready))
        }
        listOf(
            BillingResponseCode.NETWORK_ERROR,
            BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingResponseCode.SERVICE_DISCONNECTED,
            SERVICE_TIMEOUT,
            BillingResponseCode.ERROR,
        ).forEach { assertEquals("Code $it", BillingAvailability.ERROR, BillingMapping.availabilityAfter(it, ready)) }
        // Produkt- oder Kauffehler lassen die Verbindung, wie sie ist.
        listOf(
            BillingResponseCode.ITEM_UNAVAILABLE,
            BillingResponseCode.ITEM_ALREADY_OWNED,
            BillingResponseCode.DEVELOPER_ERROR,
            BillingResponseCode.USER_CANCELED,
        ).forEach { assertEquals("Code $it", ready, BillingMapping.availabilityAfter(it, ready)) }
    }

    // ---------------------------------------------------------------------------------------------
    // Käufe
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `purchased items are owned and unacknowledged ones are acknowledged`() {
        val summary = BillingMapping.summarize(
            listOf(
                purchase(LicenseProducts.LIFETIME, PurchaseState.PURCHASED, acknowledged = true, token = "a"),
                purchase(LicenseProducts.SUBSCRIPTION, PurchaseState.PURCHASED, acknowledged = false, token = "b"),
            ),
        )

        assertEquals(setOf(Unlock.LIFETIME, Unlock.SUBSCRIPTION), summary.owned)
        assertEquals(emptySet<Unlock>(), summary.pending)
        assertEquals(listOf("b"), summary.toAcknowledge)
    }

    @Test
    fun `pending, unspecified and unknown purchases are not owned`() {
        val summary = BillingMapping.summarize(
            listOf(
                purchase(LicenseProducts.LIFETIME, PurchaseState.PENDING, acknowledged = false, token = "a"),
                purchase(LicenseProducts.SUBSCRIPTION, PurchaseState.UNSPECIFIED_STATE, acknowledged = false, token = "b"),
                purchase("something_else", PurchaseState.PURCHASED, acknowledged = false, token = "c"),
            ),
        )

        assertEquals(emptySet<Unlock>(), summary.owned)
        assertEquals(setOf(Unlock.LIFETIME), summary.pending)
        assertEquals(emptyList<String>(), summary.toAcknowledge)
    }

    @Test
    fun `a paid product is not pending at the same time`() {
        val summary = BillingMapping.summarize(
            listOf(
                purchase(LicenseProducts.LIFETIME, PurchaseState.PENDING, acknowledged = false, token = "a"),
                purchase(LicenseProducts.LIFETIME, PurchaseState.PURCHASED, acknowledged = false, token = "b"),
                purchase(LicenseProducts.LIFETIME, PurchaseState.PURCHASED, acknowledged = false, token = "b"),
            ),
        )

        assertEquals(setOf(Unlock.LIFETIME), summary.owned)
        assertEquals(emptySet<Unlock>(), summary.pending)
        assertEquals(listOf("b"), summary.toAcknowledge)
    }

    @Test
    fun `no purchases means nothing owned`() {
        assertEquals(PurchaseSummary(), BillingMapping.summarize(emptyList()))
    }

    @Test
    fun `a completed purchase adds to owned, even before the first full query`() {
        val bought = PurchaseSummary(owned = setOf(Unlock.LIFETIME), toAcknowledge = listOf("t"))
        val before = BillingState(
            availability = BillingAvailability.READY,
            pending = setOf(Unlock.LIFETIME),
            problem = BillingProblem.NETWORK,
        )

        val after = BillingMapping.afterPurchaseUpdate(before, bought, nowMillis = 1_000L)

        assertEquals(setOf(Unlock.LIFETIME), after.owned)
        assertEquals(emptySet<Unlock>(), after.pending)
        assertEquals(1_000L, after.checkedAtMillis)
        assertNull(after.problem)

        val withSubscription = BillingMapping.afterPurchaseUpdate(
            before.copy(owned = setOf(Unlock.SUBSCRIPTION), checkedAtMillis = 500L, pending = emptySet()),
            bought,
            nowMillis = 2_000L,
        )
        assertEquals(setOf(Unlock.SUBSCRIPTION, Unlock.LIFETIME), withSubscription.owned)
        assertEquals(2_000L, withSubscription.checkedAtMillis)
    }

    @Test
    fun `a pending purchase only adds to pending`() {
        val before = BillingState(owned = emptySet(), checkedAtMillis = 500L)

        val after = BillingMapping.afterPurchaseUpdate(before, PurchaseSummary(pending = setOf(Unlock.SUBSCRIPTION)), 1_000L)

        assertEquals(emptySet<Unlock>(), after.owned)
        assertEquals(setOf(Unlock.SUBSCRIPTION), after.pending)
        assertEquals(500L, after.checkedAtMillis)
    }

    @Test
    fun `an empty purchase update changes nothing`() {
        val before = BillingState(problem = BillingProblem.NETWORK)
        assertSame(before, BillingMapping.afterPurchaseUpdate(before, PurchaseSummary(), 1_000L))
    }

    // ---------------------------------------------------------------------------------------------
    // Angebote
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `the subscription uses the monthly base plan and its recurring phase`() {
        val chosen = BillingMapping.chooseSubscription(
            listOf(
                // Ein Angebot (z. B. Rabatt) auf dem Basis-Abo – nicht gemeint.
                SubscriptionOfferInfo(
                    basePlanId = LicenseProducts.MONTHLY_BASE_PLAN,
                    offerId = "intro",
                    offerToken = "offer-token",
                    phases = listOf(phase("0,50 €", "P1M", RecurrenceMode.FINITE_RECURRING), phase("1,00 €", "P1M")),
                ),
                SubscriptionOfferInfo(
                    basePlanId = "yearly",
                    offerId = null,
                    offerToken = "yearly-token",
                    phases = listOf(phase("10,00 €", "P1Y")),
                ),
                SubscriptionOfferInfo(
                    basePlanId = LicenseProducts.MONTHLY_BASE_PLAN,
                    offerId = null,
                    offerToken = "base-token",
                    phases = listOf(phase("1,00 €", "P1M")),
                ),
            ),
        )

        assertEquals(ChosenOffer(BillingOffer(Unlock.SUBSCRIPTION, "1,00 €", "P1M"), "base-token"), chosen)
    }

    @Test
    fun `the price of the subscription comes from the infinitely recurring phase`() {
        val chosen = BillingMapping.chooseSubscription(
            listOf(
                SubscriptionOfferInfo(
                    basePlanId = LicenseProducts.MONTHLY_BASE_PLAN,
                    offerId = null,
                    offerToken = "base-token",
                    phases = listOf(
                        phase("1,00 €", "P1M"),
                        phase("Kostenlos", "P1W", RecurrenceMode.NON_RECURRING),
                    ),
                ),
            ),
        )

        assertEquals(BillingOffer(Unlock.SUBSCRIPTION, "1,00 €", "P1M"), chosen?.offer)
    }

    @Test
    fun `without the monthly base plan there is no subscription offer`() {
        assertNull(BillingMapping.chooseSubscription(emptyList()))
        assertNull(
            BillingMapping.chooseSubscription(
                listOf(SubscriptionOfferInfo("yearly", null, "t", listOf(phase("10,00 €", "P1Y")))),
            ),
        )
        assertNull(
            BillingMapping.chooseSubscription(
                listOf(SubscriptionOfferInfo(LicenseProducts.MONTHLY_BASE_PLAN, null, "t", emptyList())),
            ),
        )
    }

    @Test
    fun `the one-time purchase uses the regular buy option`() {
        val chosen = BillingMapping.chooseOneTime(
            offers = listOf(
                OneTimeOfferInfo("3,00 €", offerId = "sale", offerToken = "sale-token", rental = false),
                OneTimeOfferInfo("1,00 €", offerId = null, offerToken = "rent-token", rental = true),
                OneTimeOfferInfo("5,00 €", offerId = null, offerToken = "buy-token", rental = false),
            ),
            fallback = OneTimeOfferInfo("3,00 €", offerId = "sale", offerToken = "sale-token", rental = false),
        )

        assertEquals(ChosenOffer(BillingOffer(Unlock.LIFETIME, "5,00 €"), "buy-token"), chosen)
    }

    @Test
    fun `without an offer list the default offer of the product is used`() {
        val fallback = OneTimeOfferInfo("5,00 €", offerId = null, offerToken = null, rental = false)

        assertEquals(
            ChosenOffer(BillingOffer(Unlock.LIFETIME, "5,00 €"), null),
            BillingMapping.chooseOneTime(emptyList(), fallback),
        )
        assertNull(BillingMapping.chooseOneTime(emptyList(), null))
        assertNull(BillingMapping.chooseOneTime(emptyList(), fallback.copy(rental = true)))
    }

    private fun purchase(product: String, state: Int, acknowledged: Boolean, token: String) =
        PurchaseSnapshot(products = listOf(product), state = state, acknowledged = acknowledged, token = token)

    private fun phase(price: String, period: String, mode: Int = RecurrenceMode.INFINITE_RECURRING) =
        PricingPhaseInfo(formattedPrice = price, billingPeriod = period, recurrenceMode = mode)

    private companion object {
        /** BillingResponseCode.SERVICE_TIMEOUT (veraltet, kommt von älteren Play-Store-Versionen noch). */
        const val SERVICE_TIMEOUT = -3
    }
}
