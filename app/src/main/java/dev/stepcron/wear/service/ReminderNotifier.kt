package dev.stepcron.wear.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.stepcron.wear.MainActivity
import dev.stepcron.wear.Permissions
import dev.stepcron.wear.R
import dev.stepcron.wear.domain.ExercisePicker

/** Notifications (Erinnerung + Schritterkennung) und das Vibrationsmuster. */
class ReminderNotifier(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val reminderChannel = NotificationChannelCompat.Builder(
            CHANNEL_REMINDER,
            NotificationManagerCompat.IMPORTANCE_HIGH,
        )
            .setName(context.getString(R.string.channel_reminder_name))
            .setDescription(context.getString(R.string.channel_reminder_description))
            // Die App vibriert selbst mit einem eigenen, mehrstufigen Muster.
            .setVibrationEnabled(false)
            .setSound(null, null)
            .setShowBadge(true)
            .build()
        val trackingChannel = NotificationChannelCompat.Builder(
            CHANNEL_STEP_TRACKING,
            NotificationManagerCompat.IMPORTANCE_LOW,
        )
            .setName(context.getString(R.string.channel_tracking_name))
            .setDescription(context.getString(R.string.channel_tracking_description))
            .setVibrationEnabled(false)
            .setSound(null, null)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannelsCompat(listOf(reminderChannel, trackingChannel))
    }

    /**
     * Zeigt die High-Priority-Erinnerung mit einer Bewegungsidee und den Aktionen „Erledigt",
     * „+10 Min Snooze" und „1-Min-Übung" (öffnet den Timer in der App).
     */
    fun showReminder(reminderAtMillis: Long = System.currentTimeMillis()) {
        if (!Permissions.canPostNotifications(context)) {
            Log.w(TAG, "Notifications nicht erlaubt – Erinnerung nur per Vibration")
            return
        }
        val exercises = context.resources.getStringArray(R.array.exercises)
        val exerciseIndex = ExercisePicker.indexFor(reminderAtMillis, exercises.size)
        val text = context.getString(R.string.notification_reminder_text_idea, exercises[exerciseIndex])
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle(context.getString(R.string.notification_reminder_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(openAppPendingIntent())
            .addAction(
                R.drawable.ic_check,
                context.getString(R.string.action_done),
                actionPendingIntent(ReminderReceiver.ACTION_DONE, REQUEST_CODE_DONE),
            )
            .addAction(
                R.drawable.ic_snooze,
                context.getString(R.string.action_snooze),
                actionPendingIntent(ReminderReceiver.ACTION_SNOOZE, REQUEST_CODE_SNOOZE),
            )
            .addAction(
                R.drawable.ic_walk,
                context.getString(R.string.action_exercise),
                exercisePendingIntent(exerciseIndex),
            )
            .build()
        try {
            notificationManager.notify(NOTIFICATION_ID_REMINDER, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification konnte nicht angezeigt werden", e)
        }
    }

    fun cancelReminder() {
        notificationManager.cancel(NOTIFICATION_ID_REMINDER)
    }

    /** Builder für die laufende Foreground-Notification der Schritterkennung. */
    fun stepTrackingNotificationBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_STEP_TRACKING)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle(context.getString(R.string.notification_tracking_title))
            .setContentText(context.getString(R.string.notification_tracking_text))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setLocalOnly(true)
            .setContentIntent(openAppPendingIntent())

    fun openAppPendingIntent(): PendingIntent {
        // Keine Task-Flags: Wear OS verwaltet Recents selbst (taskAffinity="" an der Activity).
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Deutliches, mehrstufiges Muster: 2× lang, 3× kurz, 1× lang (≈ 3,7 s). */
    fun vibrateReminder() {
        val vibrator = vibrator() ?: return
        if (!vibrator.hasVibrator()) return
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(VIBRATION_TIMINGS, VIBRATION_AMPLITUDES, NO_REPEAT)
        } else {
            VibrationEffect.createWaveform(VIBRATION_TIMINGS, NO_REPEAT)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, attributes)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Vibration fehlgeschlagen", e)
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }

    /** Öffnet die App direkt im 1-Minuten-Timer der Übung (siehe [exerciseIndexOf] und DeskBreakNavHost). */
    private fun exercisePendingIntent(exerciseIndex: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_EXERCISE_INDEX, exerciseIndex)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_EXERCISE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_REMINDER = "movement_reminders"
        const val CHANNEL_STEP_TRACKING = "step_tracking"
        const val NOTIFICATION_ID_REMINDER = 1001
        const val NOTIFICATION_ID_STEP_TRACKING = 1002

        private const val TAG = "ReminderNotifier"
        private const val REQUEST_CODE_OPEN_APP = 200
        private const val REQUEST_CODE_DONE = 201
        private const val REQUEST_CODE_SNOOZE = 202
        private const val REQUEST_CODE_EXERCISE = 203

        private const val EXTRA_EXERCISE_INDEX = "dev.stepcron.wear.extra.EXERCISE_INDEX"
        private const val NO_REPEAT = -1

        /** Übung, die der Intent öffnen soll (Aktion „1-Min-Übung" der Erinnerung), sonst `null`. */
        fun exerciseIndexOf(intent: Intent?): Int? =
            intent?.takeIf { it.hasExtra(EXTRA_EXERCISE_INDEX) }?.getIntExtra(EXTRA_EXERCISE_INDEX, 0)

        // Abwechselnd Pause/Vibration (beginnend mit Pause), Werte in Millisekunden.
        private val VIBRATION_TIMINGS = longArrayOf(0, 500, 250, 500, 400, 150, 120, 150, 120, 150, 400, 800)
        private val VIBRATION_AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 200, 0, 200, 0, 200, 0, 255)
    }
}
