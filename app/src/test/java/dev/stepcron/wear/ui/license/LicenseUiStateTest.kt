package dev.stepcron.wear.ui.license

import dev.stepcron.billing.BillingAvailability
import dev.stepcron.billing.BillingOffer
import dev.stepcron.billing.BillingProblem
import dev.stepcron.billing.BillingState
import dev.stepcron.shared.license.LicenseRecord
import dev.stepcron.shared.license.LicenseRules
import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.shared.license.Unlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseUiStateTest {

    private val now = 10 * LicenseRules.DAY_MILLIS
    private val trial = LicenseRecord(trialStartMillis = now - LicenseRules.DAY_MILLIS)
    private val expired = LicenseRecord(trialStartMillis = now - 4 * LicenseRules.DAY_MILLIS)
    private val lifetime = BillingOffer(Unlock.LIFETIME, "5,00 €")
    private val monthly = BillingOffer(Unlock.SUBSCRIPTION, "1,00 €", "P1M")
    private val ready = BillingState(
        availability = BillingAvailability.READY,
        // Absichtlich in anderer Reihenfolge: Der Einmalkauf steht immer oben.
        offers = mapOf(Unlock.SUBSCRIPTION to monthly, Unlock.LIFETIME to lifetime),
        owned = emptySet(),
    )

    private fun create(
        record: LicenseRecord,
        billing: BillingState = ready,
        checking: Boolean = false,
        phoneLink: PhoneLinkState = PhoneLinkState.IDLE,
    ) = LicenseUiState.create(record, now, billing, checking, phoneLink)

    @Test
    fun `during the trial both offers can be bought on the watch, one-time first`() {
        val state = create(trial)

        assertEquals(LicenseStatus.Trial(trial.trialStartMillis + LicenseRules.TRIAL_MILLIS, 2), state.status)
        assertEquals(listOf(lifetime, monthly), state.offers)
        assertNull(state.notice)
        assertNull(state.unlock)
    }

    @Test
    fun `without play billing on the watch only the phone remains, without an error`() {
        val unavailable = BillingState(
            availability = BillingAvailability.UNAVAILABLE,
            problem = BillingProblem.BILLING_UNAVAILABLE,
        )

        val state = create(expired, unavailable)

        assertTrue(state.status is LicenseStatus.Expired)
        assertEquals(emptyList<BillingOffer>(), state.offers)
        assertNull(state.notice)
        // Ohne Verbindung (noch) keine Angebote, auch wenn alte bekannt sind.
        assertEquals(emptyList<BillingOffer>(), create(expired, ready.copy(availability = BillingAvailability.ERROR)).offers)
    }

    @Test
    fun `when unlocked there is nothing to buy and no notice`() {
        val record = expired.copy(ownUnlock = Unlock.LIFETIME.name, ownCheckedAtMillis = now)

        val state = create(record, ready.copy(pending = setOf(Unlock.SUBSCRIPTION), problem = BillingProblem.NETWORK))

        assertEquals(LicenseStatus.Unlocked(Unlock.LIFETIME), state.status)
        assertEquals(Unlock.LIFETIME, state.unlock)
        assertEquals(emptyList<BillingOffer>(), state.offers)
        assertNull(state.notice)
    }

    @Test
    fun `notices for pending purchases and problems`() {
        assertEquals(LicenseNotice.PENDING, create(expired, ready.copy(pending = setOf(Unlock.LIFETIME))).notice)
        assertEquals(LicenseNotice.UNREACHABLE, create(expired, ready.copy(problem = BillingProblem.NETWORK)).notice)
        assertEquals(LicenseNotice.UNREACHABLE, create(expired, ready.copy(problem = BillingProblem.SERVICE_UNAVAILABLE)).notice)
        assertEquals(LicenseNotice.FAILED, create(expired, ready.copy(problem = BillingProblem.PRODUCT_UNAVAILABLE)).notice)
        assertEquals(LicenseNotice.FAILED, create(expired, ready.copy(problem = BillingProblem.DEVELOPER_ERROR)).notice)
        assertEquals(LicenseNotice.FAILED, create(expired, ready.copy(problem = BillingProblem.UNKNOWN)).notice)
        // „Bereits gekauft" klärt das Gateway selbst (es liest die Käufe neu ein).
        assertNull(create(expired, ready.copy(problem = BillingProblem.ALREADY_OWNED)).notice)
    }

    @Test
    fun `checking and the phone link are passed through`() {
        val state = create(trial, checking = true, phoneLink = PhoneLinkState.OPENED_IN_APP)

        assertTrue(state.checking)
        assertEquals(PhoneLinkState.OPENED_IN_APP, state.phoneLink)
        assertEquals(PhoneLinkState.IDLE, state.manageLink)
        assertEquals(false, state.loading)
    }

    @Test
    fun `unlocking on the phone and managing the subscription keep separate states`() {
        val record = expired.copy(ownUnlock = Unlock.SUBSCRIPTION.name, ownCheckedAtMillis = now)

        val state = LicenseUiState.create(record, now, ready, false, PhoneLinkState.OPENED_IN_APP, PhoneLinkState.FAILED)

        assertEquals(Unlock.SUBSCRIPTION, state.unlock)
        assertEquals(PhoneLinkState.OPENED_IN_APP, state.phoneLink)
        assertEquals(PhoneLinkState.FAILED, state.manageLink)
    }
}
