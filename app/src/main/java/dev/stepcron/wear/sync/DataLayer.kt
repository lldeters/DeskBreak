package dev.stepcron.wear.sync

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dev.stepcron.shared.sync.SyncPaths
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Zugang zum Wearable Data Layer der Play-Dienste. Jeder Aufruf kann fehlschlagen (keine Play-Dienste, kein Handy,
 * Zeitüberschreitung) und wirft dann – die Aufrufer fangen das ab.
 */
class DataLayer(context: Context) : SnapshotChannel {

    private val appContext = context.applicationContext
    private val dataClient by lazy { Wearable.getDataClient(appContext) }
    private val messageClient by lazy { Wearable.getMessageClient(appContext) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(appContext) }

    /** Auch nicht verbundene Handys zählen (FILTER_ALL): Der Data Layer stellt den Snapshot später zu. */
    override suspend fun hasPhone(): Boolean {
        val info = capabilityClient.getCapability(SyncPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_ALL).await()
        return info.nodes.isNotEmpty()
    }

    /**
     * Snapshot als DataItem [SyncPaths.SNAPSHOT] mit dem Asset [SyncPaths.SNAPSHOT_ASSET] in seiner DataMap
     * (lesbar mit `DataMapItem.fromDataItem(item).dataMap.getAsset(SNAPSHOT_ASSET)`), dringend zugestellt.
     */
    override suspend fun publishSnapshot(bytes: ByteArray) {
        val request = PutDataMapRequest.create(SyncPaths.SNAPSHOT).apply {
            dataMap.putAsset(SyncPaths.SNAPSHOT_ASSET, Asset.createFromBytes(bytes))
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
    }

    /**
     * Inhalt des Assets [key] von [item]; `null`, wenn es fehlt oder größer als [maxBytes] ist. Gesucht wird zuerst in
     * der DataMap (`PutDataMapRequest` auf dem Handy), dann unter [key] direkt am DataItem (`PutDataRequest.putAsset`),
     * zuletzt das einzige Asset des DataItems.
     */
    suspend fun readAsset(item: DataItem, key: String, maxBytes: Int = MAX_ASSET_BYTES): ByteArray? {
        val task = dataMapAsset(item, key)?.let { dataClient.getFdForAsset(it) }
            ?: (item.assets[key] ?: item.assets.values.singleOrNull())?.let { dataClient.getFdForAsset(it) }
            ?: return null
        val response = task.await()
        return try {
            response.inputStream.use { it.readAtMost(maxBytes) }
        } finally {
            response.release()
        }
    }

    /**
     * Daten der DataItems [SyncPaths.LICENSE] (je Handy eines; LicenseCodec.encodeInfo) – die Lizenz-Sicht der Handys,
     * auch wenn gerade keines verbunden ist. Gelesen werden alle DataItems der App (wenige) und nach dem Pfad gefiltert,
     * so hängt nichts davon ab, wie die Play-Dienste eine URI ohne Knoten-ID auslegen.
     */
    suspend fun readPhoneLicenses(): List<ByteArray> {
        val buffer = dataClient.dataItems.await()
        // Der Puffer gilt nur bis release(): Einträge vorher einfrieren.
        return try {
            buffer.filter { it.uri.path == SyncPaths.LICENSE }.mapNotNull { it.freeze().data }
        } finally {
            buffer.release()
        }
    }

    /** Löscht die DataItems unter [uri] (mit Knoten-ID als Host: nur die dieses Knotens) im ganzen Netz. */
    suspend fun delete(uri: Uri) {
        dataClient.deleteDataItems(uri).await()
    }

    suspend fun sendMessage(nodeId: String, path: String, data: ByteArray) {
        messageClient.sendMessage(nodeId, path, data).await()
    }

    /** Das Asset [key] aus der DataMap von [item]; `null`, wenn die Daten keine DataMap sind oder es fehlt. */
    private fun dataMapAsset(item: DataItem, key: String): Asset? = try {
        DataMapItem.fromDataItem(item).dataMap.getAsset(key)
    } catch (e: RuntimeException) {
        // Keine DataMap (z. B. PutDataRequest mit eigenen Bytes oder ohne Daten): direkt am DataItem suchen.
        null
    }

    private companion object {
        /** Eine Sicherung über viele Jahre bleibt deutlich darunter; schützt vor beschädigten Riesen-Assets. */
        const val MAX_ASSET_BYTES = 32 * 1024 * 1024

        /** Liest höchstens [limit] Bytes; `null`, wenn der Strom länger ist. */
        fun InputStream.readAtMost(limit: Int): ByteArray? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = read(buffer)
                if (read < 0) return out.toByteArray()
                out.write(buffer, 0, read)
                if (out.size() > limit) return null
            }
        }
    }
}
