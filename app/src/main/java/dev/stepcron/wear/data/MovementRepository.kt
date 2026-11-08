package dev.stepcron.wear.data

import dev.stepcron.wear.data.db.MovementDao
import dev.stepcron.wear.data.db.MovementEvent
import dev.stepcron.wear.data.db.MovementEventType
import dev.stepcron.wear.domain.DailyCounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Schreib- und Lesezugriff auf die Bewegungs-Historie.
 *
 * Die Room-Datenbank wird erst beim ersten echten Zugriff gebaut – nicht schon, wenn ViewModel und
 * Engine beim App-Start (auf dem Main-Thread, im ersten Frame) das Repository erhalten.
 */
class MovementRepository(daoProvider: () -> MovementDao) {

    private val dao: MovementDao by lazy(daoProvider)

    suspend fun log(type: MovementEventType, timestampMillis: Long, intervalMinutes: Int): Long =
        dao.insert(
            MovementEvent(
                timestamp = timestampMillis,
                eventType = type,
                intervalMinutes = intervalMinutes,
            ),
        )

    /** Ereignisse im Bereich [fromMillis, toMillis), z. B. der heutige Tag ab Mitternacht. */
    fun eventsBetween(fromMillis: Long, toMillis: Long): Flow<List<MovementEvent>> =
        dao.observeEventsBetween(fromMillis, toMillis)

    /** Tages-Aggregate ab [fromMillis] (z. B. die letzten 7 Tage). */
    fun dailyCountsSince(fromMillis: Long): Flow<List<DailyCounts>> =
        dao.observeDailyStats(fromMillis).map { rows ->
            rows.mapNotNull { row ->
                val date = try {
                    LocalDate.parse(row.day)
                } catch (e: DateTimeParseException) {
                    return@mapNotNull null
                }
                DailyCounts(
                    date = date,
                    movements = row.movementCount,
                    snoozes = row.snoozeCount,
                    missed = row.missedCount,
                )
            }
        }

    /** Erfolgreiche Intervalle in Folge seit der letzten verpassten Erinnerung. */
    fun currentStreak(): Flow<Int> = dao.observeCurrentStreak()

    suspend fun replaceBetween(fromMillis: Long, toMillis: Long, events: List<MovementEvent>) =
        dao.replaceBetween(fromMillis, toMillis, events)

    suspend fun clearAll(): Int = dao.deleteAll()
}
