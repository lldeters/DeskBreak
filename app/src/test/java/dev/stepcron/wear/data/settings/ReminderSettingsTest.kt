package dev.stepcron.wear.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSettingsTest {

    @Test
    fun `snooze and step threshold snap to the nearest option`() {
        val settings = ReminderSettings(snoozeMinutes = 12, stepThreshold = 1000).sanitized()
        assertEquals(10, settings.snoozeMinutes)
        assertEquals(100, settings.stepThreshold)
        assertEquals(5, ReminderSettings(snoozeMinutes = -3).sanitized().snoozeMinutes)
        assertEquals(20, ReminderSettings(stepThreshold = 0).sanitized().stepThreshold)
    }

    @Test
    fun `stepping through options stops at both ends`() {
        val options = ReminderSettings.SNOOZE_OPTIONS
        assertEquals(15, options.stepFrom(10, 1))
        assertEquals(5, options.stepFrom(10, -1))
        assertEquals(5, options.stepFrom(5, -1))
        assertEquals(30, options.stepFrom(30, 1))
        // Ein Wert zwischen den Optionen geht von der nächstgelegenen aus.
        assertEquals(30, options.stepFrom(22, 1))
    }

    @Test
    fun `vibration levels cycle`() {
        assertEquals(VibrationLevel.LIGHT, VibrationLevel.OFF.next())
        assertEquals(VibrationLevel.OFF, VibrationLevel.STRONG.next())
        assertEquals(VibrationLevel.MEDIUM, VibrationLevel.fromName("MEDIUM"))
        assertEquals(null, VibrationLevel.fromName("LOUD"))
    }
}
