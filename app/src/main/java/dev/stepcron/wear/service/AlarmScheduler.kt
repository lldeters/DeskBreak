package dev.stepcron.wear.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dev.stepcron.wear.domain.AlarmKind
import dev.stepcron.wear.domain.PlannedAlarm

/**
 * Plant den einzigen DeskBreak-Alarm über den [AlarmManager] (kein WorkManager).
 * Ein neuer Alarm ersetzt immer den vorherigen, da dieselbe PendingIntent-Identität genutzt wird.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** @return `true`, wenn der Alarm exakt geplant wurde (sonst ungenauer Fallback). */
    fun schedule(alarm: PlannedAlarm): Boolean {
        val pendingIntent = alarmPendingIntent(alarm.kind)
        if (canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarm.triggerAtMillis,
                    pendingIntent,
                )
                return true
            } catch (e: SecurityException) {
                Log.w(TAG, "Exakter Alarm nicht erlaubt – nutze ungenauen Fallback", e)
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, pendingIntent)
        return false
    }

    fun cancel() {
        alarmManager.cancel(alarmPendingIntent(AlarmKind.REMINDER))
    }

    private fun alarmPendingIntent(kind: AlarmKind): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_ALARM)
            .putExtra(ReminderReceiver.EXTRA_ALARM_KIND, kind.name)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val TAG = "AlarmScheduler"
        const val REQUEST_CODE_ALARM = 100
    }
}
