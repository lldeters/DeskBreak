package dev.stepcron.wear.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * [MovementDao] im Speicher, damit das echte MovementRepository ohne Room auf der JVM läuft. Die nicht
 * abstrakten Methoden (z. B. [replaceAll]) kommen unverändert aus dem DAO. Die Auswertungen liefern nichts.
 */
internal class FakeMovementDao(initial: List<MovementEvent> = emptyList()) : MovementDao() {

    private val stored = mutableListOf<MovementEvent>()
    private var nextId = 1L

    /** Gespeicherte Ereignisse, älteste zuerst. */
    val rows: List<MovementEvent> get() = stored.sortedBy { it.timestamp }

    init {
        initial.forEach(::store)
    }

    private fun store(event: MovementEvent): Long {
        val id = nextId++
        stored += event.copy(id = id)
        return id
    }

    override suspend fun insert(event: MovementEvent): Long = store(event)

    override suspend fun insertAll(events: List<MovementEvent>) {
        events.forEach(::store)
    }

    override fun observeEventsBetween(fromMillis: Long, toMillis: Long): Flow<List<MovementEvent>> =
        flowOf(rows.filter { it.timestamp in fromMillis until toMillis })

    override suspend fun eventsSince(fromMillis: Long): List<MovementEvent> = rows.filter { it.timestamp >= fromMillis }

    override fun observeDailyStats(fromMillis: Long): Flow<List<DailyMovementStats>> = flowOf(emptyList())

    override fun observeHourlyStats(fromMillis: Long): Flow<List<HourlyMovementStats>> = flowOf(emptyList())

    override fun observeCurrentStreak(): Flow<Int> = flowOf(0)

    override suspend fun deleteBetween(fromMillis: Long, toMillis: Long): Int {
        val before = stored.size
        stored.removeAll { it.timestamp in fromMillis until toMillis }
        return before - stored.size
    }

    override suspend fun deleteAll(): Int {
        val count = stored.size
        stored.clear()
        return count
    }
}
