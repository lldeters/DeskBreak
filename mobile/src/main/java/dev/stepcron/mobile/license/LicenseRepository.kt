package dev.stepcron.mobile.license

import dev.stepcron.billing.BillingAvailability
import dev.stepcron.billing.BillingGateway
import dev.stepcron.billing.BillingProblem
import dev.stepcron.billing.BillingState
import dev.stepcron.billing.PlayBillingGateway
import dev.stepcron.mobile.sync.LicenseLink
import dev.stepcron.mobile.sync.WatchGateway
import dev.stepcron.shared.license.LicenseCodec
import dev.stepcron.shared.license.LicenseInfo
import dev.stepcron.shared.license.LicenseRecord
import dev.stepcron.shared.license.LicenseRules
import dev.stepcron.shared.license.Unlock
import dev.stepcron.shared.sync.SyncPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Zeitgrenzen der Lizenz-Abfragen; in Tests kürzer. */
data class LicenseTiming(
    /**
     * Ist die eigene Play-Abfrage älter, fragt das Handy auch im Hintergrund neu nach (wenn ein Snapshot der Uhr
     * ankommt) – so bleibt ein Abo auf der Uhr gültig, auch wenn die Handy-App selten geöffnet wird.
     */
    val staleAfterMillis: Long = LicenseRules.DAY_MILLIS,
    /**
     * Bei einem offenen Kauf (Zahlung ausstehend oder noch nicht bestätigt, [LicenseRecord.ownOpenPurchase]) gilt die
     * eigene Play-Abfrage schon nach dieser Zeit als alt: Play erstattet unbestätigte Käufe nach drei Tagen.
     */
    val openPurchaseStaleAfterMillis: Long = 60 * 60_000L,
    /** Frühestens nach dieser Zeit ein neuer Versuch im Hintergrund, wenn Play gerade nicht antwortet. */
    val backgroundRetryMillis: Long = 60 * 60_000L,
    /**
     * So lange darf eine Play-Abfrage im Hintergrund bzw. bei „Käufe wiederherstellen" dauern – nicht kürzer, als das
     * Gateway selbst höchstens braucht, sonst ginge ein Ergebnis verloren, das gleich gekommen wäre.
     */
    val playTimeoutMillis: Long = PlayBillingGateway.MAX_REFRESH_MILLIS,
    /** So lange darf das Melden an die Uhr im Hintergrund dauern. */
    val sendTimeoutMillis: Long = 30_000L,
)

/** Ergebnis von „Käufe wiederherstellen". */
sealed interface PurchaseCheck {
    /** Play kennt einen gültigen Kauf; [unlock] ist der beste. */
    data class Found(val unlock: Unlock) : PurchaseCheck

    /** Abfrage erfolgreich, aber kein gültiger Kauf für dieses Google-Konto. */
    data object NothingFound : PurchaseCheck

    /** Abfrage fehlgeschlagen; [problem] sagt warum (falls bekannt). */
    data class Failed(val problem: BillingProblem?) : PurchaseCheck
}

/**
 * Lizenz auf dem Handy: der eigene [LicenseRecord] (gespeichert über [LicenseStore]), die Käufe laut Google Play
 * ([BillingGateway]) und der Austausch mit der Uhr. Ablauf (siehe dev.stepcron.shared.license):
 *
 * - Beim ersten Start beginnt die Testphase ([LicenseRules.withTrialStarted]).
 * - Nach jeder erfolgreichen Play-Abfrage ([BillingState.owned] gesetzt, neues [BillingState.checkedAtMillis])
 *   kommt das Ergebnis in den Stand ([LicenseRules.withOwnCheck]) und geht an die Uhr: als DataItem
 *   [SyncPaths.LICENSE], das der Data Layer auch einer gerade getrennten Uhr später zustellt (z. B. nach einem Kauf
 *   außer Reichweite), und als Message an die verbundenen Uhren.
 * - Die Sicht der Uhr kommt mit jedem Snapshot ([onWatchLicense], [LicenseRules.withPeerInfo]).
 *
 * Fehler beim Melden an die Uhr sind nicht schlimm: Das DataItem wird bei jedem Start erneut abgelegt (gleicher Inhalt
 * löst bei der Uhr nichts aus), und die Sicht des Handys geht mit jedem Abgleich wieder mit. Lebt so lange wie der
 * App-Prozess.
 */
