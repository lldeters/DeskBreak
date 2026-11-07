package dev.stepcron.wear.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Öffnet System-Einstellungsseiten (mit Fallback, falls Wear OS die Seite nicht anbietet). */
object SystemSettings {

    fun openAppSettings(context: Context) {
        startSafely(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)),
        )
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context))
            if (startSafely(context, intent)) return
        }
        openAppSettings(context)
    }

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

    /** Startet aus der Activity heraus, damit keine Task-Flags nötig sind (Wear-OS-Recents). */
    private fun startSafely(context: Context, intent: Intent): Boolean {
        val activity = context.findActivity() ?: return false
        return try {
            activity.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
