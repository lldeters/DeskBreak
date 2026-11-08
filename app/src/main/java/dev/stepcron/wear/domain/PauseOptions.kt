package dev.stepcron.wear.domain

import java.time.LocalDate
import java.time.ZoneId

/** Bis wann die Optionen der Schnellpause bzw. des Urlaubs pausieren. */
object PauseOptions {

    /** Dauern der Schnellpause in Minuten. */
    val QUICK_MINUTES: List<Int> = listOf(30, 60, 120)

    /** Längster wählbarer Urlaub in Tagen ab morgen. */
    const val MAX_VACATION_DAYS = 60

    fun forMinutes(nowMillis: Long, minutes: Int): Long = nowMillis + minutes * 60_000L

    /** Rest des Tages: bis Mitternacht, also bis zum nächsten Fensterbeginn ab morgen. */
    fun restOfDay(nowMillis: Long, zone: ZoneId): Long = endOfDay(nowMillis.toLocalDate(zone), zone)

    /** Urlaub bis einschließlich [lastDay]: bis zu dessen Ende um Mitternacht. */
    fun vacationUntil(lastDay: LocalDate, zone: ZoneId): Long = endOfDay(lastDay, zone)

    /** Wählbare letzte Urlaubstage: morgen bis in [MAX_VACATION_DAYS] Tagen. */
    fun vacationDays(today: LocalDate): List<LocalDate> = (1..MAX_VACATION_DAYS).map { today.plusDays(it.toLong()) }

    private fun endOfDay(date: LocalDate, zone: ZoneId): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
