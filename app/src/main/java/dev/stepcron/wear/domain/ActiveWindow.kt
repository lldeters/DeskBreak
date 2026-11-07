package dev.stepcron.wear.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tägliches aktives Zeitfenster `[startHour, endHour)` in lokaler Zeit.
 * `endHour = 24` steht für Mitternacht am Folgetag. Außerhalb des Fensters gilt Ruhezeit.
 */
data class ActiveWindow(val startHour: Int, val endHour: Int) {

    init {
        require(startHour in 0..23) { "startHour muss zwischen 0 und 23 liegen: $startHour" }
        require(endHour in 1..24) { "endHour muss zwischen 1 und 24 liegen: $endHour" }
        require(startHour < endHour) { "startHour ($startHour) muss vor endHour ($endHour) liegen" }
    }

    /** Die vollen Stunden des Fensters, z. B. 8..16 für 08:00–17:00. */
    val hours: IntRange get() = startHour until endHour

    fun startOn(date: LocalDate, zone: ZoneId): Long =
        date.atTime(startHour, 0).atZone(zone).toInstant().toEpochMilli()

    fun endOn(date: LocalDate, zone: ZoneId): Long =
        if (endHour == 24) {
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            date.atTime(endHour, 0).atZone(zone).toInstant().toEpochMilli()
        }

    fun contains(epochMillis: Long, zone: ZoneId): Boolean {
        val date = epochMillis.toLocalDate(zone)
        return epochMillis >= startOn(date, zone) && epochMillis < endOn(date, zone)
    }

    /** Beginn des Fensters an dem Kalendertag, in den [epochMillis] fällt. */
    fun startOfDay(epochMillis: Long, zone: ZoneId): Long = startOn(epochMillis.toLocalDate(zone), zone)

    /** Ende des Fensters an dem Kalendertag, in den [epochMillis] fällt. */
    fun endOfDay(epochMillis: Long, zone: ZoneId): Long = endOn(epochMillis.toLocalDate(zone), zone)

    /** Nächster Fensterbeginn: heute, falls noch vor Beginn, sonst am Folgetag. */
    fun nextStart(epochMillis: Long, zone: ZoneId): Long {
        val date = epochMillis.toLocalDate(zone)
        val todayStart = startOn(date, zone)
        return if (epochMillis < todayStart) todayStart else startOn(date.plusDays(1), zone)
    }
}

fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
