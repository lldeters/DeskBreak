package dev.stepcron.mobile.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.stepcron.mobile.R
import dev.stepcron.mobile.settings.ScheduleIssue
import dev.stepcron.mobile.settings.VibrationLevel
import dev.stepcron.mobile.settings.toLocalDate
import dev.stepcron.mobile.sync.SyncProblem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Formatierung für die Anzeige. Uhrzeiten im 24-Stunden-Format; Datum und Wochentage in der Sprache des Handys.
 * Zeiten der Uhr (Status, Auswertung) stehen in deren Zeitzone, Zeiten des Handys (letzter Abgleich) in dessen.
 */
object Formats {
    private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun clock(epochMillis: Long, zone: ZoneId): String = clockFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** Minuten seit Mitternacht als „08:30"; 1440 wird zu „24:00". */
    fun minuteOfDay(minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

    /** Kurzes Datum mit Wochentag, z. B. „Mon, Nov 24" bzw. „Mo., 24. Nov.". */
    fun shortDate(date: LocalDate, locale: Locale): String =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale).format(date)

    /** Datum ohne Wochentag, z. B. „Nov 24, 2026". */
    fun mediumDate(date: LocalDate, locale: Locale): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

    fun weekdayShort(day: DayOfWeek, locale: Locale): String = day.getDisplayName(TextStyle.SHORT, locale)

    fun weekdayFull(day: DayOfWeek, locale: Locale): String = day.getDisplayName(TextStyle.FULL, locale)
}

/** Sprache des Handys für Datumsangaben; folgt einem Sprachwechsel. */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

/** „heute, 10:42", „gestern, 18:03" oder „Mo., 3. Nov., 10:42" – in der Zeitzone des Handys. */
@Composable
fun phoneDateTimeText(epochMillis: Long, nowMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = epochMillis.toLocalDate(zone)
    val today = nowMillis.toLocalDate(zone)
    val time = Formats.clock(epochMillis, zone)
    return when (date) {
        today -> stringResource(R.string.datetime_today, time)
        today.minusDays(1) -> stringResource(R.string.datetime_yesterday, time)
        else -> stringResource(R.string.datetime_date, Formats.shortDate(date, currentLocale()), time)
    }
}

/** Wann es nach Pause bzw. Ruhezeit weitergeht, wie auf der Uhr: „Weiter morgen um 08:00" usw. */
@Composable
fun resumeText(resumeAtMillis: Long, nowMillis: Long, zone: ZoneId): String {
    val time = Formats.clock(resumeAtMillis, zone)
    val date = resumeAtMillis.toLocalDate(zone)
    val locale = currentLocale()
    return when (val days = ChronoUnit.DAYS.between(nowMillis.toLocalDate(zone), date)) {
        0L -> stringResource(R.string.resume_today, time)
        1L -> stringResource(R.string.resume_tomorrow, time)
        in 2L..6L -> stringResource(R.string.resume_on, Formats.weekdayFull(date.dayOfWeek, locale), time)
        7L -> stringResource(R.string.resume_next_week, Formats.weekdayFull(date.dayOfWeek, locale), time)
        else -> if (days < 0) {
            stringResource(R.string.resume_today, time)
        } else {
            stringResource(R.string.resume_on, Formats.shortDate(date, locale), time)
        }
    }
}

/** Erklärung eines Problems für den Nutzer. */
@Composable
fun problemText(problem: SyncProblem): String = when (problem) {
    SyncProblem.PlayServicesMissing -> stringResource(R.string.problem_play_services)
    SyncProblem.WearApiUnavailable -> stringResource(R.string.problem_wear_api)
    SyncProblem.NoWatch -> stringResource(R.string.problem_no_watch)
    SyncProblem.AppMissingOnWatch -> stringResource(R.string.problem_app_missing)
    SyncProblem.NoDataYet -> stringResource(R.string.problem_no_data)
    SyncProblem.WatchDidNotAnswer -> stringResource(R.string.problem_no_answer)
    is SyncProblem.DataTooNew -> stringResource(R.string.problem_too_new)
    SyncProblem.InvalidData -> stringResource(R.string.problem_invalid)
    SyncProblem.Timeout -> stringResource(R.string.problem_timeout)
    is SyncProblem.RejectedByWatch -> problem.message?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.problem_rejected_message, it) }
        ?: stringResource(R.string.problem_rejected)

    is SyncProblem.Failed -> problem.statusCode?.let { stringResource(R.string.problem_failed_code, it) }
        ?: stringResource(R.string.problem_failed)
}

/** Ist das Problem ein echter Fehler (rot) oder nur ein Hinweis? */
fun SyncProblem.isError(): Boolean = when (this) {
    SyncProblem.NoDataYet, SyncProblem.WatchDidNotAnswer, SyncProblem.NoWatch -> false
    else -> true
}

@Composable
fun scheduleIssueText(issue: ScheduleIssue): String = when (issue) {
    ScheduleIssue.NoActiveDay -> stringResource(R.string.schedule_issue_no_day)
    is ScheduleIssue.StartNotBeforeEnd -> stringResource(R.string.schedule_issue_start, Formats.minuteOfDay(issue.endMinute))
    is ScheduleIssue.EndNotAfterStart -> stringResource(R.string.schedule_issue_end, Formats.minuteOfDay(issue.startMinute))
}

@Composable
fun vibrationText(level: VibrationLevel): String = when (level) {
    VibrationLevel.OFF -> stringResource(R.string.vibration_off)
    VibrationLevel.LIGHT -> stringResource(R.string.vibration_light)
    VibrationLevel.MEDIUM -> stringResource(R.string.vibration_medium)
    VibrationLevel.STRONG -> stringResource(R.string.vibration_strong)
}

/** „1 Pause" / „7 Pausen". */
@Composable
fun breaksText(count: Int): String = pluralStringResource(R.plurals.breaks_count, count, count)

/** Prozentwert oder „–". */
@Composable
fun percentText(percent: Int?): String =
    percent?.let { stringResource(R.string.percent_value, it) } ?: stringResource(R.string.no_value)
