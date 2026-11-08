package dev.stepcron.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.stepcron.wear.R
import dev.stepcron.wear.domain.ActiveWindow
import dev.stepcron.wear.domain.WeeklySchedule
import java.time.DayOfWeek

/**
 * Texte für Zeitplan-Anzeigen. Kommen in der App aus den String-Ressourcen ([rememberScheduleTexts]) und
 * können in Tests direkt angegeben werden.
 */
class ScheduleTexts(
    /** Wochentage kurz, Montag zuerst („Mo" … „So"). */
    val shortDayNames: List<String>,
    /** Wochentage lang, Montag zuerst („Montag" … „Sonntag"). */
    val longDayNames: List<String>,
    val daily: String,
    val noActiveDay: String,
    val varied: String,
    /** Tage und Uhrzeit, z. B. „%1$s · %2$s". */
    val summaryFormat: String,
    /** Fenster aus vollen Stunden, z. B. „%1$s–%2$s Uhr" mit „08" und „17". */
    val fullHoursFormat: String,
    /** Fenster mit Minuten, z. B. „%1$s–%2$s" mit „08:30" und „17:15". */
    val timesFormat: String,
) {
    fun shortName(day: DayOfWeek): String = shortDayNames.getOrElse(day.value - 1) { day.name }

    fun longName(day: DayOfWeek): String = longDayNames.getOrElse(day.value - 1) { day.name }

    /**
     * Zusammenfassung für den Home-Bildschirm, z. B. „Mo–Fr · 08–17 Uhr", „Täglich · 08–17 Uhr",
     * „Mo, Mi, Fr · 08–17 Uhr", „Mo–Fr · unterschiedlich" oder „Kein Tag aktiv".
     * Nur aktive Tage zählen; freie Tage dürfen andere Zeiten gespeichert haben.
     */
    fun summary(schedule: WeeklySchedule): String {
        val activeDays = schedule.activeDays
        if (activeDays.isEmpty()) return noActiveDay
        val days = if (activeDays.size == WeeklySchedule.DAYS_PER_WEEK) daily else dayRuns(activeDays)
        val windows = activeDays.map { schedule[it].window }.distinct()
        val hours = windows.singleOrNull()?.let(::window) ?: varied
        return summaryFormat.format(days, hours)
    }

    /**
     * Fasst aufeinanderfolgende Tage (innerhalb Mo–So, ohne Umlauf) ab drei Tagen als Bereich zusammen,
     * kürzere Folgen werden aufgezählt: „Mo–Fr", „Sa, So", „Mo–Mi, Fr", „Mo, Fr–So".
     */
    fun dayRuns(days: List<DayOfWeek>): String {
        val sorted = days.distinct().sortedBy { it.value }
        val runs = mutableListOf<MutableList<DayOfWeek>>()
        for (day in sorted) {
            val run = runs.lastOrNull()
            if (run != null && run.last().value + 1 == day.value) run += day else runs += mutableListOf(day)
        }
        return runs.joinToString(", ") { run ->
            if (run.size >= MIN_RANGE_LENGTH) {
                "${shortName(run.first())}–${shortName(run.last())}"
            } else {
                run.joinToString(", ") { shortName(it) }
            }
        }
    }

    /** Zeitfenster kompakt: „08–17 Uhr" bei vollen Stunden, sonst „08:30–17:15" (Mitternacht als 24). */
    fun window(window: ActiveWindow): String =
        if (window.startMinute % 60 == 0 && window.endMinute % 60 == 0) {
            fullHoursFormat.format("%02d".format(window.startMinute / 60), "%02d".format(window.endMinute / 60))
        } else {
            timesFormat.format(formatTimeOfDay(window.startMinute), formatTimeOfDay(window.endMinute))
        }

    private companion object {
        const val MIN_RANGE_LENGTH = 3
    }
}

/** Uhrzeit aus Minuten seit Mitternacht, z. B. „08:30"; 1440 wird zu „24:00". */
fun formatTimeOfDay(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

@Composable
fun rememberScheduleTexts(): ScheduleTexts {
    val context = LocalContext.current
    // Bei einer Sprach- bzw. Konfigurationsänderung neu laden.
    val configuration = LocalConfiguration.current
    return remember(context, configuration) {
        val resources = context.resources
        ScheduleTexts(
            shortDayNames = resources.getStringArray(R.array.weekday_short).asList(),
            longDayNames = resources.getStringArray(R.array.weekday_long).asList(),
            daily = resources.getString(R.string.schedule_summary_daily),
            noActiveDay = resources.getString(R.string.schedule_summary_none),
            varied = resources.getString(R.string.schedule_summary_varied),
            summaryFormat = resources.getString(R.string.schedule_summary_format),
            fullHoursFormat = resources.getString(R.string.schedule_window_hours),
            timesFormat = resources.getString(R.string.schedule_window_times),
        )
    }
}
