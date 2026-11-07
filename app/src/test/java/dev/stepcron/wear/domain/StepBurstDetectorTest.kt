package dev.stepcron.wear.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepBurstDetectorTest {

    private val second = 1_000_000_000L

    @Test
    fun `thirty steps within two minutes are detected`() {
        val detector = StepBurstDetector()
        var t = 0L
        repeat(29) {
            assertFalse(detector.onStep(t))
            t += second / 2
        }
        assertTrue(detector.onStep(t))
    }

    @Test
    fun `slow steps spread over more than two minutes are ignored`() {
        val detector = StepBurstDetector()
        // Ein Schritt alle 5 s → höchstens 25 Schritte im 2-Minuten-Fenster.
        for (i in 0 until 60) {
            assertFalse(detector.onStep(i * 5 * second))
        }
    }

    @Test
    fun `cooldown suppresses immediate re-detection while walking`() {
        val detector = StepBurstDetector()
        var t = 0L
        var detections = 0
        // 100 s zügiges Gehen (2 Schritte/s): Erkennung nach 15 s, dann erst wieder nach 60 s Pause.
        repeat(200) {
            if (detector.onStep(t)) detections++
            t += second / 2
        }
        assertTrue("erwartet: genau zwei Erkennungen, war $detections", detections == 2)
    }

    @Test
    fun `step counter delta within the window is detected`() {
        val detector = StepBurstDetector()
        assertFalse(detector.onCounter(0L, 1_000))
        assertTrue(detector.onCounter(60 * second, 1_035))
    }

    @Test
    fun `step counter delta spread over a long time is scaled down`() {
        val detector = StepBurstDetector()
        assertFalse(detector.onCounter(0L, 1_000))
        // 100 Schritte über 10 Minuten ≈ 20 Schritte pro 2 Minuten.
        assertFalse(detector.onCounter(600 * second, 1_100))
    }

    @Test
    fun `step counter reset after reboot is treated as new baseline`() {
        val detector = StepBurstDetector()
        assertFalse(detector.onCounter(0L, 5_000))
        assertFalse(detector.onCounter(10 * second, 12))
        assertTrue(detector.onCounter(40 * second, 50))
    }
}
