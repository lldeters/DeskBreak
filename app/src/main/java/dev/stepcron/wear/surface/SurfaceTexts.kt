package dev.stepcron.wear.surface

import android.content.Context
import dev.stepcron.wear.R
import dev.stepcron.wear.ui.formatClock
import dev.stepcron.wear.ui.resumeText
import java.time.ZoneId

/** Texte für Kachel und Komplikation (aus den String-Ressourcen). */
internal class SurfaceTexts(private val context: Context, private val zone: ZoneId = ZoneId.systemDefault()) {

    /** Kurzer Hauptwert, z. B. „23 min", „Frei" oder „Aus". */
    fun headline(model: SurfaceModel, nowMillis: Long): String = when (model) {
        is SurfaceModel.Countdown -> {
            val minutes = model.remainingMinutesAt(nowMillis)
            if (minutes <= 0L) {
                context.getString(R.string.surface_now)
            } else {
                context.getString(R.string.surface_minutes, minutes.toInt())
            }
        }

        is SurfaceModel.OffHours ->
            context.getString(if (model.freeToday) R.string.surface_free else R.string.surface_rest)

        is SurfaceModel.PausedByUser -> context.getString(R.string.surface_paused)

        is SurfaceModel.DayDone -> context.getString(R.string.surface_done)
        SurfaceModel.Off -> context.getString(R.string.surface_off)
        SurfaceModel.NoActiveDays -> context.getString(R.string.surface_no_days)
        SurfaceModel.Planning -> context.getString(R.string.surface_planning)
    }

    /** Erklärende Zeile, z. B. „Pause um 10:45" oder „Weiter am Montag um 08:00". */
    fun caption(model: SurfaceModel): String {
        if (model.reminderPending) return context.getString(R.string.surface_pending)
        return when (model) {
            is SurfaceModel.Countdown -> context.getString(
                if (model.snoozed) R.string.surface_snoozed_until else R.string.surface_next_at,
                formatClock(model.reminderAtMillis, zone),
            )

            is SurfaceModel.OffHours -> resumeText(context.resources, model.resumeAtMillis, model.daysUntilResume, zone)
            is SurfaceModel.PausedByUser -> resumeText(context.resources, model.resumeAtMillis, model.daysUntilResume, zone)
            is SurfaceModel.DayDone ->
                context.getString(R.string.home_status_day_done_hint, formatClock(model.windowEndMillis, zone))

            SurfaceModel.Off -> context.getString(R.string.surface_off_caption)
            SurfaceModel.NoActiveDays -> context.getString(R.string.surface_no_days_caption)
            SurfaceModel.Planning -> context.getString(R.string.home_status_planning)
        }
    }
}
