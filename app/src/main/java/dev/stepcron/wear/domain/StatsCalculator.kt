package dev.stepcron.wear.domain

import dev.stepcron.wear.data.db.MovementEvent
import dev.stepcron.wear.data.db.MovementEventType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Status eines Stunden-Slots in der Tages-Timeline. */
enum class SlotStatus { DONE, SNOOZED, MISSED, NONE }

data class HourSlot(
    val hour: Int,
    val status: SlotStatus,
    val movementCount: Int,
    val isCurrent: Boolean,
    val isFuture: Boolean,
)

/** Aggregierte Zähler eines Kalendertags. */
data class DailyCounts(
    val date: LocalDate,
    val movements: Int,
    val snoozes: Int,
    val missed: Int,
)

data class DayStat(
    val date: LocalDate,
    val movements: Int,
    val snoozes: Int,
    val missed: Int,
    /** Regelmäßigkeits-Quote in Prozent oder `null`, wenn an diesem Tag nichts fällig war. */
    val percent: Int?,
    val isToday: Boolean,
)

/** Zähler einer Stunde des Tages (0–23) über mehrere Tage. */
data class HourlyCounts(val hour: Int, val movements: Int, val missed: Int)

/** Quote einer Stunde: [percent] der [due] fälligen Intervalle waren erfolgreich. */
data class HourRate(val hour: Int, val percent: Int, val due: Int)

/** Beste und schwächste Stunde des Tages. */
data class TimeOfDayInsight(val best: HourRate, val worst: HourRate)

data class DaySummary(val movements: Int, val snoozes: Int, val missed: Int) {
    val percent: Int? get() = StatsCalculator.regularityPercent(movements, missed)
}

/**
 * Auswertungslogik.
 *
 * Jedes fällige Intervall endet mit genau einem Ergebnis: erfolgreich (Erledigt, Auto-Schritte,
 * manueller Reset) oder verpasst. Snooze verschiebt nur und zählt weder positiv noch negativ.
 * Regelmäßigkeit = erfolgreiche Intervalle / alle fälligen Intervalle.
 */
object StatsCalculator {

    fun regularityPercent(movements: Int, missed: Int): Int? {
        val due = movements + missed
        if (due <= 0) return null
        return (movements * 100.0 / due).roundToInt()
    }

    fun summarize(events: List<MovementEvent>): DaySummary = DaySummary(
        movements = events.count { it.eventType.isMovement },
        snoozes = events.count { it.eventType == MovementEventType.SNOOZED },
        missed = events.count { it.eventType == MovementEventType.MISSED },
    )

    /**
     * Ein Slot pro (angebrochener) Stunde des Zeitfensters von heute. Priorität: Bewegung (grün) >
     * verpasst (rot) > Snooze (gelb) > nichts (grau).
     */
    fun hourSlots(
        todayEvents: List<MovementEvent>,
        window: ActiveWindow,
        nowMillis: Long,
        zone: ZoneId,
    ): List<HourSlot> {
        val currentHour = Instant.ofEpochMilli(nowMillis).atZone(zone).hour
        val byHour = todayEvents.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).hour }
        return window.hours.map { hour ->
            val events = byHour[hour].orEmpty()
            val movements = events.count { it.eventType.isMovement }
            val status = when {
                movements > 0 -> SlotStatus.DONE
                events.any { it.eventType == MovementEventType.MISSED } -> SlotStatus.MISSED
                events.any { it.eventType == MovementEventType.SNOOZED } -> SlotStatus.SNOOZED
                else -> SlotStatus.NONE
            }
            HourSlot(
                hour = hour,
                status = status,
                movementCount = movements,
                isCurrent = hour == currentHour,
                isFuture = hour > currentHour,
            )
        }
    }

    /** Fällige Intervalle, ab denen eine Stunde für die beste bzw. schwächste Tageszeit zählt. */
    const val MIN_DUE_PER_HOUR = 3

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

    /** Wochen der Monatsansicht. */
    const val CALENDAR_WEEKS = 5

    /**
     * Die letzten [weeks] Kalenderwochen bis einschließlich der aktuellen, je Montag bis Sonntag. Tage nach
     * [today] sind `null`.
     */
    fun calendarWeeks(daily: List<DailyCounts>, today: LocalDate, weeks: Int = CALENDAR_WEEKS): List<List<DayStat?>> {
        val byDate = daily.associateBy { it.date }
        val firstMonday = calendarStart(today, weeks)
        return (0 until weeks).map { week ->
            (0 until 7).map { day ->
                val date = firstMonday.plusDays(week * 7L + day)
                if (date > today) null else dayStat(date, byDate[date], isToday = date == today)
            }
        }
    }

    /** Erster Tag der Monatsansicht (Montag vor [CALENDAR_WEEKS] Wochen). */
    fun calendarStart(today: LocalDate, weeks: Int = CALENDAR_WEEKS): LocalDate =
        today.minusDays(today.dayOfWeek.value - 1L).minusWeeks(weeks - 1L)

    private fun dayStat(date: LocalDate, counts: DailyCounts?, isToday: Boolean): DayStat {
        val movements = counts?.movements ?: 0
        val missed = counts?.missed ?: 0
        return DayStat(
            date = date,
            movements = movements,
            snoozes = counts?.snoozes ?: 0,
            missed = missed,
            percent = regularityPercent(movements, missed),
            isToday = isToday,
        )
    }

    /** Die letzten 7 Tage (ältester zuerst, heute zuletzt); Tage ohne Daten erhalten `percent = null`. */
    fun lastSevenDays(daily: List<DailyCounts>, today: LocalDate): List<DayStat> {
        val byDate = daily.associateBy { it.date }
        return (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            dayStat(date, byDate[date], isToday = offset == 0)
        }
    }
}
