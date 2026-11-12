package dev.stepcron.wear.ui.license

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.wear.remote.interactions.RemoteActivityHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Öffnet eine Seite auf dem verbundenen Handy; austauschbar, damit das ViewModel ohne Uhr testbar bleibt. */
fun interface PhoneOpener {
    /** `true`, wenn das Handy die Seite geöffnet hat; nie eine Ausnahme. */
    suspend fun open(uri: Uri): Boolean
}

/**
 * [PhoneOpener] über den [RemoteActivityHelper] von Wear OS: Das Handy öffnet [Intent.ACTION_VIEW] mit der Adresse
 * (Google Play für `market://` und Play-Links). Ohne verbundenes Android-Handy (oder mit einem iPhone) schlägt das fehl.
 */
class RemotePhoneOpener(context: Context) : PhoneOpener {

    private val appContext = context.applicationContext

    // Rückrufe auf dem Main-Thread: Der Standard-Executor wäre ein eigener Thread pro Instanz.
    private val helper by lazy { RemoteActivityHelper(appContext, ContextCompat.getMainExecutor(appContext)) }

    override suspend fun open(uri: Uri): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        withTimeoutOrNull(TIMEOUT_MILLIS) {
            helper.startRemoteActivity(intent).await()
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

        /** Die Seite der App in Google Play (auf dem Handy die Play-Store-App). */
        fun storePage(packageName: String): Uri = "market://details?id=$packageName".toUri()
    }
}
