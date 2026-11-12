package dev.stepcron.wear.ui.license

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import dev.stepcron.shared.sync.SyncPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.tasks.await as awaitTask

/** Öffnet eine Seite auf dem verbundenen Handy; austauschbar, damit das ViewModel ohne Uhr testbar bleibt. */
interface PhoneOpener {
    /** `true`, wenn das Handy die Seite geöffnet hat (Google Play für `market://` und Play-Links); nie eine Ausnahme. */
    suspend fun open(uri: Uri): Boolean

    /**
     * Öffnet den Deep Link [uri] in DeskBreak auf einem erreichbaren Handy, auf dem die Handy-App installiert ist
     * (Capability [SyncPaths.CAPABILITY_PHONE]). `false`, wenn keines erreichbar ist oder keines ihn öffnet (z. B. eine
     * ältere Handy-App ohne den Link); nie eine Ausnahme.
     */
    suspend fun openInPhoneApp(uri: Uri): Boolean
}

/** Öffnet eine Adresse auf der Uhr selbst, z. B. in Google Play der Uhr; austauschbar wie [PhoneOpener]. */
fun interface WatchOpener {
    /** `true`, wenn eine App auf der Uhr die Adresse geöffnet hat; nie eine Ausnahme. */
    fun open(uri: Uri): Boolean
}

/**
 * Reihenfolge der Versuche beim Öffnen – ohne Android-Typen, damit sie ohne Uhr testbar ist. Jeder Versuch liefert
 * `true`, wenn er geklappt hat; danach folgt keiner mehr.
 */
internal object LinkFallbacks {

    /**
     * „Am Handy freischalten": zuerst die Freischalt-Seite in DeskBreak auf dem Handy – dort ist der Kauf direkt
     * möglich. Ohne erreichbare Handy-App (nicht installiert, zu alt) die App in Google Play auf dem Handy.
     */
    suspend fun unlockOnPhone(
        openInPhoneApp: suspend () -> Boolean,
        openStoreOnPhone: suspend () -> Boolean,
    ): PhoneLinkState = when {
        openInPhoneApp() -> PhoneLinkState.OPENED_IN_APP
        openStoreOnPhone() -> PhoneLinkState.OPENED_ON_PHONE
        else -> PhoneLinkState.FAILED
    }

    /**
     * „Abo verwalten": die Abo-Verwaltung von Google Play auf dem Handy, ohne erreichbares Android-Handy (z. B. mit
     * einem iPhone gekoppelt) auf der Uhr. Klappt beides nicht, zeigt der Bildschirm die Adresse zum Kündigen im Browser.
     */
    suspend fun manageSubscription(
        openOnPhone: suspend () -> Boolean,
        openOnWatch: () -> Boolean,
    ): PhoneLinkState = when {
        openOnPhone() -> PhoneLinkState.OPENED_ON_PHONE
        openOnWatch() -> PhoneLinkState.OPENED_ON_WATCH
        else -> PhoneLinkState.FAILED
    }

    /** Knoten für den Deep Link: per Bluetooth verbundene (nahe) zuerst, dann die übrigen erreichbaren. */
    fun phoneAppNodeIds(nodes: Collection<Node>): List<String> =
        nodes.sortedByDescending { it.isNearby }.map { it.id }
}

/**
 * [PhoneOpener] über den [RemoteActivityHelper] von Wear OS: Das Handy öffnet [Intent.ACTION_VIEW] mit der Adresse.
 * Ohne verbundenes Android-Handy (oder mit einem iPhone) schlägt das fehl.
 */
class RemotePhoneOpener(context: Context) : PhoneOpener {

    private val appContext = context.applicationContext

    // Rückrufe auf dem Main-Thread: Der Standard-Executor wäre ein eigener Thread pro Instanz.
    private val helper by lazy { RemoteActivityHelper(appContext, ContextCompat.getMainExecutor(appContext)) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(appContext) }

    override suspend fun open(uri: Uri): Boolean = start(uri, nodeId = null)

    override suspend fun openInPhoneApp(uri: Uri): Boolean {
        val nodes = try {
            // Nur jetzt verbundene Handys (FILTER_REACHABLE): Ein Deep Link lässt sich nicht nachreichen.
            withTimeoutOrNull(CAPABILITY_TIMEOUT_MILLIS) {
                capabilityClient.getCapability(SyncPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                    .awaitTask().nodes
            }.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keine Play-Dienste bzw. kein Data Layer: dann über Google Play.
            Log.w(TAG, "Handy-App nicht gefunden", e)
            emptySet()
        }
        return LinkFallbacks.phoneAppNodeIds(nodes).any { nodeId -> start(uri, nodeId) }
    }

    /** Öffnet [uri] auf dem Knoten [nodeId] bzw. ohne ihn auf dem gekoppelten Handy. */
    private suspend fun start(uri: Uri, nodeId: String?): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        withTimeoutOrNull(TIMEOUT_MILLIS) {
            helper.startRemoteActivity(intent, nodeId).await()
            true
        } ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // RemoteIntentException: kein Handy verbunden bzw. es kann die Seite nicht öffnen.
        Log.w(TAG, "Seite auf dem Handy nicht geöffnet", e)
        false
    }

    companion object {
        private const val TAG = "RemotePhoneOpener"
        private const val TIMEOUT_MILLIS = 20_000L
        private const val CAPABILITY_TIMEOUT_MILLIS = 5_000L

        /** Die Seite der App in Google Play (auf dem Handy die Play-Store-App). */
        fun storePage(packageName: String): Uri = "market://details?id=$packageName".toUri()
    }
}

/**
 * [WatchOpener] über [Context.startActivity]. Aus dem Anwendungskontext, weil das ViewModel keine Activity hält – die
 * Zielseite (Google Play) läuft ohnehin in ihrer eigenen Task.
 */
class ActivityWatchOpener(context: Context) : WatchOpener {

    private val appContext = context.applicationContext

    // NEW_TASK ist aus dem Anwendungskontext Pflicht; die Warnung zu den Wear-OS-Recents betrifft eigene Activities,
    // Google Play erscheint ohnehin als eigener Eintrag.
    @SuppressLint("WearRecents")
    override fun open(uri: Uri): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        // Viele Uhren haben weder Browser noch eine Play-Version, die die Adresse öffnet.
        Log.i(TAG, "Keine App auf der Uhr für $uri")
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "Seite auf der Uhr nicht geöffnet", e)
        false
    }

    private companion object {
        const val TAG = "ActivityWatchOpener"
    }
}
