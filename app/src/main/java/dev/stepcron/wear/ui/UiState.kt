package dev.stepcron.wear.ui

import dev.stepcron.wear.data.db.MovementEvent
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.ReminderState
import dev.stepcron.wear.domain.AlarmKind
import dev.stepcron.wear.domain.DailyCounts
import dev.stepcron.wear.domain.DayStat
import dev.stepcron.wear.domain.HourSlot
import dev.stepcron.wear.domain.StatsCalculator
import dev.stepcron.wear.domain.toLocalDate
import java.time.LocalDate
import java.time.ZoneId

/** Berechtigungen & Geräte-Fähigkeiten, die die UI anzeigen muss. */
data class SystemStatus(
    val notificationsGranted: Boolean = true,
    val activityRecognitionGranted: Boolean = true,
    val exactAlarmsAllowed: Boolean = true,
    val stepSensorAvailable: Boolean = true,
)

/** Was der Status-Header auf dem Hauptbildschirm zeigt. */
sealed interface HomeStatus {
    data object Disabled : HomeStatus
    data object Planning : HomeStatus
    data class Paused(val resumeAtMillis: Long, val resumesTomorrow: Boolean) : HomeStatus
    data class Scheduled(
        val atMillis: Long,
        val remainingMinutes: Long,
        val progress: Float,
        val snoozed: Boolean,
    ) : HomeStatus
    data class DayDone(val windowEndMillis: Long) : HomeStatus
}

data class HomeUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val intervalMinutes: Int = ReminderSettings.DEFAULT_INTERVAL_MINUTES,
    val startHour: Int = ReminderSettings.DEFAULT_START_HOUR,
    val endHour: Int = ReminderSettings.DEFAULT_END_HOUR,
    val autoStepEnabled: Boolean = true,
    val permissionsRequested: Boolean = false,
    val status: HomeStatus = HomeStatus.Disabled,
    val reminderPending: Boolean = false,
    val stepTrackingActive: Boolean = false,
    val system: SystemStatus = SystemStatus(),
) {
    val canDecreaseInterval: Boolean get() = intervalMinutes > ReminderSettings.MIN_INTERVAL_MINUTES
    val canIncreaseInterval: Boolean get() = intervalMinutes < ReminderSettings.MAX_INTERVAL_MINUTES
    val canDecreaseStart: Boolean get() = startHour > 0
    val canIncreaseStart: Boolean get() = startHour < endHour - 1
    val canDecreaseEnd: Boolean get() = endHour > startHour + 1
    val canIncreaseEnd: Boolean get() = endHour < 24

    val needsPermissions: Boolean
        get() = !system.notificationsGranted || (autoStepEnabled && !system.activityRecognitionGranted)

    companion object {
        fun create(
            settings: ReminderSettings,
            state: ReminderState,
            nowMillis: Long,
            system: SystemStatus,
            zone: ZoneId,
        ) = HomeUiState(
            loading = false,
            enabled = settings.enabled,
            intervalMinutes = settings.intervalMinutes,
            startHour = settings.activeStartHour,
            endHour = settings.activeEndHour,
            autoStepEnabled = settings.autoStepEnabled,
            permissionsRequested = settings.permissionsRequested,
            status = homeStatus(settings, state, nowMillis, zone),
            reminderPending = settings.enabled && state.pendingReminderAt > 0L,
            stepTrackingActive = state.stepTrackingActive,
            system = system,
        )

        private fun homeStatus(
            settings: ReminderSettings,
            state: ReminderState,
            nowMillis: Long,
            zone: ZoneId,
        ): HomeStatus {
            if (!settings.enabled) return HomeStatus.Disabled
            val window = settings.window
            val kind = state.nextAlarmKind
            return when {
                kind == AlarmKind.CYCLE_START || !window.contains(nowMillis, zone) -> {
                    val resumeAt = if (kind == AlarmKind.CYCLE_START && state.nextAlarmAt > nowMillis) {
                        state.nextAlarmAt
                    } else {
                        window.nextStart(nowMillis, zone)
                    }
                    HomeStatus.Paused(
                        resumeAtMillis = resumeAt,
                        resumesTomorrow = resumeAt.toLocalDate(zone) != nowMillis.toLocalDate(zone),
                    )
                }

                kind == AlarmKind.WINDOW_END -> HomeStatus.DayDone(state.nextAlarmAt)

                kind == AlarmKind.REMINDER && state.nextAlarmAt > 0L -> {
                    val remainingMillis = (state.nextAlarmAt - nowMillis).coerceAtLeast(0L)
                    val total = (state.nextAlarmAt - state.cycleStartAt).coerceAtLeast(1L)
                    HomeStatus.Scheduled(
                        atMillis = state.nextAlarmAt,
                        remainingMinutes = (remainingMillis + 59_999L) / 60_000L,
                        progress = ((nowMillis - state.cycleStartAt).toFloat() / total).coerceIn(0f, 1f),
                        snoozed = state.snoozeUntil > 0L && state.snoozeUntil == state.nextAlarmAt,
                    )
                }

                else -> HomeStatus.Planning
            }
        }
    }
}

data class StatsUiState(
    val loading: Boolean = true,
    val todayPercent: Int? = null,
    val todayMovements: Int = 0,
    val todaySnoozes: Int = 0,
    val todayMissed: Int = 0,
    val streak: Int = 0,
    val startHour: Int = ReminderSettings.DEFAULT_START_HOUR,
    val endHour: Int = ReminderSettings.DEFAULT_END_HOUR,
    val hourSlots: List<HourSlot> = emptyList(),
    val week: List<DayStat> = emptyList(),
    val weekPercent: Int? = null,
    val weekMovements: Int = 0,
) {
    companion object {
        fun create(
            todayEvents: List<MovementEvent>,
            dailyCounts: List<DailyCounts>,
            streak: Int,
            settings: ReminderSettings,
            nowMillis: Long,
            today: LocalDate,
            zone: ZoneId,
        ): StatsUiState {
            val summary = StatsCalculator.summarize(todayEvents)
            val week = StatsCalculator.lastSevenDays(dailyCounts, today)
            val weekMovements = week.sumOf { it.movements }
            val weekMissed = week.sumOf { it.missed }
            return StatsUiState(
                loading = false,
                todayPercent = summary.percent,
                todayMovements = summary.movements,
                todaySnoozes = summary.snoozes,
                todayMissed = summary.missed,
                streak = streak,
                startHour = settings.activeStartHour,
                endHour = settings.activeEndHour,
                hourSlots = StatsCalculator.hourSlots(todayEvents, settings.window, nowMillis, zone),
                week = week,
                weekPercent = StatsCalculator.regularityPercent(weekMovements, weekMissed),
                weekMovements = weekMovements,
            )
        }
    }
}
