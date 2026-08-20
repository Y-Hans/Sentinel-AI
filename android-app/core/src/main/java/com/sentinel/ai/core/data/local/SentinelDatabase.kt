package com.sentinel.ai.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ThreatRecordEntity::class, UrlReputationEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SentinelDatabase : RoomDatabase() {

    abstract fun threatDao(): ThreatDao
    abstract fun urlReputationDao(): UrlReputationDao

    companion object {
        private const val DATABASE_NAME = "sentinel_db"

        @Volatile
        private var INSTANCE: SentinelDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `url_reputation` (
                        `target` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `verdict` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `firstSeenTimestamp` INTEGER NOT NULL,
                        `lastSeenTimestamp` INTEGER NOT NULL,
                        `scanCount` INTEGER NOT NULL,
                        `latestHeuristicScore` REAL NOT NULL,
                        `latestMlScore` REAL NOT NULL,
                        `latestFinalScore` REAL NOT NULL,
                        `reasons` TEXT NOT NULL,
                        PRIMARY KEY(`target`)
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): SentinelDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SentinelDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
        }
    }
}
