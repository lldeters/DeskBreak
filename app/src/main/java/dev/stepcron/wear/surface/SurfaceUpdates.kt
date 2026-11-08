package dev.stepcron.wear.surface

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

/** Fordert Kachel und Komplikation auf, sich neu zu laden – nach jeder Änderung von Timer oder Einstellungen. */
object SurfaceUpdates {

    private const val TAG = "SurfaceUpdates"

    fun request(context: Context) {
        val appContext = context.applicationContext
        try {
            TileService.getUpdater(appContext).requestUpdate(DeskBreakTileService::class.java)
            ComplicationDataSourceUpdateRequester
                .create(appContext, ComponentName(appContext, DeskBreakComplicationService::class.java))
                .requestUpdateAll()
        } catch (e: RuntimeException) {
            // Ohne Kachel-Host oder Zifferblatt (z. B. Emulator ohne Wear-Systemoberfläche) gibt es nichts zu tun.
            Log.w(TAG, "Aktualisierung von Kachel/Komplikation nicht möglich", e)
        }
    }
}
