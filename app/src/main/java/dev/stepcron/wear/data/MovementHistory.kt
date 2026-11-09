package dev.stepcron.wear.data

import dev.stepcron.wear.data.db.MovementEvent

/**
 * Die Bewegungs-Historie als Ganzes – für den Abgleich mit dem Handy (Snapshot, Wiederherstellung).
 * In der App: [MovementRepository], in den Unit-Tests eine Attrappe.
 */
interface MovementHistory {
    /** Alle Ereignisse ab [fromMillis] (einschließlich), älteste zuerst. */
    suspend fun eventsSince(fromMillis: Long): List<MovementEvent>

    /** Ersetzt die gesamte Historie atomar durch [events]: entweder alles oder nichts. */
    suspend fun replaceAll(events: List<MovementEvent>)
}
