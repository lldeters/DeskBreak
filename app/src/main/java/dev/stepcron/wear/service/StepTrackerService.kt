package dev.stepcron.wear.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import dev.stepcron.wear.Permissions
import dev.stepcron.wear.R
import dev.stepcron.wear.appContainer
import dev.stepcron.wear.data.settings.ReminderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Schlanker Foreground-Service (Typ „health"), der nur während der aktiven Zeit läuft und den
 * [StepTracker] am Leben hält. Er wird vom [ReminderEngine] gestartet und gestoppt.
 */
class StepTrackerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stepTracker: StepTracker? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Nach startForegroundService() muss startForeground() immer zuerst erfolgen.
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (stepTracker == null && !startTracking()) {
            Log.w(TAG, "Kein Schrittsensor verfügbar – Dienst wird beendet")
            stopSelf()
            return START_NOT_STICKY
        }
        // Bei einem Neustart durch das System (START_STICKY) die Bedingungen erneut prüfen.
        scope.launch {
            val settings = appContainer.settingsRepository.currentSettings()
            if (!shouldRun(this@StepTrackerService, settings, System.currentTimeMillis(), ZoneId.systemDefault())) {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun enterForeground(): Boolean {
        val notifier = appContainer.notifier
        val builder = notifier.stepTrackingNotificationBuilder()
        return try {
            // Wear-OS-„Ongoing Activity": Symbol auf dem Zifferblatt und im App-Launcher.
            OngoingActivity.Builder(applicationContext, ReminderNotifier.NOTIFICATION_ID_STEP_TRACKING, builder)
                .setStaticIcon(R.drawable.ic_walk)
                .setTouchIntent(notifier.openAppPendingIntent())
                .setStatus(Status.Builder().addTemplate(getString(R.string.ongoing_status)).build())
                .build()
                .apply(applicationContext)
            val notification = builder.build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    ReminderNotifier.NOTIFICATION_ID_STEP_TRACKING,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
                )
            } else {
                startForeground(ReminderNotifier.NOTIFICATION_ID_STEP_TRACKING, notification)
            }
            true
        } catch (e: RuntimeException) {
            // z. B. ForegroundServiceStartNotAllowedException oder fehlende ACTIVITY_RECOGNITION.
            Log.w(TAG, "Foreground-Start nicht möglich", e)
            false
        }
    }

    private fun startTracking(): Boolean {
        val sensorManager = getSystemService(SensorManager::class.java) ?: return false
        val tracker = StepTracker(sensorManager) { onWalkDetected() }
        if (!tracker.start()) return false
        stepTracker = tracker
        scope.launch {
            appContainer.settingsRepository.updateState { it.copy(stepTrackingActive = true) }
        }
        return true
    }

    private fun onWalkDetected() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        // Kurzer Wake-Lock, damit Timer-Reset und Datenbank-Eintrag auch bei schlafender CPU fertig werden.
        lock.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
        scope.launch {
            try {
                appContainer.engine.onWalkDetected()
            } finally {
                if (lock.isHeld) lock.release()
            }
        }
    }

    override fun onDestroy() {
        stepTracker?.stop()
        stepTracker = null
        wakeLock?.let { if (it.isHeld) it.release() }
        val settingsRepository = appContainer.settingsRepository
        // Eigener Scope, da [scope] gleich abgebrochen wird.
        CoroutineScope(Dispatchers.IO).launch {
            settingsRepository.updateState { it.copy(stepTrackingActive = false) }
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "StepTrackerService"
        private const val ACTION_START = "dev.stepcron.wear.action.STEP_TRACKING_START"
        private const val WAKE_LOCK_TAG = "DeskBreak:StepReset"
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 10_000L

        /** Läuft die Schritterkennung jetzt sinnvollerweise? */
        fun shouldRun(context: Context, settings: ReminderSettings, nowMillis: Long, zone: ZoneId): Boolean =
            settings.enabled &&
                settings.autoStepEnabled &&
                settings.window.contains(nowMillis, zone) &&
                Permissions.hasActivityRecognition(context) &&
                StepTracker.isAvailable(context)

        fun start(context: Context) {
            val intent = Intent(context, StepTrackerService::class.java).setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException: Start aus dem Hintergrund nicht erlaubt.
                Log.w(TAG, "Schritterkennung konnte nicht gestartet werden", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Schritterkennung konnte nicht gestartet werden", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StepTrackerService::class.java))
        }
    }
}
