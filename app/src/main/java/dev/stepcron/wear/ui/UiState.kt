package dev.stepcron.wear.ui

import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.wear.data.db.MovementEvent
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.ReminderState
import dev.stepcron.wear.data.settings.SkipReason
import dev.stepcron.wear.data.settings.VibrationLevel
import dev.stepcron.wear.domain.AlarmKind
import dev.stepcron.wear.domain.DailyCounts
import dev.stepcron.wear.domain.DaySchedule
import dev.stepcron.wear.domain.DayStat
import dev.stepcron.wear.domain.HourSlot
import dev.stepcron.wear.domain.HourlyCounts
import dev.stepcron.wear.domain.StatsCalculator
import dev.stepcron.wear.domain.TimeOfDayInsight
import dev.stepcron.wear.domain.WeeklySchedule
import dev.stepcron.wear.domain.toLocalDate
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

    /** Erinnerungen sind an, aber die Testphase ist vorbei und nicht freigeschaltet: Sie ruhen bis zur Freischaltung. */
    data object Locked : HomeStatus

    /** Erinnerungen sind an, aber im Zeitplan ist kein Tag aktiv. */
    data object NoActiveDays : HomeStatus
    data object Planning : HomeStatus

    /**
     * Außerhalb der aktiven Zeit.
     * @property daysUntilResume Kalendertage bis zum nächsten Fensterbeginn (0 = heute, 1 = morgen, …).
     * @property freeToday Heute ist laut Zeitplan frei.
     */
    data class OffHours(
        val resumeAtMillis: Long,
        val daysUntilResume: Int,
        val freeToday: Boolean,
    ) : HomeStatus

    /**
     * Vom Nutzer pausiert (Schnellpause oder Urlaub).
     * @property resumeAtMillis Erster aktiver Moment nach der Pause, dort beginnt ein frischer Zyklus.
     * @property daysUntilResume Kalendertage bis dahin (0 = heute, 1 = morgen, …).
     */
    data class PausedByUser(
        val resumeAtMillis: Long,
        val daysUntilResume: Int,
    ) : HomeStatus
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
    val schedule: WeeklySchedule = WeeklySchedule.DEFAULT,
    /** Einstellung des heutigen Wochentags (an/frei und Zeitfenster). */
    val today: DaySchedule = WeeklySchedule.DEFAULT.days.first(),
    val autoStepEnabled: Boolean = true,
    val snoozeMinutes: Int = ReminderSettings.DEFAULT_SNOOZE_MINUTES,
    val stepThreshold: Int = ReminderSettings.DEFAULT_STEP_THRESHOLD,
    val vibration: VibrationLevel = VibrationLevel.STRONG,
    val respectQuietModes: Boolean = true,
    val onlyWhenWorn: Boolean = true,
    /** Die letzte Erinnerung fiel still aus (Ruhemodus, abgelegt); seitdem läuft dieser Zyklus. */
    val skippedReason: SkipReason? = null,
    val dailyGoal: Int = ReminderSettings.DEFAULT_DAILY_GOAL,
    /** Heutige Bewegungspausen; `null`, bis die Historie geladen ist. */
    val todayMovements: Int? = null,
    val permissionsRequested: Boolean = false,
    val status: HomeStatus = HomeStatus.Disabled,
    val reminderPending: Boolean = false,
    val stepTrackingActive: Boolean = false,
    val system: SystemStatus = SystemStatus(),
    /** Stand der Lizenz; `null`, solange er nicht bekannt ist (zählt dann wie aktiv). */
    val license: LicenseStatus? = null,
) {
    val canDecreaseGoal: Boolean get() = dailyGoal > ReminderSettings.MIN_DAILY_GOAL
    val canIncreaseGoal: Boolean get() = dailyGoal < ReminderSettings.MAX_DAILY_GOAL

    val canDecreaseInterval: Boolean get() = intervalMinutes > ReminderSettings.MIN_INTERVAL_MINUTES
    val canIncreaseInterval: Boolean get() = intervalMinutes < ReminderSettings.MAX_INTERVAL_MINUTES

    val needsPermissions: Boolean
        get() = !system.notificationsGranted || (autoStepEnabled && !system.activityRecognitionGranted)

    /**
     * Exakte Alarme fehlen (ab Android 14 bei neuen Installationen der Normalfall). Ohne sie kommen Erinnerungen
     * verspätet, und die Schritterkennung startet zu Beginn der aktiven Zeit nicht im Hintergrund – deshalb oben auf
     * dem Startbildschirm, auch bei ausgeschalteten Erinnerungen (so springt beim Einschalten nichts). Nach der
     * Testphase ohne Freischaltung ruhen die Erinnerungen ohnehin: dann nicht.
     */
    val needsExactAlarms: Boolean
        get() = !loading && !system.exactAlarmsAllowed && !licenseExpired

    /** Testphase vorbei, nicht freigeschaltet. */
    val licenseExpired: Boolean get() = license is LicenseStatus.Expired

    companion object {
        fun create(
            settings: ReminderSettings,
            state: ReminderState,
            nowMillis: Long,
            system: SystemStatus,
            zone: ZoneId,
            todayMovements: Int? = null,
            license: LicenseStatus? = null,
        ) = HomeUiState(
            loading = false,
            enabled = settings.enabled,
            intervalMinutes = settings.intervalMinutes,
            schedule = settings.schedule,
            today = settings.schedule[nowMillis.toLocalDate(zone).dayOfWeek],
            autoStepEnabled = settings.autoStepEnabled,
            snoozeMinutes = settings.snoozeMinutes,
            stepThreshold = settings.stepThreshold,
            vibration = settings.vibration,
            respectQuietModes = settings.respectQuietModes,
            onlyWhenWorn = settings.onlyWhenWorn,
            skippedReason = state.lastSkipReason.takeIf {
                settings.enabled && state.lastSkippedAt > 0L && state.lastSkippedAt == state.cycleStartAt
            },
            dailyGoal = settings.dailyGoal,
            todayMovements = todayMovements,
            permissionsRequested = settings.permissionsRequested,
            status = homeStatus(settings, state, nowMillis, zone, licensed = license?.isActive != false),
            reminderPending = settings.enabled && license?.isActive != false && state.pendingReminderAt > 0L,
            stepTrackingActive = state.stepTrackingActive,
            system = system,
            license = license,
        )

        private fun homeStatus(
            settings: ReminderSettings,
            state: ReminderState,
            nowMillis: Long,
            zone: ZoneId,
            licensed: Boolean,
        ): HomeStatus {
            if (!settings.enabled) return HomeStatus.Disabled
            if (!licensed) return HomeStatus.Locked
            val schedule = settings.schedule
            val kind = state.nextAlarmKind
            return when {
                !schedule.hasActiveDay -> HomeStatus.NoActiveDays

                settings.isPausedAt(nowMillis) -> {
                    val resumeAt = schedule.nextActiveAt(settings.pausedUntil, zone) ?: return HomeStatus.NoActiveDays
                    HomeStatus.PausedByUser(resumeAt, daysBetween(nowMillis, resumeAt, zone))
                }

                !schedule.contains(nowMillis, zone) -> {
                    // Aus dem Zeitplan statt aus dem gespeicherten Alarm: Nach einer Änderung ist der Plan sofort
                    // aktuell, der Alarm erst nach dem Abgleich.
                    val resumeAt = schedule.nextStart(nowMillis, zone) ?: return HomeStatus.NoActiveDays
                    HomeStatus.OffHours(
                        resumeAtMillis = resumeAt,
                        daysUntilResume = daysBetween(nowMillis, resumeAt, zone),
                        freeToday = schedule.windowAt(nowMillis, zone) == null,
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

        /** Kalendertage von [fromMillis] bis [toMillis] (0 = derselbe Tag). */
        private fun daysBetween(fromMillis: Long, toMillis: Long, zone: ZoneId): Int =
            ChronoUnit.DAYS.between(fromMillis.toLocalDate(zone), toMillis.toLocalDate(zone)).toInt()
    }
}

data class StatsUiState(
    val loading: Boolean = true,
    val todayPercent: Int? = null,
    val todayMovements: Int = 0,
    val todaySnoozes: Int = 0,
    val todayMissed: Int = 0,
    val streak: Int = 0,
    val hourSlots: List<HourSlot> = emptyList(),
    val week: List<DayStat> = emptyList(),
    val weekPercent: Int? = null,
    val weekMovements: Int = 0,
    val dailyGoal: Int = ReminderSettings.DEFAULT_DAILY_GOAL,
    /** Monatsansicht: die letzten Kalenderwochen, je Montag bis Sonntag; künftige Tage sind `null`. */
    val calendarWeeks: List<List<DayStat?>> = emptyList(),
    /** Tage der Monatsansicht mit erreichtem Tagesziel bzw. mit Daten überhaupt. */
    val goalDays: Int = 0,
    val daysWithData: Int = 0,
    /** Beste und schwächste Tageszeit der letzten 30 Tage; `null` bei zu wenigen Daten. */
    val timeOfDay: TimeOfDayInsight? = null,
) {
    val goalReached: Boolean get() = todayMovements >= dailyGoal

    companion object {
        fun create(
            todayEvents: List<MovementEvent>,
            dailyCounts: List<DailyCounts>,
            hourlyCounts: List<HourlyCounts>,
            streak: Int,
            settings: ReminderSettings,
            nowMillis: Long,
            today: LocalDate,
            zone: ZoneId,
        ): StatsUiState {
            val summary = StatsCalculator.summarize(todayEvents)
            val week = StatsCalculator.lastSevenDays(dailyCounts, today)
            val calendar = StatsCalculator.calendarWeeks(dailyCounts, today)
            val calendarDays = calendar.flatten().filterNotNull()
            val weekMovements = week.sumOf { it.movements }
            val weekMissed = week.sumOf { it.missed }
            return StatsUiState(
                loading = false,
                todayPercent = summary.percent,
                todayMovements = summary.movements,
                todaySnoozes = summary.snoozes,
                todayMissed = summary.missed,
                streak = streak,
                // Auch an einem freien Tag: dessen gespeichertes Fenster, damit trotzdem erfasste Pausen erscheinen.
                hourSlots = StatsCalculator.hourSlots(todayEvents, settings.schedule[today.dayOfWeek].window, nowMillis, zone),
                week = week,
                weekPercent = StatsCalculator.regularityPercent(weekMovements, weekMissed),
                weekMovements = weekMovements,
                dailyGoal = settings.dailyGoal,
                calendarWeeks = calendar,
                goalDays = calendarDays.count { it.movements >= settings.dailyGoal },
                daysWithData = calendarDays.count { it.movements + it.missed > 0 },
                timeOfDay = StatsCalculator.timeOfDay(hourlyCounts),
            )
        }
    }
}
