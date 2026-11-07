package dev.stepcron.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Zentrale Prüfung der Laufzeit-Berechtigungen. */
object Permissions {

    /** Berechtigungen, die zur Laufzeit angefragt werden müssen. */
    fun runtimePermissions(): Array<String> = buildList {
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun missingRuntimePermissions(context: Context): Array<String> =
        runtimePermissions().filterNot { isGranted(context, it) }.toTypedArray()

    fun hasActivityRecognition(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACTIVITY_RECOGNITION)

    fun canPostNotifications(context: Context): Boolean {
        val permissionOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        return permissionOk && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
