package dev.stepcron.wear.data

import dev.stepcron.wear.data.db.MovementEvent
import dev.stepcron.wear.data.db.MovementEventType
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.domain.toLocalDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Erzeugt realistische Demo-Daten für die letzten 7 Tage (inklusive heute bis „jetzt"),
 * damit die Graphen sofort auf der Uhr getestet werden können.
 * Bereits vorhandene Ereignisse in diesem Zeitraum werden ersetzt.
 */
class DemoDataSeeder(
    private val repository: MovementRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
) {

    /** @return Anzahl der erzeugten Ereignisse. */
    suspend fun seedLastSevenDays(settings: ReminderSettings): Int {
        val zone = zoneProvider()
        val now = clock()
        val today = now.toLocalDate(zone)
        val from = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val random = Random(today.toEpochDay())

        val events = (6 downTo 0).flatMap { offset ->
            generateDay(today.minusDays(offset.toLong()), settings, zone, now, random)
        }
        repository.replaceBetween(from, now + 1, events)
        return events.size
    }

    private fun generateDay(
        date: LocalDate,
        settings: ReminderSettings,
        zone: ZoneId,
        now: Long,
        random: Random,
    ): List<MovementEvent> {
        // Freie Tage bekommen keine Ereignisse.
        val window = settings.schedule.windowOn(date) ?: return emptyList()
        val interval = settings.intervalMillis
        val windowEnd = window.endOn(date, zone)
        // „Tagesform": am Wochenende weniger diszipliniert, unter der Woche schwankend.
        val discipline = when (date.dayOfWeek) {
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> 0.5 + random.nextDouble() * 0.15
            else -> 0.68 + random.nextDouble() * 0.27
        }

        val events = mutableListOf<MovementEvent>()
        fun add(timestamp: Long, type: MovementEventType) {
            events += MovementEvent(timestamp = timestamp, eventType = type, intervalMinutes = settings.intervalMinutes)
        }

        var cycleStart = window.startOn(date, zone)
        while (true) {
            val reminderAt = cycleStart + interval
            if (reminderAt >= windowEnd || reminderAt > now) break
            val roll = random.nextDouble()
            when {
                // Eigenständig gegangen, bevor die Erinnerung kam.
                roll < discipline * 0.35 -> {
                    val at = cycleStart + (interval * (0.45 + random.nextDouble() * 0.5)).toLong()
                    add(at, MovementEventType.AUTO_STEP_RESET)
                    cycleStart = at
                }
                // Manuell „Jetzt bewegt" gedrückt.
                roll < discipline * 0.45 -> {
                    val at = cycleStart + (interval * (0.6 + random.nextDouble() * 0.35)).toLong()
                    add(at, MovementEventType.MANUAL_RESET)
                    cycleStart = at
                }
                // Erinnerung kam und wurde erledigt.
                roll < discipline -> {
                    val at = reminderAt + random.nextLong(30_000L, 4 * 60_000L)
                    if (at > now) break
                    add(at, MovementEventType.COMPLETED_REMINDER)
                    cycleStart = reminderAt
                }
                // Erst Snooze, danach erledigt.
                roll < discipline + (1 - discipline) * 0.45 -> {
                    val snoozeAt = reminderAt + random.nextLong(20_000L, 90_000L)
                    val doneAt = snoozeAt + SNOOZE_MILLIS + random.nextLong(10_000L, 2 * 60_000L)
                    if (doneAt > now || doneAt >= windowEnd) break
                    add(snoozeAt, MovementEventType.SNOOZED)
                    add(doneAt, MovementEventType.COMPLETED_REMINDER)
                    cycleStart = snoozeAt + SNOOZE_MILLIS
                }
                // Erinnerung ignoriert.
                else -> {
                    add(reminderAt, MovementEventType.MISSED)
                    cycleStart = reminderAt
                }
            }
        }
        return events
    }

    private companion object {
        const val SNOOZE_MILLIS = 10 * 60_000L
    }
}
