package dev.stepcron.wear.service

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.stepcron.wear.appContainer

/**
 * Stellt den Alarm nach einem Neustart der Uhr bzw. einem App-Update aus dem DataStore wieder her.
 * Bei Zeit-/Zeitzonenwechsel oder geänderter Exakt-Alarm-Berechtigung wird neu geplant.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val engine = context.appContainer.engine
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> launchAsync { engine.restoreAfterBoot() }

            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> launchAsync { engine.resync() }
        }
    }
}
