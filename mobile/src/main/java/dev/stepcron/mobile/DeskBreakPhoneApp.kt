package dev.stepcron.mobile

import android.app.Application
import android.util.Log
import dev.stepcron.billing.BillingGateway
import dev.stepcron.billing.PlayBillingGateway
import dev.stepcron.mobile.license.LicenseRepository
import dev.stepcron.mobile.license.LicenseStore
import dev.stepcron.mobile.sync.DataLayerGateway
import dev.stepcron.mobile.sync.SnapshotCache
import dev.stepcron.mobile.sync.SyncRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hält die prozessweiten Objekte: Repository, Lizenz, Play-Billing- und Data-Layer-Zugriff leben so lange wie der
 * Prozess, damit Activity, ViewModel und der Hintergrunddienst denselben Stand teilen.
 */
class DeskBreakPhoneApp : Application() {

    /** Für Arbeit, die eine Activity überdauern soll (Senden, Wiederherstellen). Fehler werden nur geloggt. */
    val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Unerwarteter Fehler im Abgleich", error)
        },
    )

    val gateway: DataLayerGateway by lazy { DataLayerGateway(this, appScope) }

    /** Google Play Billing für die Freischaltung (Einmalkauf oder Monatsabo). */
    val billing: BillingGateway by lazy { PlayBillingGateway(this, appScope) }

    /** Lizenz des Handys; beim ersten Zugriff beginnt (falls noch nicht geschehen) die Testphase. */
    val license: LicenseRepository by lazy { LicenseRepository(LicenseStore(filesDir), billing, gateway, appScope) }

    val repository: SyncRepository by lazy {
        SyncRepository(gateway, SnapshotCache(filesDir), appScope, license = license)
    }

    private companion object {
        const val TAG = "DeskBreakPhone"
    }
}
