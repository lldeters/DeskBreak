package dev.stepcron.wear.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.stepcron.wear.R
import dev.stepcron.wear.domain.WeeklySchedule
import dev.stepcron.wear.domain.toLocalDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Wann es weitergeht: „Weiter um 08:00", „Weiter morgen um 08:00", „Weiter am Montag um 08:00",
 * „Weiter nächsten Montag um 08:00" bzw. ab acht Tagen (nach einem Urlaub) „Weiter am Mo, 24.11. um 08:00".
 */
fun resumeText(resources: Resources, resumeAtMillis: Long, daysUntilResume: Int, zone: ZoneId): String {
    val time = formatClock(resumeAtMillis, zone)
    val date = resumeAtMillis.toLocalDate(zone)
    return when (daysUntilResume) {
        0 -> resources.getString(R.string.home_status_paused_resume_today, time)
        1 -> resources.getString(R.string.home_status_paused_resume_tomorrow, time)
        in 2 until WeeklySchedule.DAYS_PER_WEEK ->
            resources.getString(R.string.home_status_paused_resume_later, dayName(resources, date.dayOfWeek), time)

        WeeklySchedule.DAYS_PER_WEEK ->
            resources.getString(R.string.home_status_paused_resume_next_week, dayName(resources, date.dayOfWeek), time)

        else -> resources.getString(R.string.home_status_paused_resume_later, shortDate(resources, date), time)
    }
}

/** [resumeText] für die Compose-UI; folgt einem Sprachwechsel. */
@Composable
fun rememberResumeText(resumeAtMillis: Long, daysUntilResume: Int): String {
    val configuration = LocalConfiguration.current
    val resources = LocalContext.current.resources
    return remember(configuration, resources, resumeAtMillis, daysUntilResume) {
        resumeText(resources, resumeAtMillis, daysUntilResume, ZoneId.systemDefault())
    }
}

/**
 * Kurzes Datum mit Wochentag, z. B. „Mo., 24.11." bzw. „Mon, Nov 24" – in der Sprache der App-Texte, nicht der
 * des Geräts: Auf einer Uhr ohne eigene Übersetzung sollen Wochentag und Monat zum englischen Text passen.
 */
fun shortDate(resources: Resources, date: LocalDate): String {
    val locale = Locale.forLanguageTag(resources.getString(R.string.resource_locale))
    return date.format(DateTimeFormatter.ofPattern(resources.getString(R.string.date_short_pattern), locale))
}

private fun dayName(resources: Resources, day: DayOfWeek): String =
    resources.getStringArray(R.array.weekday_long).getOrElse(day.value - 1) { day.name }