class LicenseRepository(
    private val store: LicenseStore,
    val billing: BillingGateway,
    private val watch: WatchGateway,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timing: LicenseTiming = LicenseTiming(),
) : LicenseLink {

    private val _record = MutableStateFlow<LicenseRecord?>(null)

    /** Lizenz-Stand des Handys; `null`, solange er noch nicht gelesen ist. */
    val record: StateFlow<LicenseRecord?> = _record.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()

    /** Schützt Lesen-Ändern-Speichern des Stands und [appliedCheck]. */
    private val mutex = Mutex()

    /** Das zuletzt übernommene Play-Ergebnis (nur in diesem Prozess). */
    private var appliedCheck: AppliedCheck? = null

    private val lock = Any()
    private var refreshJob: Job? = null

    /** Letzter Versuch einer Play-Abfrage im Hintergrund (nur in diesem Prozess), 0 = keiner. */
    @Volatile
    private var lastBackgroundAttemptAtMillis = 0L

    /** Ein Verbraucher meldet nacheinander immer den neuesten Stand an die Uhr (Wünsche dazwischen fallen zusammen). */
    private val sendRequests = Channel<Unit>(Channel.CONFLATED)

    /** Ein Wunsch an [sendRequests] soll auch eine Message an die verbundenen Uhren schicken (nicht nur das DataItem). */
    private val messageRequested = AtomicBoolean(false)

    /** Meldungen an die Uhr nacheinander, jede mit dem dann aktuellen Stand – nie ein älterer nach einem neueren. */
    private val publishMutex = Mutex()

    init {
        scope.launch { load() }
        scope.launch {
            awaitLoaded()
            // Jedes neue Play-Ergebnis – auch nach einem Kauf – übernehmen und der Uhr melden.
            billing.state.collect { state -> if (applyBilling(state)) requestSend() }
        }
        scope.launch {
            for (request in sendRequests) publishLatest(message = messageRequested.getAndSet(false))
        }
    }

    private suspend fun load() {
        val stored = withContext(ioDispatcher) {
            try {
                store.read()
            } catch (e: Exception) {
                // Ein unlesbarer Speicher ist kein Grund abzustürzen: dann eben ein neuer Stand.
                null
            }
        }
        val record = LicenseRules.withTrialStarted(stored ?: LicenseRecord(), clock())
        mutex.withLock {
            if (record != stored) persist(record)
            _record.value = record
        }
        loaded.complete(Unit)
        // Testphase gerade begonnen (bzw. Beginn korrigiert): der Uhr gleich Bescheid geben. Sonst nur das DataItem
        // sicherstellen (fehlte es, etwa nach einem Update oder einem Fehler beim letzten Ablegen, sähe eine Uhr, die
        // gerade nicht verbunden ist, die Freischaltung nie); unverändert löst es bei der Uhr nichts aus.
        requestSend(message = record.trialStartMillis != stored?.trialStartMillis)
    }

    suspend fun awaitLoaded() = loaded.await()

    // --- Google Play ---

    /** App im Vordergrund: Käufe bei Play neu abfragen. */
    fun onForeground() = refreshPlay()

    /** Fragt Angebote und Käufe bei Play neu ab (sofern nicht schon eine Abfrage läuft); das Ergebnis kommt in den Stand. */
    fun refreshPlay() {
        synchronized(lock) {
            if (refreshJob?.isActive == true) return
            refreshJob = scope.launch {
                awaitLoaded()
                billing.refresh()
                if (applyBilling(billing.state.value)) requestSend()
            }
        }
    }

    /** „Käufe wiederherstellen": fragt Play neu ab und sagt, was dabei herauskam. */
    suspend fun checkPurchases(): PurchaseCheck {
        awaitLoaded()
        val before = billing.state.value.checkedAtMillis
        withTimeoutOrNull(timing.playTimeoutMillis) { billing.refresh() }
        val state = billing.state.value
        if (applyBilling(state)) requestSend()
        val owned = state.owned
        val succeeded = owned != null && state.availability == BillingAvailability.READY &&
            (state.checkedAtMillis != before || state.problem == null)
        return when {
            !succeeded || owned == null -> PurchaseCheck.Failed(state.problem ?: problemFor(state.availability))
            else -> Unlock.best(owned)?.let { PurchaseCheck.Found(it) } ?: PurchaseCheck.NothingFound
        }
    }

    /**
     * Für den Hintergrunddienst, wenn ein Snapshot der Uhr ankam: Ist die eigene Play-Abfrage älter als
     * [LicenseTiming.staleAfterMillis], neu abfragen und das Ergebnis gleich der Uhr melden (der Dienst wartet
     * darauf, sonst endet der Prozess womöglich vorher). Liefert `true`, wenn abgefragt wurde.
     */
    suspend fun refreshPlayIfStale(): Boolean {
        awaitLoaded()
        val before = _record.value ?: return false
        val now = clock()
        val age = now - before.ownCheckedAtMillis
        // Ein offener Kauf soll rechtzeitig bestätigt werden (bzw. nach der Zahlung gelten): dann früher nachfragen.
        val staleAfter = if (before.ownOpenPurchase) timing.openPurchaseStaleAfterMillis else timing.staleAfterMillis
        // Eine Abfrage aus der „Zukunft" (Uhrzeit zurückgestellt) gilt als alt.
        val fresh = before.ownCheckedAtMillis > 0L && age in 0 until staleAfter
        val retryTooSoon = lastBackgroundAttemptAtMillis > 0L &&
            now - lastBackgroundAttemptAtMillis in 0 until timing.backgroundRetryMillis
        if (fresh || retryTooSoon) return false
        lastBackgroundAttemptAtMillis = now
        withTimeoutOrNull(timing.playTimeoutMillis) { billing.refresh() }
        applyBilling(billing.state.value)
        val info = _record.value?.let(LicenseRules::infoFor) ?: return true
        if (info != LicenseRules.infoFor(before)) withTimeoutOrNull(timing.sendTimeoutMillis) { publishLatest(message = true) }
        return true
    }

    /**
     * Übernimmt ein erfolgreiches Play-Ergebnis, das noch nicht übernommen wurde (auch, wenn sich nur geändert hat, ob
     * ein Kauf offen ist). Liefert `true`, wenn sich die Meldung an die Uhr dadurch geändert hat (bei jeder neuen
     * Abfrage: deren Zeitpunkt).
     */
    private suspend fun applyBilling(state: BillingState): Boolean {
        val owned = state.owned ?: return false
        awaitLoaded()
        val (old, new) = mutex.withLock {
            val current = _record.value ?: return false
            val check = AppliedCheck(state.checkedAtMillis, state.hasOpenPurchase)
            if (check == appliedCheck) return false
            appliedCheck = check
            val checkedAt = state.checkedAtMillis.takeIf { it > 0L } ?: clock()
            val updated = LicenseRules.withOwnCheck(current, owned, checkedAt, openPurchase = state.hasOpenPurchase)
            if (updated != current) {
                persist(updated)
                _record.value = updated
            }
            current to updated
        }
        return LicenseRules.infoFor(old) != LicenseRules.infoFor(new)
    }

    // --- Uhr ---

    override suspend fun onWatchLicense(info: LicenseInfo) {
        awaitLoaded()
        mutex.withLock {
            val current = _record.value ?: return
            val updated = LicenseRules.withPeerInfo(current, info, clock())
            if (updated != current) {
                persist(updated)
                _record.value = updated
            }
        }
        // Nicht zurückmelden: Die Uhr kennt ihre eigene Sicht (und damit den früheren Testbeginn) schon.
    }

    override suspend fun licenseForWatch(): LicenseInfo? {
        awaitLoaded()
        return _record.value?.let(LicenseRules::infoFor)
    }

    /** Meldet den aktuellen Stand im Hintergrund an die Uhr; mit [message] auch per Message (sonst nur das DataItem). */
    private fun requestSend(message: Boolean = true) {
        if (message) messageRequested.set(true)
        sendRequests.trySend(Unit)
    }

    /**
     * Meldet den dann aktuellen Stand an die Uhr: zuerst als DataItem (bleibt liegen, bis die Uhr wieder verbunden ist),
     * mit [message] zusätzlich per Message an die gerade verbundenen Uhren (kommt sofort an). Fehler sind nicht schlimm
     * (keine Uhr, Play Services fehlen …): Das DataItem wird beim nächsten Start erneut abgelegt, und die Sicht des
     * Handys geht mit dem nächsten Abgleich wieder mit.
     */
    private suspend fun publishLatest(message: Boolean) = publishMutex.withLock {
        val info = _record.value?.let(LicenseRules::infoFor) ?: return@withLock
        val bytes = LicenseCodec.encodeInfo(info)
        attempt { watch.putLicense(bytes) }
        if (message) attempt { watch.sendToWatches(SyncPaths.LICENSE, bytes) }
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Siehe publishLatest: beim nächsten Anlass erneut.
        }
    }

    /** Unter [mutex] aufrufen. */
    private suspend fun persist(record: LicenseRecord) {
        withContext(ioDispatcher) {
            try {
                store.write(record)
            } catch (e: IOException) {
                // Anzeige trotzdem aktualisieren; beim nächsten Ändern wird wieder gespeichert.
            }
        }
    }

    private fun problemFor(availability: BillingAvailability): BillingProblem? = when (availability) {
        BillingAvailability.UNAVAILABLE -> BillingProblem.BILLING_UNAVAILABLE
        BillingAvailability.ERROR -> BillingProblem.SERVICE_UNAVAILABLE
        else -> null
    }

    /** Ein übernommenes Play-Ergebnis: Zeitpunkt der Abfrage und ob dabei ein Kauf offen war. */
    private data class AppliedCheck(val checkedAtMillis: Long, val openPurchase: Boolean)
}
