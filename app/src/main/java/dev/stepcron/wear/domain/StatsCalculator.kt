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
     * Ein Slot pro Stunde des aktiven Fensters. Priorität: Bewegung (grün) > verpasst (rot) >
     * Snooze (gelb) > nichts (grau).
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

    /** Die letzten 7 Tage (ältester zuerst, heute zuletzt); Tage ohne Daten erhalten `percent = null`. */
    fun lastSevenDays(daily: List<DailyCounts>, today: LocalDate): List<DayStat> {
        val byDate = daily.associateBy { it.date }
        return (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val counts = byDate[date]
            val movements = counts?.movements ?: 0
            val missed = counts?.missed ?: 0
            DayStat(
                date = date,
                movements = movements,
                snoozes = counts?.snoozes ?: 0,
                missed = missed,
                percent = regularityPercent(movements, missed),
                isToday = offset == 0,
            )
        }
    }
}
