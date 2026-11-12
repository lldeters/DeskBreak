package dev.stepcron.mobile.stats

import dev.stepcron.shared.sync.SyncEvent
import dev.stepcron.shared.sync.SyncSnapshot
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/** Zähler eines Kalendertags. */
data class DayStat(
    val date: LocalDate,
    val movements: Int,
    val snoozes: Int,
    val missed: Int,
    val isToday: Boolean,
    /** Ist der Wochentag im (aktuellen) Wochenplan ein Erinnerungstag? Freie Tage ohne Daten zeigen „frei". */
    val scheduled: Boolean = true,
) {
    /** Regelmäßigkeit in Prozent oder `null`, wenn an diesem Tag nichts fällig war. */
    val percent: Int? get() = StatsCalculator.regularityPercent(movements, missed)

    /** Gab es an diesem Tag überhaupt etwas (auch nur Snoozes)? */
    val hasData: Boolean get() = movements + missed + snoozes > 0
}

/** Heute: Pausen, verpasste Erinnerungen, Snoozes, Regelmäßigkeit und Tagesziel. */
data class TodaySummary(
    val movements: Int,
    val snoozes: Int,
    val missed: Int,
    val dailyGoal: Int,
) {
    val percent: Int? get() = StatsCalculator.regularityPercent(movements, missed)
    val goalReached: Boolean get() = movements >= dailyGoal
    val remainingToGoal: Int get() = (dailyGoal - movements).coerceAtLeast(0)

    /** Fortschritt zum Tagesziel, 0 bis 1. */
    val goalProgress: Float get() = StatsCalculator.goalShare(movements, dailyGoal)
}

/** Quote einer Stunde: [percent] der [due] fälligen Intervalle waren erfolgreich. */
data class HourRate(val hour: Int, val percent: Int, val due: Int)

/** Beste und schwächste Stunde des Tages. */
data class TimeOfDayInsight(val best: HourRate, val worst: HourRate)

/** Zähler einer Stunde des Tages (0–23) über mehrere Tage. */
data class HourlyCounts(val hour: Int, val movements: Int, val missed: Int)

/** Durchschnitt eines Wochentags über die letzten Wochen (nur Tage mit Daten). */
data class WeekdayStat(
    val dayOfWeek: java.time.DayOfWeek,
    /** Durchschnittliche Pausen an Tagen mit Daten; `null` ohne solche Tage. */
    val averageMovements: Float?,
    val percent: Int?,
    val daysWithData: Int,
)

/** Monatsansicht: Kalenderwochen Montag bis Sonntag, künftige Tage `null`. */
data class CalendarSummary(
    val weeks: List<List<DayStat?>>,
    /** Tage mit erreichtem Tagesziel bzw. mit fälligen Intervallen überhaupt. */
    val goalDays: Int,
    val daysWithData: Int,
)

/** Alles, was Heute- und Verlaufs-Seite aus einem Snapshot zeigen. */
data class Dashboard(
    val today: LocalDate,
    val zone: ZoneId,
    val summary: TodaySummary,
    val streak: Int,
    /** Die letzten sieben Tage, ältester zuerst. */
    val week: List<DayStat>,
    val weekMovements: Int,
    val weekPercent: Int?,
    /** Die sieben Tage davor – für den Vergleich mit der Vorwoche. */
    val previousWeekMovements: Int,
    val previousWeekPercent: Int?,
    val calendar: CalendarSummary,
    val timeOfDay: TimeOfDayInsight?,
    /** Pausen und verpasste Erinnerungen je Stunde der letzten 30 Tage (nur Stunden mit Daten). */
    val hourly: List<HourlyCounts>,
    /** Montag bis Sonntag über die Wochen der Monatsansicht (ohne heute). */
    val weekdays: List<WeekdayStat>,
    /** Die letzten 30 Tage, neuester (heute) zuerst. */
    val lastDays: List<DayStat>,
    val dailyGoal: Int,
)

/**
 * Auswertung wie auf der Uhr (StatsCalculator/MovementDao dort), damit die Zahlen übereinstimmen.
 *
 * Jedes fällige Intervall endet entweder erfolgreich (Erledigt, Auto-Schritte, manueller Reset) oder verpasst
 * (Zeitpunkt = wann die Erinnerung fällig war). Snooze verschiebt nur und zählt weder positiv noch negativ.
 * Tage und Stunden gelten in der Zeitzone der Uhr; unbekannte Ereignistypen werden übergangen.
 */
object StatsCalculator {

    /** Fällige Intervalle, ab denen eine Stunde für die beste bzw. schwächste Tageszeit zählt. */
    const val MIN_DUE_PER_HOUR = 3

    /** Zeitraum der besten/schwächsten Tageszeit und der Tagesliste. */
    const val TIME_OF_DAY_DAYS = 30
    const val LAST_DAYS = 30

    /** Wochen der Monatsansicht. */
    const val CALENDAR_WEEKS = 5

