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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
            stopSelfAndForgetStart()
            return START_NOT_STICKY
        }
        if (stepTracker == null && !startTracking()) {
            Log.w(TAG, "Kein Schrittsensor verfügbar – Dienst wird beendet")
            stopSelfAndForgetStart()
            return START_NOT_STICKY
        }
        // Bei einem Neustart durch das System (START_STICKY) die Bedingungen erneut prüfen.
        scope.launch {
            val settings = appContainer.settingsRepository.currentSettings()
            if (!shouldRun(this@StepTrackerService, settings, System.currentTimeMillis(), ZoneId.systemDefault())) {
                stopSelfAndForgetStart()
            }
        }
        return START_STICKY
    }

    /** Beendet den Dienst und erlaubt dem nächsten [start] wieder einen echten Start. */
    private fun stopSelfAndForgetStart() {
        startRequested = false
        stopSelf()
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
        val settingsRepository = appContainer.settingsRepository
        scope.launch { settingsRepository.updateState { it.copy(stepTrackingActive = true) } }
        // Schwelle aus den Einstellungen – sofort und bei jeder Änderung, solange der Dienst läuft.
        scope.launch {
            settingsRepository.settings.map { it.stepThreshold }.distinctUntilChanged().collect { tracker.threshold = it }
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
        startRequested = false
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

        /**
         * Prozesslokal: Start angefordert und seitdem nicht beendet. Verhindert, dass jeder Abgleich
         * (z. B. bei jedem Öffnen der App) einen weiteren onStartCommand auslöst, der auf dem Main-Thread
         * die Foreground-Notification neu baut und neu postet. Im Zweifel `false` – ein überzähliger
         * Start ist harmlos, ein verpasster nicht.
         */
        @Volatile
        private var startRequested = false

        /** Läuft die Schritterkennung jetzt sinnvollerweise? */
        fun shouldRun(context: Context, settings: ReminderSettings, nowMillis: Long, zone: ZoneId): Boolean =
            settings.wantsStepTracking(nowMillis, zone) &&
                Permissions.hasActivityRecognition(context) &&
                StepTracker.isAvailable(context)

        fun start(context: Context) {
            if (startRequested) return
            // Vor dem Aufruf setzen: Bricht der Dienst seinen Start ab (stopSelf), setzt er das Flag
            // danach zuverlässig wieder zurück – es kann also nicht fälschlich auf `true` hängen bleiben.
            startRequested = true
            val intent = Intent(context, StepTrackerService::class.java).setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException: Start aus dem Hintergrund nicht erlaubt.
                startRequested = false
                Log.w(TAG, "Schritterkennung konnte nicht gestartet werden", e)
            } catch (e: SecurityException) {
                startRequested = false
                Log.w(TAG, "Schritterkennung konnte nicht gestartet werden", e)
            }
        }

        fun stop(context: Context) {
            startRequested = false
            context.stopService(Intent(context, StepTrackerService::class.java))
        }
    }
}

/** [StepTracking] über den [StepTrackerService]. */
class ServiceStepTracking(private val context: Context) : StepTracking {
    override fun shouldRun(settings: ReminderSettings, nowMillis: Long, zone: ZoneId): Boolean =
        StepTrackerService.shouldRun(context, settings, nowMillis, zone)

    override fun start() = StepTrackerService.start(context)

    override fun stop() = StepTrackerService.stop(context)
}
