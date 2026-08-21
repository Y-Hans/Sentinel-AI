package com.sentinel.ai.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ThreatRecordEntity::class],
    version = 4,
    exportSchema = false
)
abstract class SentinelDatabase : RoomDatabase() {

    abstract fun threatDao(): ThreatDao

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

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `url_reputation` ADD COLUMN `safeCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `url_reputation` ADD COLUMN `suspiciousCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `url_reputation` ADD COLUMN `maliciousCount` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `url_reputation`")
            }
        }

        fun getInstance(context: Context): SentinelDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SentinelDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { INSTANCE = it }
            }
        }
    }
}
