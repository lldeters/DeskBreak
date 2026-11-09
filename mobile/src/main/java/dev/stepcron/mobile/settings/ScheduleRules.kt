package dev.stepcron.mobile.settings

import dev.stepcron.shared.sync.SyncDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Was an einem Zeitplan nicht stimmt. */
sealed interface ScheduleIssue {
    /** An keinem Tag wird erinnert. */
    data object NoActiveDay : ScheduleIssue

    /** Gewählter Beginn liegt nicht mindestens 15 Minuten vor dem Ende [endMinute]. */
    data class StartNotBeforeEnd(val endMinute: Int) : ScheduleIssue

    /** Gewähltes Ende liegt nicht mindestens 15 Minuten nach dem Beginn [startMinute]. */
    data class EndNotAfterStart(val startMinute: Int) : ScheduleIssue
}

/**
 * Wochenplan der Uhr: sieben [SyncDay]s, Montag zuerst; Minuten seit Mitternacht, Ende bis 1440 (24:00),
 * mindestens 15 Minuten lang. Zeiten werden auf dem Handy im 15-Minuten-Raster gewählt (wie auf der Uhr).
 * Dazu die Zeitrechnung, die die Uhr für ihren Status nutzt (Fenster heute, nächster Beginn).
 */
object ScheduleRules {
    const val DAYS_PER_WEEK = 7
    const val MINUTES_PER_DAY = 24 * 60
    const val STEP_MINUTES = 15
    const val MIN_LENGTH_MINUTES = 15

    /** Spätester Beginn (23:45). */
    const val MAX_START_MINUTE = MINUTES_PER_DAY - MIN_LENGTH_MINUTES

    /** Neue bzw. fehlende Tage: Montag bis Freitag 08:00–17:00 wie der Standard der Uhr. */
    fun defaultDay(index: Int): SyncDay = SyncDay(enabled = index < 5, startMinute = 8 * 60, endMinute = 17 * 60)

    /** Auf das 15-Minuten-Raster gerundet (kaufmännisch). */
    fun snap(minuteOfDay: Int): Int = ((minuteOfDay.coerceAtLeast(0) + STEP_MINUTES / 2) / STEP_MINUTES) * STEP_MINUTES

    /** Beginn aus einer Zeitauswahl (Stunde 0–23, Minute 0–59): gerundet, höchstens 23:45. */
    fun startFromPicker(hour: Int, minute: Int): Int = snap(hour * 60 + minute).coerceIn(0, MAX_START_MINUTE)

    /** Ende aus einer Zeitauswahl: gerundet; 00:00 (oder auf Mitternacht gerundet) bedeutet 24:00. */
    fun endFromPicker(hour: Int, minute: Int): Int {
        val snapped = snap(hour * 60 + minute)
        return if (snapped == 0) MINUTES_PER_DAY else snapped.coerceIn(MIN_LENGTH_MINUTES, MINUTES_PER_DAY)
    }

    /** Prüft einen neuen Beginn gegen das Ende des Tages. */
    fun checkStart(day: SyncDay, startMinute: Int): ScheduleIssue? =
        if (startMinute > day.endMinute - MIN_LENGTH_MINUTES) ScheduleIssue.StartNotBeforeEnd(day.endMinute) else null

    /** Prüft ein neues Ende gegen den Beginn des Tages. */
    fun checkEnd(day: SyncDay, endMinute: Int): ScheduleIssue? =
        if (endMinute < day.startMinute + MIN_LENGTH_MINUTES) ScheduleIssue.EndNotAfterStart(day.startMinute) else null

    fun isValid(day: SyncDay): Boolean =
        day.startMinute in 0..MAX_START_MINUTE &&
            day.endMinute in (day.startMinute + MIN_LENGTH_MINUTES)..MINUTES_PER_DAY

    /** Hinweise zum ganzen Plan (derzeit: kein aktiver Tag). */
    fun issues(schedule: List<SyncDay>): List<ScheduleIssue> =
        if (schedule.none { it.enabled }) listOf(ScheduleIssue.NoActiveDay) else emptyList()

