package dev.stepcron.wear.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.stepcron.wear.appContainer
import dev.stepcron.wear.domain.AlarmKind

/** Empfängt den AlarmManager-Alarm sowie die Notification-Aktionen „Erledigt" und „Snooze". */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val engine = context.appContainer.engine
        when (intent.action) {
            ACTION_ALARM -> {
                val kind = AlarmKind.fromName(intent.getStringExtra(EXTRA_ALARM_KIND))
                launchAsync { engine.onAlarmFired(kind) }
            }
            ACTION_DONE -> launchAsync { engine.onReminderDone() }
            ACTION_SNOOZE -> launchAsync { engine.onReminderSnoozed() }
            else -> Log.w(TAG, "Unbekannte Aktion: ${intent.action}")
        }
    }

    companion object {
        private const val TAG = "ReminderReceiver"

        const val ACTION_ALARM = "dev.stepcron.wear.action.ALARM"
        const val ACTION_DONE = "dev.stepcron.wear.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "dev.stepcron.wear.action.REMINDER_SNOOZE"
        const val EXTRA_ALARM_KIND = "dev.stepcron.wear.extra.ALARM_KIND"
    }
}
