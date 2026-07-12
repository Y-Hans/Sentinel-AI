package com.sentinel.ai.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ThreatRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SentinelDatabase : RoomDatabase() {

    abstract fun threatDao(): ThreatDao

    companion object {
        private const val DATABASE_NAME = "sentinel_db"

        @Volatile
        private var INSTANCE: SentinelDatabase? = null

        fun getInstance(context: Context): SentinelDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SentinelDatabase::class.java,
                    DATABASE_NAME
                ).build().also { INSTANCE = it }
            }
        }
    }
}
