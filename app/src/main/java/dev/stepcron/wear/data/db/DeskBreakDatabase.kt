package dev.stepcron.wear.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [MovementEvent::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DeskBreakDatabase : RoomDatabase() {

    abstract fun movementDao(): MovementDao

    companion object {
        private const val DATABASE_NAME = "deskbreak.db"

        fun create(context: Context): DeskBreakDatabase =
            Room.databaseBuilder(context.applicationContext, DeskBreakDatabase::class.java, DATABASE_NAME)
                .build()
    }
}
