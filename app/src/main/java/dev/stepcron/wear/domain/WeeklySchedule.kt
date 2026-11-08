package dev.stepcron.wear.domain

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** Einstellung eines Wochentags: ob an ihm erinnert wird ([enabled]) und in welchem Zeitfenster. */
@Immutable
data class DaySchedule(val enabled: Boolean, val window: ActiveWindow)

/**
 * Wochenplan der Erinnerungen: pro Wochentag an oder frei, jeweils mit eigenem Zeitfenster.
 * Ein freier Tag behält sein Fenster, damit es beim Wiedereinschalten erhalten bleibt.
 * Alle Abfragen beziehen sich auf den lokalen Kalendertag des Zeitpunkts.
 */
@Immutable
data class WeeklySchedule(
    /** Genau 7 Einträge, Montag zuerst (Index = ISO-Wochentag − 1). */
    val days: List<DaySchedule>,
) {

    init {
        require(days.size == DAYS_PER_WEEK) { "Ein Wochenplan braucht genau 7 Tage: ${days.size}" }
    }

    operator fun get(day: DayOfWeek): DaySchedule = days[day.value - 1]

    /** Ändert den Eintrag eines Wochentags. */
    fun with(day: DayOfWeek, transform: (DaySchedule) -> DaySchedule): WeeklySchedule =
        WeeklySchedule(days.mapIndexed { index, entry -> if (index == day.value - 1) transform(entry) else entry })

    /** Übernimmt [window] für alle Tage; ob ein Tag an oder frei ist, bleibt unverändert. */
    fun withAllWindows(window: ActiveWindow): WeeklySchedule = WeeklySchedule(days.map { it.copy(window = window) })

    val hasActiveDay: Boolean get() = days.any { it.enabled }

    /** Tage, an denen erinnert wird, in Wochenreihenfolge (Montag zuerst). */
    val activeDays: List<DayOfWeek> get() = DayOfWeek.entries.filter { this[it].enabled }

    /** Zeitfenster am Kalendertag [date] oder `null`, wenn der Tag frei ist. */
    fun windowOn(date: LocalDate): ActiveWindow? = this[date.dayOfWeek].takeIf { it.enabled }?.window

    /** Zeitfenster des Kalendertags, in den [epochMillis] fällt, oder `null` an einem freien Tag. */
    fun windowAt(epochMillis: Long, zone: ZoneId): ActiveWindow? = windowOn(epochMillis.toLocalDate(zone))

    fun contains(epochMillis: Long, zone: ZoneId): Boolean =
        windowAt(epochMillis, zone)?.contains(epochMillis, zone) == true

    /** Beginn des heutigen Fensters oder `null` an einem freien Tag. */
    fun startOfDay(epochMillis: Long, zone: ZoneId): Long? = windowAt(epochMillis, zone)?.startOfDay(epochMillis, zone)

    /** Ende des heutigen Fensters oder `null` an einem freien Tag. */
    fun endOfDay(epochMillis: Long, zone: ZoneId): Long? = windowAt(epochMillis, zone)?.endOfDay(epochMillis, zone)

    /**
     * Nächster Fensterbeginn nach [epochMillis]: heute, falls das heutige Fenster noch nicht begonnen hat,
     * sonst der Beginn am nächsten aktiven Tag. `null`, wenn an keinem Tag erinnert wird.
     */
    fun nextStart(epochMillis: Long, zone: ZoneId): Long? {
        val today = epochMillis.toLocalDate(zone)
        // Heute und die 7 Folgetage: Ist nur der heutige Wochentag aktiv und sein Fenster schon vorbei,
        // kommt er in einer Woche wieder.
        for (offset in 0L..DAYS_PER_WEEK.toLong()) {
            val date = today.plusDays(offset)
            val start = windowOn(date)?.startOn(date, zone) ?: continue
            if (start > epochMillis) return start
        }
        return null
    }

    /** Erster Moment ab [epochMillis] (einschließlich), an dem erinnert wird; `null` ohne aktiven Tag. */
    fun nextActiveAt(epochMillis: Long, zone: ZoneId): Long? =
        if (contains(epochMillis, zone)) epochMillis else nextStart(epochMillis, zone)

    companion object {
        const val DAYS_PER_WEEK = 7

        /** Zeitfenster neuer Tage: 08:00–17:00. */
        val DEFAULT_WINDOW: ActiveWindow = ActiveWindow.ofHours(8, 17)

        fun everyDay(window: ActiveWindow): WeeklySchedule =
            WeeklySchedule(List(DAYS_PER_WEEK) { DaySchedule(enabled = true, window = window) })

        /** Standard neuer Installationen: Montag bis Freitag 08–17 Uhr, Wochenende frei. */
        val DEFAULT: WeeklySchedule = WeeklySchedule(
            DayOfWeek.entries.map { day -> DaySchedule(enabled = day <= DayOfWeek.FRIDAY, window = DEFAULT_WINDOW) },
        )
    }
}
