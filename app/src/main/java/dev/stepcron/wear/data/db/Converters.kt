package dev.stepcron.wear.data.db

import androidx.room.TypeConverter

/** Speichert [MovementEventType] als lesbaren Text (Name der Konstante). */
class Converters {

    @TypeConverter
    fun fromEventType(type: MovementEventType): String = type.name

    @TypeConverter
    fun toEventType(value: String): MovementEventType =
        MovementEventType.entries.firstOrNull { it.name == value } ?: MovementEventType.MISSED
}
