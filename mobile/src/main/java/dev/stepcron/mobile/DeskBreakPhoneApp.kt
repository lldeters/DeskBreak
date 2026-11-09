package dev.stepcron.mobile

import android.app.Application
import android.util.Log
import dev.stepcron.mobile.sync.DataLayerGateway
import dev.stepcron.mobile.sync.SnapshotCache
import dev.stepcron.mobile.sync.SyncRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hält die prozessweiten Objekte: Repository und Data-Layer-Zugriff leben so lange wie der Prozess, damit
 * Activity, ViewModel und der Hintergrunddienst denselben Stand teilen.
 */
class DeskBreakPhoneApp : Application() {

    /** Für Arbeit, die eine Activity überdauern soll (Senden, Wiederherstellen). Fehler werden nur geloggt. */
    val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Unerwarteter Fehler im Abgleich", error)
        },
    )

    val gateway: DataLayerGateway by lazy { DataLayerGateway(this, appScope) }

    val repository: SyncRepository by lazy { SyncRepository(gateway, SnapshotCache(filesDir), appScope) }

    private companion object {
        const val TAG = "DeskBreakPhone"
    }
}