    /** Genau sieben gültige Tage: kaputte Fenster werden wie auf der Uhr begrenzt, fehlende Tage ergänzt. */
    fun normalize(schedule: List<SyncDay>): List<SyncDay> = List(DAYS_PER_WEEK) { index ->
        val day = schedule.getOrNull(index) ?: return@List defaultDay(index)
        if (isValid(day)) {
            day
        } else {
            val start = day.startMinute.coerceIn(0, MAX_START_MINUTE)
            day.copy(startMinute = start, endMinute = day.endMinute.coerceIn(start + MIN_LENGTH_MINUTES, MINUTES_PER_DAY))
        }
    }

    fun withEnabled(schedule: List<SyncDay>, dayIndex: Int, enabled: Boolean): List<SyncDay> =
        normalize(schedule).mapIndexed { index, day -> if (index == dayIndex) day.copy(enabled = enabled) else day }

    /** Neuer Beginn; bleibt mindestens 15 Minuten vor dem Ende, ohne das Ende zu verschieben (wie auf der Uhr). */
    fun withStart(schedule: List<SyncDay>, dayIndex: Int, startMinute: Int): List<SyncDay> =
        normalize(schedule).mapIndexed { index, day ->
            if (index != dayIndex) return@mapIndexed day
            day.copy(startMinute = snap(startMinute).coerceIn(0, day.endMinute - MIN_LENGTH_MINUTES))
        }

    /** Neues Ende; bleibt mindestens 15 Minuten nach dem Beginn, ohne den Beginn zu verschieben. */
    fun withEnd(schedule: List<SyncDay>, dayIndex: Int, endMinute: Int): List<SyncDay> =
        normalize(schedule).mapIndexed { index, day ->
            if (index != dayIndex) return@mapIndexed day
            day.copy(endMinute = snap(endMinute).coerceIn(day.startMinute + MIN_LENGTH_MINUTES, MINUTES_PER_DAY))
        }

    // --- Zeitrechnung wie WeeklySchedule/ActiveWindow der Uhr ---

    /** Eintrag des Wochentags von [date] (Montag = Index 0). */
    fun dayOf(schedule: List<SyncDay>, date: LocalDate): SyncDay = normalize(schedule)[date.dayOfWeek.value - 1]

    /** Fenster am Kalendertag [date] oder `null`, wenn der Tag frei ist. */
    fun windowOn(schedule: List<SyncDay>, date: LocalDate): SyncDay? = dayOf(schedule, date).takeIf { it.enabled }

    fun startOn(day: SyncDay, date: LocalDate, zone: ZoneId): Long =
        date.atTime(day.startMinute / 60, day.startMinute % 60).atZone(zone).toInstant().toEpochMilli()

    fun endOn(day: SyncDay, date: LocalDate, zone: ZoneId): Long =
        if (day.endMinute >= MINUTES_PER_DAY) {
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            date.atTime(day.endMinute / 60, day.endMinute % 60).atZone(zone).toInstant().toEpochMilli()
        }

    /** Liegt [epochMillis] im Fenster seines Kalendertags? */
    fun contains(schedule: List<SyncDay>, epochMillis: Long, zone: ZoneId): Boolean {
        val date = epochMillis.toLocalDate(zone)
        val day = windowOn(schedule, date) ?: return false
        return epochMillis >= startOn(day, date, zone) && epochMillis < endOn(day, date, zone)
    }

    /** Ende des heutigen Fensters oder `null` an einem freien Tag. */
    fun endOfDay(schedule: List<SyncDay>, epochMillis: Long, zone: ZoneId): Long? {
        val date = epochMillis.toLocalDate(zone)
        return windowOn(schedule, date)?.let { endOn(it, date, zone) }
    }

    /** Nächster Fensterbeginn nach [epochMillis] (heute oder an einem der nächsten sieben Tage), sonst `null`. */
    fun nextStart(schedule: List<SyncDay>, epochMillis: Long, zone: ZoneId): Long? {
        val today = epochMillis.toLocalDate(zone)
        for (offset in 0L..DAYS_PER_WEEK.toLong()) {
            val date = today.plusDays(offset)
            val day = windowOn(schedule, date) ?: continue
            val start = startOn(day, date, zone)
            if (start > epochMillis) return start
        }
        return null
    }

    /** Erster Moment ab [epochMillis] (einschließlich), an dem erinnert wird; `null` ohne aktiven Tag. */
    fun nextActiveAt(schedule: List<SyncDay>, epochMillis: Long, zone: ZoneId): Long? =
        if (contains(schedule, epochMillis, zone)) epochMillis else nextStart(schedule, epochMillis, zone)
}

fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
