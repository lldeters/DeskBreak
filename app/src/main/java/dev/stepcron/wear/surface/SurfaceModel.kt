package dev.stepcron.wear.surface

import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.ReminderState
import dev.stepcron.wear.ui.HomeStatus
import dev.stepcron.wear.ui.HomeUiState
import dev.stepcron.wear.ui.SystemStatus
import java.time.ZoneId

/**
 * Was Kachel und Komplikation anzeigen. Abgeleitet aus denselben Regeln wie der Status auf dem
 * Home-Bildschirm ([HomeUiState]), ohne Android-Abhängigkeiten und daher unit-testbar.
 */
sealed interface SurfaceModel {

    /** Eine Erinnerung wurde noch nicht beantwortet. */
    val reminderPending: Boolean get() = false

    /** Erinnerungen sind ausgeschaltet. */
    data object Off : SurfaceModel

    /** Erinnerungen sind an, aber im Zeitplan ist kein Tag aktiv. */
    data object NoActiveDays : SurfaceModel

    /** Kurzzeitig, während der nächste Alarm geplant wird. */
    data object Planning : SurfaceModel

    /** Außerhalb der aktiven Zeit; weiter um [resumeAtMillis] (in [daysUntilResume] Tagen). */
    data class OffHours(
        val resumeAtMillis: Long,
        val daysUntilResume: Int,
        val freeToday: Boolean,
        override val reminderPending: Boolean,
    ) : SurfaceModel

    /** Vom Nutzer pausiert (Schnellpause oder Urlaub); weiter um [resumeAtMillis] (in [daysUntilResume] Tagen). */
    data class PausedByUser(
        val resumeAtMillis: Long,
        val daysUntilResume: Int,
    ) : SurfaceModel

    /** Nächste Erinnerung um [reminderAtMillis]; das laufende Intervall begann um [cycleStartMillis]. */
    data class Countdown(
        val reminderAtMillis: Long,
        val cycleStartMillis: Long,
        val snoozed: Boolean,
        override val reminderPending: Boolean,
    ) : SurfaceModel

    /** Heute keine weitere Erinnerung mehr; die aktive Zeit endet um [windowEndMillis]. */
    data class DayDone(
        val windowEndMillis: Long,
        override val reminderPending: Boolean,
    ) : SurfaceModel

    companion object {
        fun create(settings: ReminderSettings, state: ReminderState, nowMillis: Long, zone: ZoneId): SurfaceModel {
            // Berechtigungen spielen für die Anzeige hier keine Rolle.
            val home = HomeUiState.create(settings, state, nowMillis, SystemStatus(), zone)
            val pending = home.reminderPending
            return when (val status = home.status) {
                HomeStatus.Disabled -> Off
                HomeStatus.NoActiveDays -> NoActiveDays
                HomeStatus.Planning -> Planning
                is HomeStatus.OffHours -> OffHours(status.resumeAtMillis, status.daysUntilResume, status.freeToday, pending)
                is HomeStatus.PausedByUser -> PausedByUser(status.resumeAtMillis, status.daysUntilResume)
                is HomeStatus.Scheduled -> Countdown(
                    reminderAtMillis = status.atMillis,
                    // Ein unplausibler Zyklusbeginn (0 oder nach der Erinnerung) zählt als „gerade begonnen".
                    cycleStartMillis = state.cycleStartAt.takeIf { it in 1..status.atMillis } ?: nowMillis,
                    snoozed = status.snoozed,
                    reminderPending = pending,
                )
                is HomeStatus.DayDone -> DayDone(status.windowEndMillis, pending)
            }
        }
    }
}

/** Fortschritt des laufenden Intervalls (0 = gerade begonnen, 1 = Erinnerung fällig). */
internal fun SurfaceModel.Countdown.progressAt(nowMillis: Long): Float {
    val total = (reminderAtMillis - cycleStartMillis).coerceAtLeast(1L)
    return ((nowMillis - cycleStartMillis).toFloat() / total).coerceIn(0f, 1f)
}

/** Verbleibende Minuten bis zur Erinnerung, aufgerundet (0 = jetzt fällig). */
internal fun SurfaceModel.Countdown.remainingMinutesAt(nowMillis: Long): Long =
    ((reminderAtMillis - nowMillis).coerceAtLeast(0L) + 59_999L) / 60_000L
