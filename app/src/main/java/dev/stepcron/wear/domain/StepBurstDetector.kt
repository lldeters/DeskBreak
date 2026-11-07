package dev.stepcron.wear.domain

/**
 * Erkennt eigenständige Gehpausen: mindestens [threshold] Schritte innerhalb von [windowNanos].
 *
 * Zeitstempel sind `SensorEvent.timestamp`-Werte (Nanosekunden, monoton). Da die Sensoren gebatcht
 * liefern, zählt der Zeitpunkt des Schritts – nicht der Zeitpunkt der Zustellung.
 * Nach einer Erkennung ist der Detektor für [cooldownNanos] stumm, damit Dauergehen nicht
 * im Sekundentakt Ereignisse auslöst.
 */
class StepBurstDetector(
    private val threshold: Int = DEFAULT_THRESHOLD,
    private val windowNanos: Long = DEFAULT_WINDOW_NANOS,
    private val cooldownNanos: Long = DEFAULT_COOLDOWN_NANOS,
) {
    private val stepTimes = ArrayDeque<Long>()
    private var lastCounterTotal = -1L
    private var lastCounterTimestamp = 0L
    private var cooldownUntilNanos = Long.MIN_VALUE

    /** Ein einzelner Schritt von `TYPE_STEP_DETECTOR`. @return `true` bei erkannter Gehpause. */
    fun onStep(timestampNanos: Long): Boolean = addSteps(timestampNanos, 1)

    /** Kumulativer Zählerstand von `TYPE_STEP_COUNTER` (Fallback). @return `true` bei erkannter Gehpause. */
    fun onCounter(timestampNanos: Long, totalSteps: Long): Boolean {
        val previousTotal = lastCounterTotal
        val previousTimestamp = lastCounterTimestamp
        lastCounterTotal = totalSteps
        lastCounterTimestamp = timestampNanos
        // Erster Messwert oder Zähler-Reset (Neustart): nur als neue Basis merken.
        if (previousTotal < 0 || totalSteps <= previousTotal) return false

        val delta = totalSteps - previousTotal
        val elapsed = timestampNanos - previousTimestamp
        // Liegt der letzte Messwert länger zurück, wird das Delta anteilig auf das Fenster umgelegt.
        val stepsInWindow = if (elapsed <= windowNanos) {
            delta
        } else {
            (delta.toDouble() * windowNanos / elapsed).toLong()
        }
        if (stepsInWindow <= 0) return false
        return addSteps(timestampNanos, stepsInWindow.coerceAtMost(threshold.toLong()).toInt())
    }

    fun reset() {
        stepTimes.clear()
        lastCounterTotal = -1L
        lastCounterTimestamp = 0L
        cooldownUntilNanos = Long.MIN_VALUE
    }

    private fun addSteps(timestampNanos: Long, count: Int): Boolean {
        repeat(count) { stepTimes.addLast(timestampNanos) }
        while (stepTimes.isNotEmpty() && timestampNanos - stepTimes.first() > windowNanos) {
            stepTimes.removeFirst()
        }
        while (stepTimes.size > threshold) stepTimes.removeFirst()

        if (timestampNanos < cooldownUntilNanos) return false
        if (stepTimes.size >= threshold) {
            stepTimes.clear()
            cooldownUntilNanos = timestampNanos + cooldownNanos
            return true
        }
        return false
    }

    companion object {
        const val DEFAULT_THRESHOLD = 30
        const val DEFAULT_WINDOW_NANOS = 120_000_000_000L // 2 Minuten
        const val DEFAULT_COOLDOWN_NANOS = 60_000_000_000L // 1 Minute
    }
}
