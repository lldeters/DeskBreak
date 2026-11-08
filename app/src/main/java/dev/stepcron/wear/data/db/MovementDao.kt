package dev.stepcron.wear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Zugriff auf die Bewegungs-Historie.
 *
 * Bewegungs-Ereignisse (erfolgreiche Intervalle) sind COMPLETED_REMINDER, AUTO_STEP_RESET und MANUAL_RESET.
 */
@Dao
abstract class MovementDao {

    @Insert
    abstract suspend fun insert(event: MovementEvent): Long

    @Insert
    abstract suspend fun insertAll(events: List<MovementEvent>)

    /** Alle Ereignisse im Bereich [fromMillis, toMillis) – z. B. „heute ab Mitternacht". */
    @Query(
        """
        SELECT * FROM movement_events
        WHERE timestamp >= :fromMillis AND timestamp < :toMillis
        ORDER BY timestamp ASC
        """,
    )
    abstract fun observeEventsBetween(fromMillis: Long, toMillis: Long): Flow<List<MovementEvent>>

    /**
     * Tageweise aggregierte Zähler ab [fromMillis] (z. B. die letzten 7 Tage).
     * Der Tag wird in der lokalen Zeitzone des Geräts gebildet.
     */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day,
               SUM(CASE WHEN eventType IN ('COMPLETED_REMINDER', 'AUTO_STEP_RESET', 'MANUAL_RESET')
                        THEN 1 ELSE 0 END) AS movementCount,
               SUM(CASE WHEN eventType = 'COMPLETED_REMINDER' THEN 1 ELSE 0 END) AS completedCount,
               SUM(CASE WHEN eventType = 'AUTO_STEP_RESET' THEN 1 ELSE 0 END) AS autoStepCount,
               SUM(CASE WHEN eventType = 'MANUAL_RESET' THEN 1 ELSE 0 END) AS manualCount,
               SUM(CASE WHEN eventType = 'SNOOZED' THEN 1 ELSE 0 END) AS snoozeCount,
               SUM(CASE WHEN eventType = 'MISSED' THEN 1 ELSE 0 END) AS missedCount
        FROM movement_events
        WHERE timestamp >= :fromMillis
        GROUP BY day
        ORDER BY day ASC
        """,
    )
    abstract fun observeDailyStats(fromMillis: Long): Flow<List<DailyMovementStats>>

    /**
     * Zähler je Stunde des Tages (0–23, lokale Zeit) über alle Tage ab [fromMillis] – Grundlage für die beste
     * und die schwächste Tageszeit. Verpasste Erinnerungen zählen zur Stunde, in der sie fällig waren.
     */
    @Query(
        """
        SELECT CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
               SUM(CASE WHEN eventType IN ('COMPLETED_REMINDER', 'AUTO_STEP_RESET', 'MANUAL_RESET')
                        THEN 1 ELSE 0 END) AS movementCount,
               SUM(CASE WHEN eventType = 'MISSED' THEN 1 ELSE 0 END) AS missedCount
        FROM movement_events
        WHERE timestamp >= :fromMillis
        GROUP BY hour
        ORDER BY hour ASC
        """,
    )
    abstract fun observeHourlyStats(fromMillis: Long): Flow<List<HourlyMovementStats>>

    /** Aktuelle Serie: erfolgreiche Intervalle seit der letzten verpassten Erinnerung. */
    @Query(
        """
        SELECT COUNT(*) FROM movement_events
        WHERE eventType IN ('COMPLETED_REMINDER', 'AUTO_STEP_RESET', 'MANUAL_RESET')
          AND timestamp > COALESCE(
                (SELECT MAX(timestamp) FROM movement_events WHERE eventType = 'MISSED'), -1)
        """,
    )
    abstract fun observeCurrentStreak(): Flow<Int>

    @Query("DELETE FROM movement_events WHERE timestamp >= :fromMillis AND timestamp < :toMillis")
    abstract suspend fun deleteBetween(fromMillis: Long, toMillis: Long): Int

    @Query("DELETE FROM movement_events")
    abstract suspend fun deleteAll(): Int

    /** Ersetzt alle Ereignisse im Bereich [fromMillis, toMillis) atomar durch [events]. */
    @Transaction
    open suspend fun replaceBetween(fromMillis: Long, toMillis: Long, events: List<MovementEvent>) {
        deleteBetween(fromMillis, toMillis)
        insertAll(events)
    }
}

/** Ergebniszeile von [MovementDao.observeHourlyStats]. */
data class HourlyMovementStats(
    val hour: Int,
    val movementCount: Int,
    val missedCount: Int,
)

/** Ergebniszeile von [MovementDao.observeDailyStats]. [day] im Format `yyyy-MM-dd`. */
data class DailyMovementStats(
    val day: String,
    val movementCount: Int,
    val completedCount: Int,
    val autoStepCount: Int,
    val manualCount: Int,
    val snoozeCount: Int,
    val missedCount: Int,
)
