package dev.stepcron.wear.service

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Führt [block] asynchron aus und hält den Broadcast per [BroadcastReceiver.goAsync] offen,
 * bis die Arbeit erledigt ist (maximal knapp unter dem 10-Sekunden-Limit).
 */
internal fun BroadcastReceiver.launchAsync(block: suspend CoroutineScope.() -> Unit) {
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            val finished = withTimeoutOrNull(BROADCAST_TIMEOUT_MILLIS) { block(); true }
            if (finished == null) Log.w(TAG, "Broadcast-Verarbeitung abgebrochen (Timeout)")
        } catch (e: Exception) {
            Log.e(TAG, "Broadcast-Verarbeitung fehlgeschlagen", e)
        } finally {
            pendingResult.finish()
        }
    }
}

private const val TAG = "DeskBreakReceiver"
private const val BROADCAST_TIMEOUT_MILLIS = 9_000L
