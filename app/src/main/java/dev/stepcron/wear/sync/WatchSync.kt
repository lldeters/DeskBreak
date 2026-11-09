package dev.stepcron.wear.sync

import android.util.Log
import dev.stepcron.shared.sync.SyncCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Weg des Snapshots zum Handy (in der App: [DataLayer], in den Unit-Tests eine Attrappe). */
interface SnapshotChannel {
    /** Ob ein Handy mit der DeskBreak-App bekannt ist – auch wenn es gerade nicht verbunden ist. */
    suspend fun hasPhone(): Boolean

    /**
     * Legt den kodierten Snapshot ([SyncCodec.encodeSnapshot]) ab; der Data Layer stellt ihn zu, sobald das Handy
     * erreichbar ist.
     */
    suspend fun publishSnapshot(bytes: ByteArray)
}

/**
 * Veröffentlicht den Stand der Uhr für die Handy-App. Rein optional: Jeder Fehler (keine Play-Dienste, kein Handy,
 * Datenbank) wird protokolliert und geschluckt – die Uhr funktioniert ohne Handy genauso, und die Engine wartet nie
 * auf den Abgleich.
 */
class WatchSync(
    private val builder: SnapshotBuilder,
    private val channel: SnapshotChannel,
    /** App-weiter Scope für den verzögerten Abgleich. */
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEBOUNCE_MILLIS,
) {
    /** Snapshots nacheinander bauen und ablegen, damit nie ein älterer einen neueren überschreibt. */
    private val publishMutex = Mutex()
    private val lock = Any()
    private var pending: Job? = null

    /**
     * Nach einer Änderung auf der Uhr: veröffentlicht nach [debounceMillis] – mehrere Änderungen kurz hintereinander
     * (z. B. Einstellungen durchtippen) ergeben einen Snapshot mit dem dann aktuellen Stand. Ohne bekanntes Handy
     * (reine Uhr-Nutzung) entfällt die Arbeit ganz. Kehrt sofort zurück.
     */
    fun requestPublish() {
        synchronized(lock) {
            if (pending?.isActive == true) return
            pending = scope.launch {
                delay(debounceMillis)
                synchronized(lock) { pending = null }
                publish(onlyWithPhone = true)
            }
        }
    }

    /**
     * Veröffentlicht sofort (auf Anfrage des Handys, nach Änderungen vom Handy) und ersetzt einen noch wartenden
     * verzögerten Abgleich. @return ob der Snapshot abgelegt wurde.
     */
    suspend fun publishNow(): Boolean {
        synchronized(lock) {
            pending?.cancel()
            pending = null
        }
        return publish(onlyWithPhone = false)
    }

    private suspend fun publish(onlyWithPhone: Boolean): Boolean = publishMutex.withLock {
        try {
            if (onlyWithPhone && !channel.hasPhone()) return@withLock false
            channel.publishSnapshot(SyncCodec.encodeSnapshot(builder.build()))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Kein Handy, keine Play-Dienste (ApiException), Datenbank: beim nächsten Anlass erneut.
            Log.w(TAG, "Snapshot nicht veröffentlicht", e)
            false
        }
    }

    companion object {
        private const val TAG = "WatchSync"

        /** Verzögerung des Abgleichs nach Änderungen auf der Uhr. */
        const val DEBOUNCE_MILLIS = 20_000L
    }
}
