package dev.stepcron.wear.data

import dev.stepcron.wear.data.db.MovementEventType

/** Protokoll der Bewegungsereignisse für die Statistik (in der App: [MovementRepository]). */
interface MovementLog {
    suspend fun log(type: MovementEventType, timestampMillis: Long, intervalMinutes: Int): Long
}
