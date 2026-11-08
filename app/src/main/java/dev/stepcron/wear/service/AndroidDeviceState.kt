package dev.stepcron.wear.service

import android.app.NotificationManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import android.util.Log
import dev.stepcron.wear.data.settings.SkipReason
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * [DeviceState] der Uhr. „Nicht stören" kommt über die öffentliche API des NotificationManagers. Schlafenszeit
 * und Kinomodus haben keine öffentliche API: Hier werden die Systemschalter gelesen, soweit das System das
 * erlaubt – sonst gelten sie als aus (die Schlafenszeit schaltet auf den meisten Uhren ohnehin „Nicht stören" ein).
 * Das Tragen meldet der Off-Body-Sensor.
 */
class AndroidDeviceState(private val context: Context) : DeviceState {

    override fun quietMode(): SkipReason? = when {
        globalFlag(BEDTIME_MODE) -> SkipReason.BEDTIME
        globalFlag(THEATER_MODE) -> SkipReason.THEATER_MODE
        doNotDisturb() -> SkipReason.DO_NOT_DISTURB
        else -> null
    }

    private fun doNotDisturb(): Boolean {
        val filter = context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter ?: return false
        // Nur „Alle" lässt die Erinnerung durch; „Wichtige", „Wecker" und „Keine" sind Ruhe.
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    private fun globalFlag(name: String): Boolean = try {
        Settings.Global.getInt(context.contentResolver, name, 0) == 1
    } catch (e: SecurityException) {
        // Ab Android 12 sind versteckte Schlüssel für Apps teils nicht lesbar.
        Log.d(TAG, "Systemschalter $name nicht lesbar", e)
        false
    } catch (e: IllegalArgumentException) {
        false
    }

    /**
     * Der Off-Body-Sensor liefert nach dem Anmelden sofort seinen Zustand (1 = getragen, 0 = abgelegt).
     * Ohne Sensor oder Antwort nach [OFF_BODY_TIMEOUT_MILLIS] ist das Tragen unbekannt.
     */
    override suspend fun isWorn(): Boolean? {
        val sensorManager = context.getSystemService(SensorManager::class.java) ?: return null
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)
            ?: return null
        return withTimeoutOrNull(OFF_BODY_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<Boolean?> { continuation ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sensorManager.unregisterListener(this)
                        if (continuation.isActive) continuation.resume(event.values.firstOrNull()?.let { it != 0f })
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                continuation.invokeOnCancellation { sensorManager.unregisterListener(listener) }
                if (!sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
                    continuation.resume(null)
                }
            }
        }
    }

    private companion object {
        const val TAG = "DeviceState"

        /** Settings.Global.Wearable.BEDTIME_MODE bzw. Settings.Global.THEATER_MODE_ON (beide nicht öffentlich). */
        const val BEDTIME_MODE = "bedtime_mode"
        const val THEATER_MODE = "theater_mode_on"

        const val OFF_BODY_TIMEOUT_MILLIS = 2_000L
    }
}
