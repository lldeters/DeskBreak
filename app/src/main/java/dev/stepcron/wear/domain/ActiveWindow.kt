package dev.stepcron.wear.domain

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Aktives Zeitfenster eines Tages `[startMinute, endMinute)` in lokaler Zeit, in Minuten seit Mitternacht.
 * `endMinute = 1440` steht für Mitternacht am Folgetag. Außerhalb des Fensters gilt Ruhezeit.
 */
@Immutable
data class ActiveWindow(val startMinute: Int, val endMinute: Int) {

    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute muss zwischen 0 und 1439 liegen: $startMinute" }
        require(endMinute in 1..MINUTES_PER_DAY) { "endMinute muss zwischen 1 und 1440 liegen: $endMinute" }
        require(startMinute < endMinute) { "startMinute ($startMinute) muss vor endMinute ($endMinute) liegen" }
    }

    /** Die angebrochenen vollen Stunden des Fensters, z. B. 8..16 für 08:00–17:00 und 8..17 für 08:30–17:15. */
    val hours: IntRange get() = (startMinute / 60) until (endMinute + 59) / 60

    fun startOn(date: LocalDate, zone: ZoneId): Long =
        date.atTime(startMinute / 60, startMinute % 60).atZone(zone).toInstant().toEpochMilli()

    fun endOn(date: LocalDate, zone: ZoneId): Long =
        if (endMinute == MINUTES_PER_DAY) {
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            date.atTime(endMinute / 60, endMinute % 60).atZone(zone).toInstant().toEpochMilli()
        }

    fun contains(epochMillis: Long, zone: ZoneId): Boolean {
        val date = epochMillis.toLocalDate(zone)
        return epochMillis >= startOn(date, zone) && epochMillis < endOn(date, zone)
    }

    /** Beginn des Fensters an dem Kalendertag, in den [epochMillis] fällt. */
    fun startOfDay(epochMillis: Long, zone: ZoneId): Long = startOn(epochMillis.toLocalDate(zone), zone)

    /** Ende des Fensters an dem Kalendertag, in den [epochMillis] fällt. */
    fun endOfDay(epochMillis: Long, zone: ZoneId): Long = endOn(epochMillis.toLocalDate(zone), zone)

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        /** Kürzestes Fenster, das beim Bereinigen gespeicherter Werte entsteht (= Schrittweite der Zeitauswahl). */
        const val MIN_LENGTH_MINUTES = 15

        fun ofHours(startHour: Int, endHour: Int): ActiveWindow = ActiveWindow(startHour * 60, endHour * 60)

        /** Bringt gespeicherte Rohwerte in einen gültigen Bereich, statt beim Lesen eine Ausnahme auszulösen. */
        fun sanitized(startMinute: Int, endMinute: Int): ActiveWindow {
            val start = startMinute.coerceIn(0, MINUTES_PER_DAY - MIN_LENGTH_MINUTES)
            return ActiveWindow(start, endMinute.coerceIn(start + MIN_LENGTH_MINUTES, MINUTES_PER_DAY))
        }
    }
}

fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
