package dev.stepcron.wear.data.settings

import dev.stepcron.wear.domain.ActiveWindow
import dev.stepcron.wear.domain.DaySchedule
import dev.stepcron.wear.domain.WeeklySchedule

/**
 * Textformat des Wochenplans im DataStore (Version 1): sieben Tage Montag bis Sonntag, durch „;" getrennt,
 * je Tag „an,startMinute,endMinute" – z. B. `1,480,1020` für „an, 08:00–17:00".
 */
internal object ScheduleCodec {

    fun encode(schedule: WeeklySchedule): String =
        schedule.days.joinToString(DAY_SEPARATOR) { day ->
            listOf(if (day.enabled) 1 else 0, day.window.startMinute, day.window.endMinute).joinToString(FIELD_SEPARATOR)
        }

    /** @return den Wochenplan oder `null`, wenn [text] nicht dem Format entspricht. Werte werden bereinigt. */
    fun decode(text: String): WeeklySchedule? {
        val parts = text.split(DAY_SEPARATOR)
        if (parts.size != WeeklySchedule.DAYS_PER_WEEK) return null
        val days = parts.map { part ->
            val fields = part.split(FIELD_SEPARATOR)
            if (fields.size != 3) return null
            val enabled = when (fields[0]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            val start = fields[1].toIntOrNull() ?: return null
            val end = fields[2].toIntOrNull() ?: return null
            DaySchedule(enabled = enabled, window = ActiveWindow.sanitized(start, end))
        }
        return WeeklySchedule(days)
    }

    private const val DAY_SEPARATOR = ";"
    private const val FIELD_SEPARATOR = ","
}
