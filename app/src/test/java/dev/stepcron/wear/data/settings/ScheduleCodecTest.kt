package dev.stepcron.wear.data.settings

import dev.stepcron.wear.domain.ActiveWindow
import dev.stepcron.wear.domain.DaySchedule
import dev.stepcron.wear.domain.WeeklySchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class ScheduleCodecTest {

    @Test
    fun `encodes monday to sunday as enabled start and end minute`() {
        assertEquals(
            "1,480,1020;1,480,1020;1,480,1020;1,480,1020;1,480,1020;0,480,1020;0,480,1020",
            ScheduleCodec.encode(WeeklySchedule.DEFAULT),
        )
    }

    @Test
    fun `round trip keeps every day`() {
        val schedule = WeeklySchedule.DEFAULT
            .with(DayOfWeek.WEDNESDAY) { DaySchedule(enabled = false, window = ActiveWindow(7 * 60 + 45, 24 * 60)) }
            .with(DayOfWeek.SUNDAY) { DaySchedule(enabled = true, window = ActiveWindow(0, 15)) }
        assertEquals(schedule, ScheduleCodec.decode(ScheduleCodec.encode(schedule)))
    }

    @Test
    fun `malformed text is rejected`() {
        assertNull(ScheduleCodec.decode(""))
        assertNull(ScheduleCodec.decode("1,480,1020"))
        assertNull(ScheduleCodec.decode(List(7) { "1,480" }.joinToString(";")))
        assertNull(ScheduleCodec.decode(List(7) { "ja,480,1020" }.joinToString(";")))
        assertNull(ScheduleCodec.decode(List(7) { "1,acht,1020" }.joinToString(";")))
        assertNull(ScheduleCodec.decode(List(8) { "1,480,1020" }.joinToString(";")))
    }
}