    fun regularityPercent(movements: Int, missed: Int): Int? {
        val due = movements + missed
        if (due <= 0) return null
        return (movements * 100.0 / due).roundToInt()
    }

    /** Anteil des Tagesziels, 0 bis 1 (Grundlage der Farbstärke in der Monatsansicht). */
    fun goalShare(movements: Int, goal: Int): Float = (movements.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)

    /** Zeitzone des Snapshots; eine unbekannte ID (kaputte Daten) fällt auf die des Handys zurück. */
    fun zoneOf(snapshot: SyncSnapshot): ZoneId = try {
        ZoneId.of(snapshot.zoneId)
    } catch (e: DateTimeException) {
        ZoneId.systemDefault()
    }

    /** Erfolgreiche Intervalle seit der letzten verpassten Erinnerung (wie die Serie der Uhr). */
    fun streak(events: List<SyncEvent>): Int {
        val lastMissed = events.filter { it.type == SyncEvent.MISSED }.maxOfOrNull { it.timestampMillis } ?: -1L
        return events.count { it.isMovement && it.timestampMillis > lastMissed }
    }

    /** Tageszähler je Kalendertag in [zone]. */
    fun dailyCounts(events: List<SyncEvent>, zone: ZoneId): Map<LocalDate, DayCounts> {
        val result = HashMap<LocalDate, DayCounts>()
        for (event in events) {
            if (event.type !in SyncEvent.KNOWN_TYPES) continue
            val date = Instant.ofEpochMilli(event.timestampMillis).atZone(zone).toLocalDate()
            val counts = result.getOrPut(date) { DayCounts() }
            when {
                event.isMovement -> counts.movements++
                event.type == SyncEvent.SNOOZED -> counts.snoozes++
                event.type == SyncEvent.MISSED -> counts.missed++
            }
        }
        return result
    }

    /** Zähler je Stunde des Tages für alle Ereignisse ab [fromMillis]. */
    fun hourlyCounts(events: List<SyncEvent>, fromMillis: Long, zone: ZoneId): List<HourlyCounts> {
        val movements = IntArray(HOURS_PER_DAY)
        val missed = IntArray(HOURS_PER_DAY)
        for (event in events) {
            if (event.timestampMillis < fromMillis) continue
            val hour = Instant.ofEpochMilli(event.timestampMillis).atZone(zone).hour
            when {
                event.isMovement -> movements[hour]++
                event.type == SyncEvent.MISSED -> missed[hour]++
            }
        }
        return (0 until HOURS_PER_DAY)
            .filter { movements[it] + missed[it] > 0 }
            .map { HourlyCounts(it, movements[it], missed[it]) }
    }

    /**
     * Beste und schwächste Stunde nach ihrer Quote; nur Stunden mit mindestens [minDue] fälligen Intervallen
     * zählen, bei Gleichstand die mit mehr Daten. `null`, solange es keine zwei solchen Stunden mit
     * unterschiedlicher Quote gibt.
     */
    fun timeOfDay(hourly: List<HourlyCounts>, minDue: Int = MIN_DUE_PER_HOUR): TimeOfDayInsight? {
        val rates = hourly.mapNotNull { counts ->
            val percent = regularityPercent(counts.movements, counts.missed) ?: return@mapNotNull null
            val due = counts.movements + counts.missed
            if (due < minDue) null else HourRate(counts.hour, percent, due)
        }
        if (rates.size < 2) return null
        val best = rates.maxWith(compareBy<HourRate> { it.percent }.thenBy { it.due })
        val worst = rates.minWith(compareBy<HourRate> { it.percent }.thenByDescending { it.due })
        return if (best.percent == worst.percent) null else TimeOfDayInsight(best, worst)
    }

    /**
     * Wochentage Montag bis Sonntag: Durchschnitt über alle Tage mit Daten aus [days] (z. B. die Monatsansicht ohne
     * heute, damit ein angebrochener Tag den Schnitt nicht drückt).
     */
    fun weekdays(days: List<DayStat>): List<WeekdayStat> = java.time.DayOfWeek.entries.map { dayOfWeek ->
        val withData = days.filter { it.date.dayOfWeek == dayOfWeek && it.movements + it.missed > 0 }
        val movements = withData.sumOf { it.movements }
        WeekdayStat(
            dayOfWeek = dayOfWeek,
            averageMovements = if (withData.isEmpty()) null else movements.toFloat() / withData.size,
            percent = regularityPercent(movements, withData.sumOf { it.missed }),
            daysWithData = withData.size,
        )
    }

    /** Erster Tag der Monatsansicht: Montag vor [weeks] − 1 Wochen. */
    fun calendarStart(today: LocalDate, weeks: Int = CALENDAR_WEEKS): LocalDate =
        today.minusDays(today.dayOfWeek.value - 1L).minusWeeks(weeks - 1L)

