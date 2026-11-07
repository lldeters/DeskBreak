package dev.stepcron.wear.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Art eines Bewegungs-Ereignisses in der Historie. */
enum class MovementEventType {
    /** Erinnerung wurde per „Erledigt" bestätigt. */
    COMPLETED_REMINDER,

    /** Automatisch erkannt: mindestens 30 Schritte innerhalb von 2 Minuten. */
    AUTO_STEP_RESET,

    /** Nutzer hat „Jetzt bewegt (Timer Reset)" gedrückt. */
    MANUAL_RESET,

    /** Erinnerung wurde um 10 Minuten verschoben. */
    SNOOZED,

    /** Erinnerung blieb unbestätigt, bis die nächste fällig wurde. */
    MISSED;

    /** `true`, wenn das Ereignis ein erfolgreich abgeschlossenes Bewegungs-Intervall darstellt. */
    val isMovement: Boolean
        get() = this == COMPLETED_REMINDER || this == AUTO_STEP_RESET || this == MANUAL_RESET
}

/** Ein einzelnes Ereignis der Bewegungs-Historie (Grundlage aller Graphen). */
@Entity(
    tableName = "movement_events",
    indices = [Index(value = ["timestamp"])],
)
data class MovementEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Zeitpunkt in Epoch-Millis. Bei [MovementEventType.MISSED] der Zeitpunkt der verpassten Erinnerung. */
    val timestamp: Long,
    val eventType: MovementEventType,
    /** Das zu diesem Zeitpunkt eingestellte Erinnerungs-Intervall. */
    val intervalMinutes: Int,
)
