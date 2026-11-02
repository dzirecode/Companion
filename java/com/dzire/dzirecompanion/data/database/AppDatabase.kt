package com.dzire.dzirecompanion.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LifeEvent::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lifeEventDao(): LifeEventDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE life_events ADD COLUMN numericValue REAL")
                db.execSQL("ALTER TABLE life_events ADD COLUMN unit TEXT")
                db.execSQL("ALTER TABLE life_events ADD COLUMN rating REAL")
                db.execSQL("ALTER TABLE life_events ADD COLUMN durationMs INTEGER")
                db.execSQL("ALTER TABLE life_events ADD COLUMN source TEXT NOT NULL DEFAULT 'USER'")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "companion_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
