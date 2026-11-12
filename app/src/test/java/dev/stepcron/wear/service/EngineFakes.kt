package dev.stepcron.wear.service

import dev.stepcron.wear.data.MovementLog
import dev.stepcron.wear.data.db.MovementEventType
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.SkipReason
import dev.stepcron.wear.data.settings.VibrationLevel
import dev.stepcron.wear.domain.PlannedAlarm
import java.time.ZoneId

// Attrappen der Engine-Schnittstellen (EnginePorts, MovementLog) für alle Tests, die eine echte ReminderEngine bauen.

internal class FakeAlarms : Alarms {
    var planned: PlannedAlarm? = null

    override fun schedule(alarm: PlannedAlarm): Boolean {
        planned = alarm
        return true
    }

    override fun cancel() {
        planned = null
    }
}

internal class FakeAlerts : ReminderAlerts {
    /** Zeitpunkt der gerade sichtbaren Erinnerung, `null` wenn keine angezeigt wird. */
    var shownFor: Long? = null
    var shownSnoozeMinutes: Int? = null
    val vibrationLevels = mutableListOf<VibrationLevel>()
    val vibrations: Int get() = vibrationLevels.size

    override fun showReminder(reminderAtMillis: Long, snoozeMinutes: Int) {
        shownFor = reminderAtMillis
        shownSnoozeMinutes = snoozeMinutes
    }

    override fun vibrateReminder(level: VibrationLevel) {
        vibrationLevels += level
    }

    override fun cancelReminder() {
        shownFor = null
    }
}

internal class FakeStepTracking : StepTracking {
    var permitted = true
    var running = false

    override fun shouldRun(settings: ReminderSettings, nowMillis: Long, zone: ZoneId): Boolean =
        permitted && settings.wantsStepTracking(nowMillis, zone)

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
    }
}

internal class FakeDeviceState : DeviceState {
    var quietMode: SkipReason? = null
    var worn: Boolean? = true

    override fun quietMode(): SkipReason? = quietMode

    override suspend fun isWorn(): Boolean? = worn
}

internal class FakeMovementLog : MovementLog {
    val events = mutableListOf<Pair<MovementEventType, Long>>()

    override suspend fun log(type: MovementEventType, timestampMillis: Long, intervalMinutes: Int): Long {
        events += type to timestampMillis
        return events.size.toLong()
    }
}

internal class FakeLicenseCheck : LicenseCheck {
    /** Testphase oder freigeschaltet. */
    var active = true

    override suspend fun isActive(nowMillis: Long): Boolean = active
}
