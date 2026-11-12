package dev.stepcron.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dev.stepcron.shared.license.LicenseProducts
import dev.stepcron.shared.license.Unlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * [BillingGateway] mit der Google Play Billing Library (Version 9).
 *
 * - Verbindet sich erst in [refresh] (und nach einem Verbindungsverlust von selbst neu), fragt dann die Angebote
 *   ([LicenseProducts.LIFETIME] als In-App-Produkt, [LicenseProducts.SUBSCRIPTION] mit dem Basis-Abo
 *   [LicenseProducts.MONTHLY_BASE_PLAN]) und die Käufe beider Produktarten ab.
 * - [BillingState.owned] setzt nur eine erfolgreiche Abfrage beider Produktarten (bzw. ein abgeschlossener Kauf,
 *   der dazukommt); scheitert eine, bleibt der bisherige Stand und [BillingState.problem] nennt den Grund. Die Käufe
 *   kommen gleich nach ihrer Abfrage in den [state], die Angebote danach – so verzögert oder verwirft die Wartezeit
 *   auf die Angebote kein Kauf-Ergebnis, und ein Kauf während der Abfrage wird nie vom älteren Stand überschrieben.
 * - Bezahlte Käufe werden bestätigt (acknowledge); scheitert das, versucht es die nächste Abfrage erneut. Bis dahin
 *   zählen sie schon als gekauft ([BillingState.unacknowledged]) – Play erstattet erst nach drei Tagen ohne
 *   Bestätigung.
 * - Ein [refresh] dauert höchstens [MAX_REFRESH_MILLIS]; jeder Schritt hat seine eigene Zeitgrenze.
 * - Ohne Play (Robolectric, Emulator ohne Play Store, Uhr ohne Play Billing) bleibt alles bei
 *   [BillingAvailability.UNAVAILABLE]; keine Ausnahme verlässt diese Klasse.
 *
 * Die Library ruft ihre Rückrufe auf dem Main-Thread auf; Verbindung und Abfragen laufen deshalb ebenfalls dort
 * (nur asynchrone Aufrufe, nichts blockiert). Der [state] wird nur atomar ([MutableStateFlow.update]) geändert.
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

    /** Verbinden und Abfragen nacheinander, damit eine ältere Antwort nie eine neuere überschreibt. */
    private val refreshMutex = Mutex()

    /** Kaufbare Produkte aus den letzten erfolgreichen Abfragen – für den Kaufdialog. */
    @Volatile
    private var products: Map<Unlock, Purchasable> = emptyMap()

    /** Zuletzt geöffneter Kaufdialog: „bereits gekauft" meldet Play ohne die Käufe selbst. */
    @Volatile
    private var launchedUnlock: Unlock? = null

    @Volatile
    private var closed = false

    /** Selbstständige Neuverbindungen seit der letzten vollständigen Abfrage. */
    private val reconnects = AtomicInteger(0)

    /** Zählt Ergebnisse des Kaufdialogs – eine Abfrage, die schon vorher lief, kennt den neuen Kauf vielleicht nicht. */
    private val purchaseUpdates = AtomicInteger(0)

    /** Käufe, deren Bestätigung gerade läuft (Purchase-Token). */
    private val acknowledging: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Die zuletzt begonnene eigene Einrichtung der Verbindung (startConnection); abgeschlossen bzw. `null` = keine. */
    @Volatile
    private var ownSetup: CompletableDeferred<Int>? = null

    private val clientDelegate = lazy { createClient() }

    /** `null`, wenn sich die Library hier nicht einrichten lässt. */
    private val client: BillingClient? by clientDelegate

    override suspend fun refresh() {
        if (closed) return
        refreshMutex.withLock {
            try {
                withContext(Dispatchers.Main.immediate) { refreshLocked() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Unerwartete Ausnahme der Library oder kein Main-Thread (Tests): nie abstürzen.
                Log.w(TAG, "Abfrage bei Play fehlgeschlagen", e)
                _state.update { it.copy(availability = BillingAvailability.ERROR, problem = BillingProblem.UNKNOWN) }
            }
        }
    }

    override fun launchPurchase(activity: Activity, unlock: Unlock): Boolean {
        val current = _state.value
        val client = if (closed) null else client
        if (client == null || current.availability != BillingAvailability.READY) return false
        val owned = current.owned.orEmpty()
        // Mit dem Einmalkauf ist ein Abo überflüssig – Play würde es trotzdem verkaufen.
        if (unlock in owned || (unlock == Unlock.SUBSCRIPTION && Unlock.LIFETIME in owned)) {
            _state.update { it.copy(problem = BillingProblem.ALREADY_OWNED) }
            return false
        }
        val product = products[unlock]
        if (product == null) {
            _state.update { it.copy(problem = BillingProblem.PRODUCT_UNAVAILABLE) }
            return false
        }
        launchedUnlock = unlock
        val result = try {
            val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product.details)
                .apply { product.chosen.offerToken?.takeIf { it.isNotEmpty() }?.let { setOfferToken(it) } }
                .build()
            client.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build(),
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "Kaufdialog nicht geöffnet", e)
            _state.update { it.copy(problem = BillingProblem.UNKNOWN) }
            return false
        }
        return when (val code = result.responseCode) {
            BillingResponseCode.OK -> {
                _state.update { it.copy(problem = null) }
                true
            }

            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _state.update { it.copy(problem = BillingProblem.ALREADY_OWNED) }
                scope.launch { confirmOwned(unlock) }
                false
            }

            else -> {
                Log.w(TAG, "Kaufdialog abgelehnt: $code ${result.debugMessage}")
                _state.update {
                    it.copy(
                        availability = BillingMapping.availabilityAfter(code, it.availability),
                        problem = BillingMapping.problemFor(code),
                    )
                }
                false
            }
        }
    }

    override fun close() {
        closed = true
        if (!clientDelegate.isInitialized()) return
        try {
            clientDelegate.value?.endConnection()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Verbindung zu Play nicht sauber beendet", e)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Verbindung und Abfragen
    // ---------------------------------------------------------------------------------------------

    private fun createClient(): BillingClient? = try {
        BillingClient.newBuilder(appContext)
            .setListener(::onPurchasesUpdated)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    } catch (e: RuntimeException) {
        Log.w(TAG, "Play Billing lässt sich nicht einrichten", e)
        null
    }

    private suspend fun refreshLocked() {
        val client = client
        if (client == null) {
            _state.update {
                it.copy(availability = BillingAvailability.UNAVAILABLE, problem = BillingProblem.BILLING_UNAVAILABLE)
            }
            return
        }
        val setupCode = connect(client)
        if (setupCode != BillingResponseCode.OK) {
            Log.i(TAG, "Keine Verbindung zu Play: $setupCode")
            _state.update {
                it.copy(
                    availability = BillingMapping.availabilityAfter(setupCode, BillingAvailability.ERROR),
                    problem = BillingMapping.problemFor(setupCode) ?: BillingProblem.UNKNOWN,
                )
            }
            return
        }

        // Ohne Abo-Unterstützung (manche Geräte) kann es hier kein Abo geben; der Einmalkauf zählt trotzdem.
        val subscriptionsSupported = client.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
            .responseCode != BillingResponseCode.FEATURE_NOT_SUPPORTED

        val updatesBefore = purchaseUpdates.get()
        val (purchasesCode, queried) = queryPurchases(client, subscriptionsSupported)
        // Gleich übernehmen, nicht erst nach den Angeboten (die dauern, und ein Abbruch dort verwürfe das Ergebnis).
        // Kam seit Beginn der Abfrage ein Kauf herein, gilt dessen Stand; die Abfrage danach (siehe onPurchased) ist
        // vollständig. Geprüft wird im selben atomaren Schritt, in dem geschrieben wird.
        val now = clock()
        _state.update { current ->
            val summary = queried?.takeIf { purchaseUpdates.get() == updatesBefore }
            BillingMapping.afterPurchaseQuery(current, purchasesCode, summary, now)
        }
        // Vor den Angeboten: Ohne Bestätigung erstattet Play den Kauf nach drei Tagen.
        if (queried != null) acknowledgeAll(client, queried)

        val offersCode = queryOffers(client, subscriptionsSupported)
        val failure = listOf(purchasesCode, offersCode).firstOrNull { it != BillingResponseCode.OK }
            ?: BillingResponseCode.OK
        // Erst eine vollständige Abfrage beendet die Folge selbstständiger Neuverbindungen (kein Endlos-Versuch).
        if (failure == BillingResponseCode.OK) reconnects.set(0) else Log.i(TAG, "Abfrage bei Play unvollständig: $failure")
        _state.update { current ->
            current.copy(
                availability = BillingMapping.availabilityAfter(failure, BillingAvailability.READY),
                offers = products.mapValues { it.value.chosen.offer },
                problem = BillingMapping.problemFor(failure),
            )
        }
    }

    /**
     * Verbindet sich, falls nötig; liefert den Antwortcode der Einrichtung (nach [CONNECT_TIMEOUT_MILLIS]: [TIMED_OUT]).
     *
     * Entscheidend ist [BillingClient.getConnectionState], nicht `isReady`: Mit automatischer Neuverbindung meldet
     * Billing 9 `isReady` immer, und jede Abfrage müsste sich erst selbst verbinden (isFeatureSupported wartete dafür
     * blockierend auf dem Main-Thread). Läuft schon eine Einrichtung – die eigene, deren Aktualisierung abgebrochen
     * wurde, oder eine Neuverbindung der Library –, wird auf sie gewartet statt startConnection erneut aufzurufen (das
     * ergäbe sofort einen falschen DEVELOPER_ERROR, siehe [BillingMapping.connectStep]). Ein Abbruch hier lässt die
     * Einrichtung weiterlaufen; die nächste Aktualisierung wartet dann auf deren Ergebnis.
     */
    private suspend fun connect(client: BillingClient): Int =
        withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) { awaitConnection(client) } ?: TIMED_OUT

    private suspend fun awaitConnection(client: BillingClient): Int {
        while (true) {
            val running = ownSetup?.takeIf { it.isActive }
            val setup = when (BillingMapping.connectStep(client.connectionState, ownSetupRunning = running != null)) {
                ConnectStep.CONNECTED -> return BillingResponseCode.OK
                ConnectStep.AWAIT_OWN_SETUP -> running!!
                ConnectStep.START -> startSetup(client)
                ConnectStep.WAIT_FOR_LIBRARY -> {
                    delay(SETUP_POLL_MILLIS)
                    continue
                }
            }
            // In kurzen Abschnitten warten und dazwischen neu entscheiden: Gibt die Library auf, ohne zu antworten,
            // bleibt so keine Aktualisierung an einer Einrichtung hängen, die nie fertig wird.
            val code = withTimeoutOrNull(SETUP_POLL_MILLIS) { setup.await() } ?: continue
            // „Verbindet schon": Die Library kam mit ihrer eigenen Neuverbindung zuvor – auf die warten.
            if (code == BillingResponseCode.DEVELOPER_ERROR &&
                client.connectionState == BillingClient.ConnectionState.CONNECTING
            ) {
                continue
            }
            return code
        }
    }

    /** Beginnt die Einrichtung; das Ergebnis kommt über den gelieferten (und in [ownSetup] gemerkten) Wert. */
    private fun startSetup(client: BillingClient): CompletableDeferred<Int> {
        val result = CompletableDeferred<Int>()
        ownSetup = result
        try {
            client.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        result.complete(billingResult.responseCode)
                    }

                    override fun onBillingServiceDisconnected() {
                        // Vor dem Abschluss gescheitert – sonst später verloren: dann neu verbinden.
                        if (!result.complete(BillingResponseCode.SERVICE_DISCONNECTED)) onDisconnected()
                    }
                },
            )
        } catch (e: RuntimeException) {
            // Nicht begonnen: Keine spätere Aktualisierung darf auf diese Einrichtung warten.
            result.cancel()
            throw e
        }
        return result
    }

    private fun onDisconnected() {
        _state.update {
            if (it.availability == BillingAvailability.READY) it.copy(availability = BillingAvailability.CONNECTING) else it
        }
        val attempt = reconnects.incrementAndGet()
        if (closed || attempt > MAX_RECONNECTS) return
        scope.launch {
            delay(RECONNECT_DELAY_MILLIS * attempt)
            refresh()
        }
    }

    /**
     * Käufe beider Produktarten. Die Zusammenfassung nur, wenn beide Abfragen gelangen; sonst `null` und der Code
     * der fehlgeschlagenen.
     */
    private suspend fun queryPurchases(client: BillingClient, subscriptionsSupported: Boolean): Pair<Int, PurchaseSummary?> {
        val purchases = mutableListOf<Purchase>()
        val types = buildList {
            add(BillingClient.ProductType.INAPP)
            if (subscriptionsSupported) add(BillingClient.ProductType.SUBS)
        }
        for (type in types) {
            val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
            val result = withTimeoutOrNull(QUERY_TIMEOUT_MILLIS) { client.queryPurchasesAsync(params) }
                ?: return TIMED_OUT to null
            val code = result.billingResult.responseCode
            if (code != BillingResponseCode.OK) return code to null
            purchases += result.purchasesList
        }
        return BillingResponseCode.OK to BillingMapping.summarize(purchases.map { it.toSnapshot() })
    }

    /**
     * Angebote beider Produkte; ersetzt [products] je Produkt nur nach einer erfolgreichen Abfrage. Liefert den Code
     * der ersten fehlgeschlagenen Abfrage bzw. OK.
     */
    private suspend fun queryOffers(client: BillingClient, subscriptionsSupported: Boolean): Int {
        val updated = products.toMutableMap()
        var failure = BillingResponseCode.OK
        val queries = buildList {
            add(Triple(Unlock.LIFETIME, BillingClient.ProductType.INAPP, ::lifetimeOf))
            if (subscriptionsSupported) add(Triple(Unlock.SUBSCRIPTION, BillingClient.ProductType.SUBS, ::subscriptionOf))
        }
        if (!subscriptionsSupported) updated.remove(Unlock.SUBSCRIPTION)
        for ((unlock, type, purchasable) in queries) {
            val productId = LicenseProducts.productId(unlock)
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(productId).setProductType(type).build()),
                )
                .build()
            val result = withTimeoutOrNull(QUERY_TIMEOUT_MILLIS) { client.queryProductDetails(params) }
            val code = result?.billingResult?.responseCode ?: TIMED_OUT
            if (code != BillingResponseCode.OK) {
                if (failure == BillingResponseCode.OK) failure = code
                continue
            }
            // Fehlt das Produkt in der Antwort, ist es in der Play Console (noch) nicht aktiv.
            val details = result?.productDetailsList.orEmpty().firstOrNull { it.productId == productId }
            val offer = details?.let(purchasable)
            if (offer != null) updated[unlock] = offer else updated.remove(unlock)
        }
        products = updated
        return failure
    }

    private fun lifetimeOf(details: ProductDetails): Purchasable? {
        val chosen = BillingMapping.chooseOneTime(
            offers = details.oneTimePurchaseOfferDetailsList.orEmpty().map { it.toInfo() },
            fallback = details.oneTimePurchaseOfferDetails?.toInfo(),
        ) ?: return null
        return Purchasable(details, chosen)
    }

    private fun subscriptionOf(details: ProductDetails): Purchasable? {
        val offers = details.subscriptionOfferDetails.orEmpty().map { offer ->
            SubscriptionOfferInfo(
                basePlanId = offer.basePlanId,
                offerId = offer.offerId,
                offerToken = offer.offerToken,
                phases = offer.pricingPhases.pricingPhaseList.map { phase ->
                    PricingPhaseInfo(phase.formattedPrice, phase.billingPeriod, phase.recurrenceMode)
                },
            )
        }
        return BillingMapping.chooseSubscription(offers)?.let { Purchasable(details, it) }
    }

    // ---------------------------------------------------------------------------------------------
    // Kaufablauf
    // ---------------------------------------------------------------------------------------------

    /** Ergebnis des Kaufdialogs (Main-Thread). */
    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (val code = result.responseCode) {
            BillingResponseCode.OK -> onPurchased(purchases.orEmpty())
            BillingResponseCode.USER_CANCELED -> {
                _state.update { it.copy(problem = null) }
                _events.tryEmit(PurchaseEvent.Cancelled)
            }

            BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { confirmOwned(launchedUnlock) }
            else -> {
                Log.w(TAG, "Kauf fehlgeschlagen: $code ${result.debugMessage}")
                val problem = BillingMapping.problemFor(code) ?: BillingProblem.UNKNOWN
                _state.update {
                    it.copy(availability = BillingMapping.availabilityAfter(code, it.availability), problem = problem)
                }
                _events.tryEmit(PurchaseEvent.Failed(problem))
            }
        }
    }

    private fun onPurchased(purchases: List<Purchase>) {
        val summary = BillingMapping.summarize(purchases.map { it.toSnapshot() })
        purchaseUpdates.incrementAndGet()
        _state.update { BillingMapping.afterPurchaseUpdate(it, summary, clock()) }
        summary.owned.forEach { _events.tryEmit(PurchaseEvent.Purchased(it)) }
        summary.pending.forEach { _events.tryEmit(PurchaseEvent.Pending(it)) }
        scope.launch {
            client?.let { withContext(Dispatchers.Main.immediate) { acknowledgeAll(it, summary) } }
            // Das Gesamtbild (z. B. ein inzwischen abgelaufenes Abo) neu abfragen.
            refresh()
        }
    }

    /** Play meldet „bereits gekauft": Käufe neu einlesen und den Kauf melden, falls er jetzt zu sehen ist. */
    private suspend fun confirmOwned(attempted: Unlock?) {
        refresh()
        val owned = _state.value.owned.orEmpty()
        val unlock = attempted?.takeIf { it in owned } ?: Unlock.best(owned)
        _events.emit(if (unlock != null) PurchaseEvent.Purchased(unlock) else PurchaseEvent.Failed(BillingProblem.ALREADY_OWNED))
    }

    /**
     * Bestätigt die bezahlten Käufe aus [summary]; ein Fehlschlag wird bei der nächsten Abfrage wiederholt. Erst wenn
     * alle bestätigt sind, gelten sie in [state] nicht mehr als unbestätigt (eine Bestätigung, die gerade anderswo
     * läuft, zählt hier nicht – die meldet ihr eigener Aufruf).
     */
    private suspend fun acknowledgeAll(client: BillingClient, summary: PurchaseSummary) {
        if (summary.toAcknowledge.isEmpty()) return
        var allConfirmed = true
        for (token in summary.toAcknowledge) {
            if (!acknowledging.add(token)) {
                allConfirmed = false
                continue
            }
            try {
                val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
                val result = withTimeoutOrNull(QUERY_TIMEOUT_MILLIS) { client.acknowledgePurchase(params) }
                if (result?.responseCode != BillingResponseCode.OK) {
                    allConfirmed = false
                    Log.w(TAG, "Kauf nicht bestätigt (${result?.responseCode}); nächster Versuch bei der nächsten Abfrage")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                allConfirmed = false
                Log.w(TAG, "Kauf nicht bestätigt", e)
            } finally {
                acknowledging.remove(token)
            }
        }
        if (allConfirmed) _state.update { BillingMapping.afterAcknowledged(it, summary.unacknowledged) }
    }

    /** Ein kaufbares Produkt: die Details von Play (für den Kaufdialog) und das gewählte Angebot. */
    private class Purchasable(val details: ProductDetails, val chosen: ChosenOffer)

    companion object {
        private const val TAG = "PlayBillingGateway"
        private const val CONNECT_TIMEOUT_MILLIS = 20_000L
        private const val QUERY_TIMEOUT_MILLIS = 30_000L
        private const val RECONNECT_DELAY_MILLIS = 2_000L
        private const val MAX_RECONNECTS = 3

        /** So oft wird nachgesehen, ob eine Neuverbindung der Library inzwischen entschieden ist. */
        private const val SETUP_POLL_MILLIS = 250L

        /**
         * Obergrenze für ein [refresh], sobald es an der Reihe ist: Verbinden, je zwei Kauf- und Angebots-Abfragen und
         * eine Bestätigung mit ihren eigenen Zeitgrenzen, dazu Reserve. Wer auf [refresh] wartet, sollte mindestens so
         * lange warten – sonst verwirft er womöglich ein Ergebnis, das gleich gekommen wäre.
         */
        const val MAX_REFRESH_MILLIS = CONNECT_TIMEOUT_MILLIS + 5 * QUERY_TIMEOUT_MILLIS + 10_000L

        /** Antwortet Play nicht rechtzeitig, gilt das wie ein Netzwerkfehler (vorübergehend, neu versuchen). */
        private const val TIMED_OUT = BillingResponseCode.NETWORK_ERROR

        private fun Purchase.toSnapshot() = PurchaseSnapshot(
            products = products,
            state = purchaseState,
            acknowledged = isAcknowledged,
            token = purchaseToken,
        )

        private fun ProductDetails.OneTimePurchaseOfferDetails.toInfo() = OneTimeOfferInfo(
            formattedPrice = formattedPrice,
            offerId = offerId,
            offerToken = offerToken,
            rental = rentalDetails != null,
        )
    }
}
