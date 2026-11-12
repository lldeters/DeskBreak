package dev.stepcron.mobile.license

import android.app.Activity
import dev.stepcron.billing.BillingAvailability
import dev.stepcron.billing.BillingGateway
import dev.stepcron.billing.BillingProblem
import dev.stepcron.billing.BillingState
import dev.stepcron.billing.PurchaseEvent
import dev.stepcron.shared.license.Unlock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Attrappe von Google Play Billing: [refresh] setzt den Stand auf das, was die nächste Abfrage bei Play ergeben soll
 * ([owns] bzw. [fails]), und zählt die Aufrufe.
 */
class FakeBilling(private val clock: () -> Long = System::currentTimeMillis) : BillingGateway {

    override val state = MutableStateFlow(BillingState())
    override val events = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 8)

    val refreshes = AtomicInteger()

    @Volatile
    private var answer: () -> BillingState = { succeeded(emptySet()) }

    /** Die nächsten Abfragen sind erfolgreich und finden [unlocks]. */
    fun owns(vararg unlocks: Unlock) {
        answer = { succeeded(unlocks.toSet()) }
    }

    /** Die nächsten Abfragen schlagen fehl; Käufe und Zeitpunkt der letzten erfolgreichen bleiben. */
    fun fails(problem: BillingProblem) {
        answer = { state.value.copy(availability = BillingAvailability.ERROR, problem = problem) }
    }

    /** Wie ein Kauf: Play meldet ohne Abfrage einen neuen Stand. */
    fun purchased(vararg unlocks: Unlock) {
        state.value = succeeded(unlocks.toSet())
    }

    override suspend fun refresh() {
        refreshes.incrementAndGet()
        state.value = answer()
    }

    override fun launchPurchase(activity: Activity, unlock: Unlock): Boolean = false

    override fun close() = Unit

    private fun succeeded(owned: Set<Unlock>): BillingState = state.value.copy(
        availability = BillingAvailability.READY,
        owned = owned,
        checkedAtMillis = clock(),
        problem = null,
    )
}
