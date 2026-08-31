package com.recall.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Deck::class, Card::class, ReviewLog::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class RecallDatabase : RoomDatabase() {

    abstract fun dao(): RecallDao

    companion object {
        // `@Volatile` + synchronized = the classic thread-safe singleton,
        // same idea as in Java.
        @Volatile
        private var instance: RecallDatabase? = null

        fun get(context: Context): RecallDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecallDatabase::class.java,
                    "recall.db"
                )
                    .addMigrations(*MIGRATIONS)
                    // NOTE: there is deliberately no fallbackToDestructiveMigration()
                    // here. If you bump `version` and forget the migration, the app
                    // crashes on launch with a clear message instead of silently
                    // deleting every card you own. A crash you fix in five minutes
                    // beats data you cannot get back.
                    .build()
                    .also { instance = it }
            }
    }
}
