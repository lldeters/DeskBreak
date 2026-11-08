package dev.stepcron.wear.ui

import dev.stepcron.wear.domain.ActiveWindow
import dev.stepcron.wear.domain.DaySchedule
import dev.stepcron.wear.domain.WeeklySchedule
import dev.stepcron.wear.ui.schedule.TimeOptions
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

class ScheduleTextsTest {

    private val texts = ScheduleTexts(
        shortDayNames = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"),
        longDayNames = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"),
        daily = "Täglich",
        noActiveDay = "Kein Tag aktiv",
        varied = "unterschiedlich",
        summaryFormat = "%1\$s · %2\$s",
        fullHoursFormat = "%1\$s–%2\$s Uhr",
        timesFormat = "%1\$s–%2\$s",
    )

    private val eightToFive = ActiveWindow.ofHours(8, 17)

    private fun schedule(vararg active: DayOfWeek, window: ActiveWindow = eightToFive) =
        WeeklySchedule(DayOfWeek.entries.map { DaySchedule(enabled = it in active, window = window) })

    @Test
    fun `summary of the default schedule`() {
        assertEquals("Mo–Fr · 08–17 Uhr", texts.summary(WeeklySchedule.DEFAULT))
    }

    @Test
    fun `free days with other hours do not count`() {
        val schedule = WeeklySchedule.DEFAULT.with(SATURDAY) { it.copy(window = ActiveWindow.ofHours(10, 14)) }
        assertEquals("Mo–Fr · 08–17 Uhr", texts.summary(schedule))
    }

    @Test
    fun `every day is daily`() {
        assertEquals("Täglich · 08–17 Uhr", texts.summary(WeeklySchedule.everyDay(eightToFive)))
    }

    @Test
    fun `different hours on active days are varied`() {
        val schedule = WeeklySchedule.DEFAULT.with(FRIDAY) { it.copy(window = ActiveWindow.ofHours(8, 14)) }
        assertEquals("Mo–Fr · unterschiedlich", texts.summary(schedule))
    }

    @Test
    fun `no active day`() {
        assertEquals("Kein Tag aktiv", texts.summary(schedule()))
    }

    @Test
    fun `day runs of three or more become ranges`() {
        assertEquals("Sa, So", texts.dayRuns(listOf(SATURDAY, SUNDAY)))
        assertEquals("Mo–Mi, Fr", texts.dayRuns(listOf(MONDAY, TUESDAY, WEDNESDAY, FRIDAY)))
        assertEquals("Mo, Fr–So", texts.dayRuns(listOf(MONDAY, FRIDAY, SATURDAY, SUNDAY)))
        assertEquals("Mo, Mi, Fr", texts.dayRuns(listOf(FRIDAY, MONDAY, WEDNESDAY)))
        assertEquals("Do", texts.dayRuns(listOf(THURSDAY)))
    }

    @Test
    fun `single day and late windows`() {
        assertEquals("Mi · 08–17 Uhr", texts.summary(schedule(WEDNESDAY)))
        assertEquals("Mo, Mi, Fr · 06–24 Uhr", texts.summary(schedule(MONDAY, WEDNESDAY, FRIDAY, window = ActiveWindow.ofHours(6, 24))))
    }

    @Test
    fun `windows with minutes show times`() {
        assertEquals("08:30–17:15", texts.window(ActiveWindow(8 * 60 + 30, 17 * 60 + 15)))
        assertEquals("08:00–12:30", texts.window(ActiveWindow(8 * 60, 12 * 60 + 30)))
        assertEquals("22:15–24:00", texts.window(ActiveWindow(22 * 60 + 15, 24 * 60)))
    }

    @Test
    fun `time options stay inside the window bounds`() {
        val window = ActiveWindow.ofHours(8, 17)
        val starts = TimeOptions.startOptions(window)
        assertEquals(0, starts.first())
        assertEquals(16 * 60 + 45, starts.last())
        val ends = TimeOptions.endOptions(window)
        assertEquals(8 * 60 + 15, ends.first())
        assertEquals(24 * 60, ends.last())
        assertEquals(listOf(24 * 60), TimeOptions.endOptions(ActiveWindow(23 * 60 + 50, 24 * 60)))
        assertEquals(starts.indexOf(8 * 60), TimeOptions.initialIndex(starts, 8 * 60))
        assertEquals(starts.lastIndex, TimeOptions.initialIndex(starts, 23 * 60))
    }
}