    /** Die letzten [weeks] Kalenderwochen bis einschließlich der aktuellen, je Montag bis Sonntag. */
    fun calendar(
        daily: Map<LocalDate, DayCounts>,
        today: LocalDate,
        goal: Int,
        weeks: Int = CALENDAR_WEEKS,
        scheduled: (LocalDate) -> Boolean = { true },
    ): CalendarSummary {
        val firstMonday = calendarStart(today, weeks)
        val grid = (0 until weeks).map { week ->
            (0 until DAYS_PER_WEEK).map { day ->
                val date = firstMonday.plusDays(week * DAYS_PER_WEEK.toLong() + day)
                if (date > today) null else dayStat(date, daily[date], today, scheduled(date))
            }
        }
        val days = grid.flatten().filterNotNull()
        return CalendarSummary(
            weeks = grid,
            goalDays = days.count { it.movements >= goal },
            daysWithData = days.count { it.movements + it.missed > 0 },
        )
    }

    /** [count] Tage bis einschließlich [today]; [newestFirst] bestimmt die Reihenfolge. */
    fun lastDays(
        daily: Map<LocalDate, DayCounts>,
        today: LocalDate,
        count: Int,
        newestFirst: Boolean,
        scheduled: (LocalDate) -> Boolean = { true },
    ): List<DayStat> {
        val offsets = if (newestFirst) 0 until count else (count - 1 downTo 0)
        return offsets.map { offset ->
            val date = today.minusDays(offset.toLong())
            dayStat(date, daily[date], today, scheduled(date))
        }
    }

    fun todaySummary(daily: Map<LocalDate, DayCounts>, today: LocalDate, goal: Int): TodaySummary {
        val counts = daily[today]
        return TodaySummary(
            movements = counts?.movements ?: 0,
            snoozes = counts?.snoozes ?: 0,
            missed = counts?.missed ?: 0,
            dailyGoal = goal,
        )
    }

    /**
     * Gesamtauswertung von [snapshot] zum Zeitpunkt [nowMillis]; „heute" ist der aktuelle Tag in der Zeitzone der
     * Uhr. [dailyGoal] kann von einer noch nicht bestätigten Änderung kommen, sonst gilt der Wert des Snapshots.
     */
    fun dashboard(snapshot: SyncSnapshot, nowMillis: Long, dailyGoal: Int = snapshot.settings.dailyGoal): Dashboard {
        val zone = zoneOf(snapshot)
        val events = snapshot.events.filter { it.type in SyncEvent.KNOWN_TYPES }
        val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone).toLocalDate()
        val daily = dailyCounts(events, zone)
        // Freie Tage nach dem aktuellen Wochenplan (Montag zuerst); ältere Pläne kennt der Snapshot nicht.
        val schedule = snapshot.settings.schedule
        val scheduled = { date: LocalDate -> schedule.getOrNull(date.dayOfWeek.value - 1)?.enabled ?: true }
        val week = lastDays(daily, today, DAYS_PER_WEEK, newestFirst = false, scheduled = scheduled)
        val weekMovements = week.sumOf { it.movements }
        val weekMissed = week.sumOf { it.missed }
        val previousWeek = lastDays(daily, today.minusDays(DAYS_PER_WEEK.toLong()), DAYS_PER_WEEK, newestFirst = false)
        val previousMovements = previousWeek.sumOf { it.movements }
        val hourlyStart = today.minusDays(TIME_OF_DAY_DAYS - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
        val hourly = hourlyCounts(events, hourlyStart, zone)
        val calendar = calendar(daily, today, dailyGoal, scheduled = scheduled)
        return Dashboard(
            today = today,
            zone = zone,
            summary = todaySummary(daily, today, dailyGoal),
            streak = streak(events),
            week = week,
            weekMovements = weekMovements,
            weekPercent = regularityPercent(weekMovements, weekMissed),
            previousWeekMovements = previousMovements,
            previousWeekPercent = regularityPercent(previousMovements, previousWeek.sumOf { it.missed }),
            calendar = calendar,
            timeOfDay = timeOfDay(hourly),
            hourly = hourly,
            weekdays = weekdays(calendar.weeks.flatten().filterNotNull().filterNot { it.isToday }),
            lastDays = lastDays(daily, today, LAST_DAYS, newestFirst = true, scheduled = scheduled),
            dailyGoal = dailyGoal,
        )
    }

    private fun dayStat(date: LocalDate, counts: DayCounts?, today: LocalDate, scheduled: Boolean = true) = DayStat(
        date = date,
        movements = counts?.movements ?: 0,
        snoozes = counts?.snoozes ?: 0,
        missed = counts?.missed ?: 0,
        isToday = date == today,
        scheduled = scheduled,
    )

    private const val HOURS_PER_DAY = 24
    private const val DAYS_PER_WEEK = 7
}

/** Veränderliche Zähler während der Auswertung eines Tages. */
class DayCounts(var movements: Int = 0, var snoozes: Int = 0, var missed: Int = 0)
