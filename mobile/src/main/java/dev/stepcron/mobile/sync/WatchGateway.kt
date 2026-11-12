package dev.stepcron.mobile.sync

/** Eine Uhr im Wearable-Netz. */
data class WatchNode(val id: String, val displayName: String)

/**
 * Erreichbare Uhren: [withApp] melden die Capability `deskbreak_watch` (DeskBreak installiert), [connected] sind
 * alle verbundenen Geräte laut NodeClient (Rückfall, solange die Capability noch nicht bekannt ist).
 */
data class WatchNodes(val withApp: List<WatchNode>, val connected: List<WatchNode>) {
    /** Ziele für Nachrichten: bevorzugt die Uhren mit DeskBreak, sonst alle verbundenen. */
    val targets: List<WatchNode> get() = withApp.ifEmpty { connected }
}

/** Ereignisse des Data Layers, solange die App im Vordergrund lauscht. */
interface GatewayListener {
    /** Ein neuer Snapshot (Rohdaten des Assets) ist angekommen. */
    fun onSnapshotReceived(bytes: ByteArray)

    /** Antwort der Uhr auf eine Wiederherstellung (Rohdaten der Message). */
    fun onRestoreResultReceived(bytes: ByteArray)

    /** Eine Uhr mit DeskBreak ist erreichbar geworden oder weggefallen. */
    fun onWatchesChanged()
}

/**
 * Zugriff auf den Wearable Data Layer. In der App stecken die Play Services dahinter ([DataLayerGateway]), in
 * den Unit-Tests eine Attrappe. Alle Fehler kommen als [SyncException] mit passendem [SyncProblem].
 */
interface WatchGateway {
    /** `null`, wenn die Play Services nutzbar sind, sonst das Problem. Wirft nicht. */
    suspend fun checkAvailability(): SyncProblem?

    suspend fun findWatches(): WatchNodes

    /** Rohdaten aller Snapshots, die der Data Layer gerade vorhält (je Uhr einer). */
    suspend fun readSnapshots(): List<ByteArray>

    /** Schickt eine Message an alle [nodeIds]; wirft nur, wenn sie bei keiner ankam. */
    suspend fun sendMessage(nodeIds: List<String>, path: String, data: ByteArray)

    /**
     * Schickt eine Message an die erreichbaren Uhren ([WatchNodes.targets]), z. B. die Lizenz-Sicht des Handys.
     * Wirft [SyncException], wenn die Play Services fehlen, keine Uhr erreichbar ist oder sie bei keiner ankam.
     */
    suspend fun sendToWatches(path: String, data: ByteArray) {
        checkAvailability()?.let { throw SyncException(it) }
        val targets = findWatches().targets
        if (targets.isEmpty()) throw SyncException(SyncProblem.NoWatch)
        sendMessage(targets.map { it.id }, path, data)
    }

    /** Legt die Sicherung als DataItem `RESTORE_PREFIX<restoreId>` mit Asset ab. */
    suspend fun putRestore(restoreId: String, backupBytes: ByteArray)

    /** Entfernt das DataItem der Wiederherstellung wieder (nach Antwort oder Zeitüberschreitung). */
    suspend fun deleteRestore(restoreId: String)

    fun startListening(listener: GatewayListener)

    fun stopListening()
}
