package dev.stepcron.mobile.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableStatusCodes
import dev.stepcron.shared.sync.SyncPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [WatchGateway] über die Play Services (Wearable Data Layer). Jeder Aufruf hat eine Zeitgrenze; jede Ausnahme
 * wird zu einer [SyncException] mit passendem [SyncProblem].
 */
class DataLayerGateway(context: Context, private val scope: CoroutineScope) : WatchGateway {

    private val appContext = context.applicationContext
    private val dataClient: DataClient by lazy { Wearable.getDataClient(appContext) }
    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(appContext) }
    private val capabilityClient: CapabilityClient by lazy { Wearable.getCapabilityClient(appContext) }
    private val nodeClient by lazy { Wearable.getNodeClient(appContext) }

    @Volatile
    private var listener: GatewayListener? = null
    private var registered = false

    private val dataListener = DataClient.OnDataChangedListener { events -> onDataChanged(events) }
    private val messageListener = MessageClient.OnMessageReceivedListener { event -> onMessage(event) }
    private val capabilityListener = CapabilityClient.OnCapabilityChangedListener { listener?.onWatchesChanged() }

    override suspend fun checkAvailability(): SyncProblem? = try {
        val result = GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(appContext)
        if (result == ConnectionResult.SUCCESS) null else SyncProblem.PlayServicesMissing
    } catch (e: Exception) {
        // Etwa auf Geräten ganz ohne Google-Dienste.
        SyncProblem.PlayServicesMissing
    }

    override suspend fun findWatches(): WatchNodes {
        val capable = call { capabilityClient.getCapability(SyncPaths.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE) }
        val connected = call { nodeClient.connectedNodes }
        return WatchNodes(
            withApp = capable.nodes.toWatchNodes(),
            connected = connected.toWatchNodes(),
        )
    }

    override suspend fun readSnapshots(): List<ByteArray> {
        // Alle DataItems der App (wenige: Snapshot der Uhr, ggf. eine laufende Wiederherstellung) und selbst nach dem
        // Pfad filtern – so hängt nichts davon ab, wie die Play Services eine URI ohne Knoten-ID auslegen.
        val buffer = call { dataClient.dataItems }
        // Der Puffer gilt nur bis release(): Einträge vorher einfrieren.
        val items = try {
            buffer.filter { it.uri.path == SyncPaths.SNAPSHOT }.map { it.freeze() }
        } finally {
            buffer.release()
        }
        return items.mapNotNull { readAsset(it, SyncPaths.SNAPSHOT_ASSET) }
    }

    /**
     * Liest das Asset [key] eines (eingefrorenen) DataItems; `null`, wenn es fehlt oder nicht lesbar ist. Die Uhr
     * legt es per PutDataMapRequest ab – dort steht es in der DataMap unter [key] (in `DataItem.assets` dagegen
     * unter „0", „1", …). Ein direkt per PutDataRequest abgelegtes Asset wird als Rückfall ebenfalls gefunden.
     */
    suspend fun readAsset(item: DataItem, key: String): ByteArray? = try {
        val mapAsset = try {
            DataMapItem.fromDataItem(item).dataMap.getAsset(key)
        } catch (e: RuntimeException) {
            // Kein DataMap-Inhalt (etwa leere Rohdaten).
            null
        }
        val response = when {
            mapAsset != null -> call(ASSET_TIMEOUT_MILLIS) { dataClient.getFdForAsset(mapAsset) }
            else -> {
                val asset = item.assets[key] ?: return null
                call(ASSET_TIMEOUT_MILLIS) { dataClient.getFdForAsset(asset) }
            }
        }
        try {
            withContext(Dispatchers.IO) { response.inputStream.use { it.readBytes() } }
        } finally {
            response.release()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Asset $key von ${item.uri} nicht lesbar", e)
        null
    }

    override suspend fun sendMessage(nodeIds: List<String>, path: String, data: ByteArray) {
        var lastError: SyncException? = null
        var delivered = 0
        for (nodeId in nodeIds) {
            try {
                call { messageClient.sendMessage(nodeId, path, data) }
                delivered++
            } catch (e: SyncException) {
                lastError = e
            }
        }
        if (delivered == 0) throw lastError ?: SyncException(SyncProblem.NoWatch)
    }

    override suspend fun putLicense(data: ByteArray) {
        // Die Bytes direkt als Daten des DataItems (klein, kein Asset); die Uhr liest sie mit DataItem.getData().
        val request = PutDataRequest.create(SyncPaths.LICENSE).setData(data).setUrgent()
        call(PUT_TIMEOUT_MILLIS) { dataClient.putDataItem(request) }
    }

    override suspend fun putRestore(restoreId: String, backupBytes: ByteArray) {
        val request = PutDataMapRequest.create(SyncPaths.RESTORE_PREFIX + restoreId).apply {
            dataMap.putAsset(SyncPaths.RESTORE_ASSET, Asset.createFromBytes(backupBytes))
        }.setUrgent().asPutDataRequest()
        call(PUT_TIMEOUT_MILLIS) { dataClient.putDataItem(request) }
    }

    override suspend fun deleteRestore(restoreId: String) {
        // Das DataItem hat dieses Handy angelegt: mit der eigenen Knoten-ID als Host eindeutig adressieren.
        val localNodeId = call { nodeClient.localNode }.id
        val uri = Uri.Builder()
            .scheme(PutDataRequest.WEAR_URI_SCHEME)
            .authority(localNodeId)
            .path(SyncPaths.RESTORE_PREFIX + restoreId)
            .build()
        call { dataClient.deleteDataItems(uri, DataClient.FILTER_LITERAL) }
    }

    override fun startListening(listener: GatewayListener) {
        this.listener = listener
        if (registered) return
        registered = true
        try {
            // Ohne URI-Filter: Die Pfade prüfen onDataChanged/onMessage selbst (eindeutiger als Platzhalter-URIs).
            dataClient.addListener(dataListener)
                .addOnFailureListener { Log.w(TAG, "DataClient-Listener nicht registriert", it) }
            messageClient.addListener(messageListener)
                .addOnFailureListener { Log.w(TAG, "MessageClient-Listener nicht registriert", it) }
            capabilityClient.addListener(capabilityListener, SyncPaths.CAPABILITY_WATCH)
                .addOnFailureListener { Log.w(TAG, "Capability-Listener nicht registriert", it) }
        } catch (e: Exception) {
            // Ohne Play Services: Die App zeigt den Fehler beim Abgleich an.
            Log.w(TAG, "Listener nicht registriert", e)
        }
    }

    override fun stopListening() {
        listener = null
        if (!registered) return
        registered = false
        try {
            dataClient.removeListener(dataListener)
            messageClient.removeListener(messageListener)
            capabilityClient.removeListener(capabilityListener, SyncPaths.CAPABILITY_WATCH)
        } catch (e: Exception) {
            Log.w(TAG, "Listener nicht entfernt", e)
        }
    }

    /** Läuft auf dem Main-Thread; der Puffer gilt nur während des Aufrufs. */
    private fun onDataChanged(events: DataEventBuffer) {
        val items = snapshotItems(events)
        if (items.isEmpty()) return
        scope.launch {
            for (item in items) {
                val bytes = readAsset(item, SyncPaths.SNAPSHOT_ASSET) ?: continue
                listener?.onSnapshotReceived(bytes)
            }
        }
    }

    private fun onMessage(event: MessageEvent) {
        if (event.path == SyncPaths.RESTORE_RESULT) listener?.onRestoreResultReceived(event.data ?: ByteArray(0))
    }

    /**
     * Führt einen Play-Services-Aufruf mit Zeitgrenze aus. Ein abgebrochener Task (nicht die Coroutine selbst) gilt
     * als Fehler, sonst bliebe z. B. „Abgleich läuft" stehen.
     */
    private suspend fun <T> call(timeoutMillis: Long = CALL_TIMEOUT_MILLIS, task: () -> Task<T>): T {
        val box = try {
            withTimeoutOrNull(timeoutMillis) { Box(task().await()) }
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw SyncException(SyncProblem.Failed(statusCode = null, message = e.message), e)
        } catch (e: ApiException) {
            throw SyncException(problemFor(e), e)
        } catch (e: SyncException) {
            throw e
        } catch (e: Exception) {
            throw SyncException(SyncProblem.Failed(statusCode = null, message = e.message), e)
        }
        return (box ?: throw SyncException(SyncProblem.Timeout)).value
    }

    private class Box<T>(val value: T)

    companion object {
        private const val TAG = "DeskBreakSync"
        private const val CALL_TIMEOUT_MILLIS = 10_000L
        private const val ASSET_TIMEOUT_MILLIS = 30_000L
        private const val PUT_TIMEOUT_MILLIS = 30_000L

        /** Geänderte Snapshot-DataItems eines Ereignis-Puffers, eingefroren (überleben das Freigeben des Puffers). */
        fun snapshotItems(events: DataEventBuffer): List<DataItem> =
            events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == SyncPaths.SNAPSHOT }
                .map { it.dataItem.freeze() }

        fun problemFor(e: ApiException): SyncProblem = when (e.statusCode) {
            // Wearable-API fehlt: keine Begleit-App der Uhr (Wear OS) auf dem Handy.
            CommonStatusCodes.API_NOT_CONNECTED -> SyncProblem.WearApiUnavailable
            // Gleiche Werte wie die veralteten CommonStatusCodes-Konstanten.
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED, ConnectionResult.SERVICE_DISABLED ->
                SyncProblem.PlayServicesMissing

            WearableStatusCodes.TARGET_NODE_NOT_CONNECTED, WearableStatusCodes.INVALID_TARGET_NODE -> SyncProblem.NoWatch
            CommonStatusCodes.TIMEOUT -> SyncProblem.Timeout
            else -> SyncProblem.Failed(e.statusCode, e.message)
        }

        private fun Collection<Node>.toWatchNodes(): List<WatchNode> =
            sortedByDescending { it.isNearby }.map { WatchNode(it.id, it.displayName) }
    }
}
