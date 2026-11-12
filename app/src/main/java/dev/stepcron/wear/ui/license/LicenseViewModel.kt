package dev.stepcron.wear.ui.license

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.stepcron.billing.BillingGateway
import dev.stepcron.billing.PlayLinks
import dev.stepcron.shared.license.LicenseRecord
import dev.stepcron.shared.license.Unlock
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
    private val packageName: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val checking = MutableStateFlow(false)
    private val phoneLink = MutableStateFlow(PhoneLinkState.IDLE)

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
        phoneLink,
        ticker,
    ) { record, billingState, checking, phoneLink, _ ->
        LicenseUiState.create(record, clock(), billingState, checking, phoneLink)
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

    /** Die Seite der App in Google Play auf dem Handy – dort in der Handy-App freischalten. */
    fun unlockOnPhone() = openOnPhone(RemotePhoneOpener.storePage(packageName))

    /** Die Abo-Verwaltung von Google Play auf dem Handy (kündigen, Zahlungsmethode). */
    fun manageSubscriptionOnPhone() = openOnPhone(PlayLinks.manageSubscription(packageName))

    private fun openOnPhone(uri: Uri) {
        if (phoneLink.value == PhoneLinkState.OPENING) return
        phoneLink.value = PhoneLinkState.OPENING
        viewModelScope.launch {
            phoneLink.value = if (phone.open(uri)) PhoneLinkState.OPENED else PhoneLinkState.FAILED
        }
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
                    packageName = app.packageName,
                )
            }
        }
    }
}
