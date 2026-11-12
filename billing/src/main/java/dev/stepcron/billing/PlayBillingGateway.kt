package dev.stepcron.billing

import android.app.Activity
import android.content.Context
import dev.stepcron.shared.license.Unlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [BillingGateway] mit der Google Play Billing Library.
 *
 * PLATZHALTER: Verbindet sich noch nicht mit Play (bleibt [BillingAvailability.CONNECTING]). Die echte Umsetzung
 * folgt; Signatur und Verhalten laut [BillingGateway] bleiben.
 *
 * @param scope lebt so lange wie die App (für Verbindung, Abfragen und das Bestätigen von Käufen).
 */
class PlayBillingGateway(
    context: Context,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : BillingGateway {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(BillingState())
    override val state: StateFlow<BillingState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 8)
    override val events: Flow<PurchaseEvent> = _events.asSharedFlow()

    override suspend fun refresh() = Unit

    override fun launchPurchase(activity: Activity, unlock: Unlock): Boolean = false

    override fun close() = Unit
}
