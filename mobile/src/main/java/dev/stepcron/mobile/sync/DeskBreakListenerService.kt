package dev.stepcron.mobile.sync

import android.util.Log
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dev.stepcron.mobile.DeskBreakPhoneApp
import dev.stepcron.shared.sync.SyncPaths
import kotlinx.coroutines.runBlocking

/**
 * Empfängt Daten der Uhr auch, wenn die App nicht im Vordergrund ist: neue Snapshots (DATA_CHANGED für
 * /deskbreak/snapshot) werden gespeichert, Antworten auf eine Wiederherstellung (MESSAGE_RECEIVED für
 * /deskbreak/restore-result) an das Repository weitergereicht. Die Play Services rufen beides auf einem
 * Hintergrund-Thread auf; dort darf blockierend gewartet werden.
 */
class DeskBreakListenerService : WearableListenerService() {

    private val app: DeskBreakPhoneApp get() = application as DeskBreakPhoneApp

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // Einfrieren, bevor der Puffer nach diesem Aufruf freigegeben wird.
        val items = DataLayerGateway.snapshotItems(dataEvents)
        if (items.isEmpty()) return
        try {
            runBlocking {
                for (item in items) {
                    val bytes = app.gateway.readAsset(item, SyncPaths.SNAPSHOT_ASSET) ?: continue
                    app.repository.ingestSnapshot(bytes)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Snapshot nicht übernommen", e)
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != SyncPaths.RESTORE_RESULT) return
        app.repository.onRestoreResultReceived(messageEvent.data ?: ByteArray(0))
    }

    private companion object {
        const val TAG = "DeskBreakSync"
    }
}
