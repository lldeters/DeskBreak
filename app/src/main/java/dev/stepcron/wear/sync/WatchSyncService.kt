package dev.stepcron.wear.sync

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dev.stepcron.shared.license.LicenseCodec
import dev.stepcron.shared.sync.SyncCodec
import dev.stepcron.shared.sync.SyncPaths
import dev.stepcron.wear.AppContainer
import dev.stepcron.wear.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Empfängt Nachrichten und Wiederherstellungen der Handy-App über den Wearable Data Layer. Dünner Adapter: Die Arbeit
 * machen [WatchSync], [SettingsUpdateApplier], [BackupRestorer] und die Lizenz der Uhr.
 *
 * Die Rückrufe laufen auf einem Hintergrund-Thread des Dienstes; die Arbeit läuft dort blockierend mit Zeitlimit.
 * Kein Fehler darf den Dienst abstürzen lassen – er wird protokolliert.
 */
class WatchSyncService : WearableListenerService() {

    private val container: AppContainer get() = appContainer

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            SyncPaths.REQUEST_SYNC -> handle("Abgleich", MESSAGE_TIMEOUT_MILLIS) { container.watchSync.publishNow() }
            SyncPaths.UPDATE_SETTINGS -> handle("Einstellungen", MESSAGE_TIMEOUT_MILLIS) {
                val update = SyncCodec.decodeSettingsUpdate(event.data)
                if (update == null) {
                    Log.w(TAG, "Unlesbare Einstellungen vom Handy übergangen")
                } else {
                    container.settingsUpdateApplier.apply(update)
                }
                // Auch ohne Änderung: Das Handy sieht so den tatsächlichen (bereinigten) Stand.
                container.watchSync.publishNow()
            }
            SyncPaths.LICENSE -> handle("Lizenz", MESSAGE_TIMEOUT_MILLIS) {
                val info = LicenseCodec.decodeInfo(event.data)
                if (info == null) {
                    Log.w(TAG, "Unlesbare Lizenz vom Handy übergangen")
                } else {
                    // Speichert, gleicht die Erinnerungen ab, falls die Lizenz dadurch aktiv bzw. inaktiv wird.
                    container.watchLicense.applyPeerInfo(info)
                }
                // Die Sicht der Uhr zurück (frühester Testbeginn, eigene Play-Abfrage).
                container.watchSync.publishNow()
            }
            else -> Log.d(TAG, "Unbekannte Nachricht ${event.path}")
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        // Der Puffer gilt nur während dieses Aufrufs: die betroffenen Einträge vorab einfrieren.
        val restores = events
            .filter { it.type == DataEvent.TYPE_CHANGED && SyncMapping.restoreId(it.dataItem.uri.path) != null }
            .map { it.dataItem.freeze() }
        restores.forEach { item -> handle("Wiederherstellung", RESTORE_TIMEOUT_MILLIS) { restore(item) } }
    }

    /** Sicherung lesen und einspielen, DataItem löschen, dem Handy antworten und den neuen Stand veröffentlichen. */
    private suspend fun restore(item: DataItem) {
        val uri = item.uri
        val restoreId = SyncMapping.restoreId(uri.path) ?: return
        val dataLayer = container.dataLayer
        val bytes = attempt("Sicherung lesen") { dataLayer.readAsset(item, SyncPaths.RESTORE_ASSET) }
        val restorer = container.backupRestorer
        val result = attempt("Sicherung einspielen") { restorer.restore(restoreId, bytes) }
            ?: restorer.failure(restoreId, RestoreFailure.NOT_SAVED)
        Log.i(TAG, "Wiederherstellung $restoreId: Erfolg ${result.success}, ${result.restoredEvents} Ereignisse")
        // Die Sicherung ist verarbeitet (auch eine unbrauchbare): nicht erneut zustellen.
        attempt("Sicherung löschen") { dataLayer.delete(uri) }
        val phoneNode = uri.host
        if (phoneNode != null) {
            attempt("Antwort senden") {
                dataLayer.sendMessage(phoneNode, SyncPaths.RESTORE_RESULT, SyncCodec.encodeRestoreResult(result))
            }
        }
        container.watchSync.publishNow()
    }

    /** Führt [block] aus; ein Fehler wird protokolliert und ergibt `null`, damit die folgenden Schritte laufen. */
    private suspend fun <T> attempt(step: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$step fehlgeschlagen", e)
        null
    }

    private fun handle(what: String, timeoutMillis: Long, block: suspend () -> Unit) {
        try {
            runBlocking { withTimeout(timeoutMillis) { block() } }
        } catch (e: CancellationException) {
            Log.w(TAG, "$what abgebrochen (Zeitlimit)", e)
        } catch (e: Exception) {
            Log.e(TAG, "$what fehlgeschlagen", e)
        }
    }

    private companion object {
        const val TAG = "WatchSyncService"
        const val MESSAGE_TIMEOUT_MILLIS = 30_000L

        /** Länger: Das Asset kommt ggf. erst per Bluetooth, und die Historie kann einige tausend Einträge haben. */
        const val RESTORE_TIMEOUT_MILLIS = 60_000L
    }
}
