package dev.stepcron.wear.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import dev.stepcron.wear.domain.StepBurstDetector

/**
 * Leichtgewichtiger Sensor-Listener für die Schritterkennung.
 *
 * Bevorzugt `TYPE_STEP_DETECTOR` (ein Event pro Schritt) als Wake-up-Sensor mit Batching: Die CPU
 * schläft, solange man sitzt, und wird beim Gehen höchstens alle [MAX_REPORT_LATENCY_US] geweckt.
 * Fallback ist `TYPE_STEP_COUNTER`.
 */
class StepTracker(
    private val sensorManager: SensorManager,
    private val detector: StepBurstDetector = StepBurstDetector(),
    private val onWalkDetected: () -> Unit,
) : SensorEventListener {

    private var registeredSensor: Sensor? = null

    val isRunning: Boolean get() = registeredSensor != null

    /** @return `false`, wenn kein Schrittsensor vorhanden ist oder die Registrierung scheitert. */
    fun start(): Boolean {
        if (registeredSensor != null) return true
        val sensor = findStepSensor(sensorManager) ?: return false
        detector.reset()
        val registered = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            MAX_REPORT_LATENCY_US,
        )
        if (registered) {
            registeredSensor = sensor
            Log.i(TAG, "Schritterkennung aktiv: ${sensor.name} (wakeUp=${sensor.isWakeUpSensor})")
        }
        return registered
    }

    fun stop() {
        if (registeredSensor == null) return
        sensorManager.unregisterListener(this)
        registeredSensor = null
        detector.reset()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val walkDetected = when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> detector.onStep(event.timestamp)
            Sensor.TYPE_STEP_COUNTER -> detector.onCounter(event.timestamp, event.values[0].toLong())
            else -> false
        }
        if (walkDetected) onWalkDetected()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val TAG = "StepTracker"

        /** Batching-Latenz: Schritte werden gesammelt und spätestens nach 30 s zugestellt. */
        const val MAX_REPORT_LATENCY_US = 30_000_000

        fun findStepSensor(sensorManager: SensorManager): Sensor? =
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR, true)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER, true)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        fun isAvailable(context: Context): Boolean {
            val sensorManager = context.getSystemService(SensorManager::class.java) ?: return false
            return findStepSensor(sensorManager) != null
        }
    }
}
