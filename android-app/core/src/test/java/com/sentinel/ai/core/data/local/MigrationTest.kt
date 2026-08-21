package com.sentinel.ai.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33])
class MigrationTest {

    private val TEST_DB = "migration-test-db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val dbFile = context.getDatabasePath(TEST_DB)
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @After
    fun tearDown() {
        val dbFile = context.getDatabasePath(TEST_DB)
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @Test
    fun migrate1To2() = runBlocking {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `threat_records` (
                            `id` TEXT NOT NULL,
                            `recordType` TEXT NOT NULL,
                            `source` TEXT NOT NULL,
                            `senderDisplayName` TEXT,
                            `senderIdentifier` TEXT,
                            `content` TEXT,
                            `riskLevel` TEXT NOT NULL,
                            `riskScore` REAL NOT NULL,
                            `explanation` TEXT NOT NULL,
                            `recommendation` TEXT,
                            `timestamp` INTEGER NOT NULL,
                            PRIMARY KEY(`id`, `recordType`)
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        db.execSQL("""
            INSERT INTO threat_records (id, recordType, source, senderDisplayName, senderIdentifier, content, riskLevel, riskScore, explanation, recommendation, timestamp)
            VALUES ('test_id_1', 'THREAT', 'SMS', 'John', '+123', 'Click here', 'RED', 85.0, 'Phishing', 'Block', 1234567890)
        """.trimIndent())
        
        db.close()
        helper.close()

        val sentinelDatabase = Room.databaseBuilder(
            context,
            SentinelDatabase::class.java,
            TEST_DB
        ).addMigrations(SentinelDatabase.MIGRATION_1_2, SentinelDatabase.MIGRATION_2_3, SentinelDatabase.MIGRATION_3_4).build()

        val cursor = sentinelDatabase.query("SELECT * FROM threat_records", null)
        assertEquals(1, cursor.count)
        cursor.close()

        sentinelDatabase.close()
    }

    @Test
    fun migrate3To4() = runBlocking {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `threat_records` (
                            `id` TEXT NOT NULL,
                            `recordType` TEXT NOT NULL,
                            `source` TEXT NOT NULL,
                            `senderDisplayName` TEXT,
                            `senderIdentifier` TEXT,
                            `content` TEXT,
                            `riskLevel` TEXT NOT NULL,
                            `riskScore` REAL NOT NULL,
                            `explanation` TEXT NOT NULL,
                            `recommendation` TEXT,
                            `timestamp` INTEGER NOT NULL,
                            PRIMARY KEY(`id`, `recordType`)
                        )
                    """.trimIndent())
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
                            `safeCount` INTEGER NOT NULL DEFAULT 0,
                            `suspiciousCount` INTEGER NOT NULL DEFAULT 0,
                            `maliciousCount` INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY(`target`)
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        db.execSQL("""
            INSERT INTO threat_records (id, recordType, source, senderDisplayName, senderIdentifier, content, riskLevel, riskScore, explanation, recommendation, timestamp)
            VALUES ('test_id_1', 'THREAT', 'SMS', 'John', '+123', 'Click here', 'RED', 85.0, 'Phishing', 'Block', 1234567890)
        """.trimIndent())

        db.execSQL("""
            INSERT INTO url_reputation (target, type, verdict, confidence, firstSeenTimestamp, lastSeenTimestamp, scanCount, latestHeuristicScore, latestMlScore, latestFinalScore, reasons, safeCount, suspiciousCount, maliciousCount)
            VALUES ('https://example.com', 'URL', 'MALICIOUS', 0.9, 1000, 2000, 1, 80.0, 85.0, 88.0, 'Malicious URL', 0, 0, 1)
        """.trimIndent())
        
        db.close()
        helper.close()

        val sentinelDatabase = Room.databaseBuilder(
            context,
            SentinelDatabase::class.java,
            TEST_DB
        ).addMigrations(SentinelDatabase.MIGRATION_3_4).build()

        val cursor = sentinelDatabase.query("SELECT * FROM threat_records", null)
        assertEquals(1, cursor.count)
        cursor.close()

        sentinelDatabase.close()
    }
}
