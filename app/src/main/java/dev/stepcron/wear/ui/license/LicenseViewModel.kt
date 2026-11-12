package dev.stepcron.wear.ui.license

import android.app.Activity
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.stepcron.billing.BillingGateway
import dev.stepcron.billing.PlayLinks
import dev.stepcron.shared.license.LicenseRecord
import dev.stepcron.shared.license.Unlock
import dev.stepcron.shared.sync.DeepLinks
import dev.stepcron.wear.DeskBreakApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Bildschirm „Freischaltung": Stand der Lizenz, Kauf auf der Uhr oder über das Handy, Käufe prüfen. */
class LicenseViewModel(
    licenseRecord: Flow<LicenseRecord>,
    private val billing: BillingGateway,
    private val phone: PhoneOpener,
    private val watch: WatchOpener,
    private val packageName: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val checking = MutableStateFlow(false)
    private val phoneLink = MutableStateFlow(PhoneLinkState.IDLE)
    private val manageLink = MutableStateFlow(PhoneLinkState.IDLE)

    /** Für die verbleibenden Tage der Testphase. */
    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    val uiState: StateFlow<LicenseUiState> = combine(
        licenseRecord,
        billing.state,
        checking,
        combine(phoneLink, manageLink, ::Pair),
        ticker,
    ) { record, billingState, checking, (phoneLink, manageLink), _ ->
        LicenseUiState.create(record, clock(), billingState, checking, phoneLink, manageLink)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LicenseUiState())

    init {
        // Frische Angebote und Käufe, sobald der Bildschirm offen ist.
        checkPurchases()
    }

    /** Käufe und Angebote neu bei Play abfragen; ein gefundener Kauf schaltet über die Lizenz der Uhr frei. */
    fun checkPurchases() {
        if (!checking.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                billing.refresh()
            } finally {
                checking.value = false
            }
        }
    }

    /** Kaufdialog von Play auf der Uhr; geht das gerade nicht, steht der Grund im Billing-Stand. */
    fun buy(activity: Activity, unlock: Unlock) {
        billing.launchPurchase(activity, unlock)
    }

    /**
     * Die Freischalt-Seite der Handy-App ([DeepLinks.UNLOCK]), wenn sie auf einem erreichbaren Handy installiert ist;
     * sonst die Seite der App in Google Play auf dem Handy.
     */
    fun unlockOnPhone() = launchLink(phoneLink) {
        LinkFallbacks.unlockOnPhone(
            openInPhoneApp = { phone.openInPhoneApp(DeepLinks.UNLOCK.toUri()) },
            openStoreOnPhone = { phone.open(RemotePhoneOpener.storePage(packageName)) },
        )
    }

    /**
     * Die Abo-Verwaltung von Google Play (kündigen, Zahlungsmethode) auf dem Handy, ohne Android-Handy auf der Uhr;
     * klappt beides nicht, zeigt der Bildschirm die Adresse für den Browser.
     */
    fun manageSubscription() = launchLink(manageLink) {
        val uri = PlayLinks.manageSubscription(packageName)
        LinkFallbacks.manageSubscription(
            openOnPhone = { phone.open(uri) },
            openOnWatch = { watch.open(uri) },
        )
    }

    private fun launchLink(link: MutableStateFlow<PhoneLinkState>, open: suspend () -> PhoneLinkState) {
        if (link.value == PhoneLinkState.OPENING) return
        link.value = PhoneLinkState.OPENING
        viewModelScope.launch { link.value = open() }
    }

    companion object {
        private const val TICK_MILLIS = 60_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeskBreakApp
                val container = app.container
                LicenseViewModel(
                    licenseRecord = container.licenseRepository.record,
                    billing = container.billing,
                    phone = RemotePhoneOpener(app),
                    watch = ActivityWatchOpener(app),
                    packageName = app.packageName,
                )
            }
        }
    }
}
