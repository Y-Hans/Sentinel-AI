package com.sentinel.ai.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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
        // Step 1: Create an actual version-1 database using FrameworkSQLiteOpenHelper
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // This is the exact schema for ThreatRecordEntity from version 1
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

        // Step 2: Insert representative ThreatRecordEntity data
        db.execSQL("""
            INSERT INTO threat_records (id, recordType, source, senderDisplayName, senderIdentifier, content, riskLevel, riskScore, explanation, recommendation, timestamp)
            VALUES ('test_id_1', 'THREAT', 'SMS', 'John', '+123', 'Click here', 'RED', 85.0, 'Phishing', 'Block', 1234567890)
        """.trimIndent())
        
        // Step 3: Close the database
        db.close()
        helper.close()

        // Step 4: Open/migrate it to version 2 using Room and the actual MIGRATION_1_2
        val sentinelDatabase = Room.databaseBuilder(
            context,
            SentinelDatabase::class.java,
            TEST_DB
        ).addMigrations(SentinelDatabase.MIGRATION_1_2).build()

        // Verify the old ThreatRecordEntity row still exists
        val threatDao = sentinelDatabase.threatDao()
        // We will just query directly
        val cursor = sentinelDatabase.query("SELECT * FROM threat_records", null)
        assertEquals(1, cursor.count)
        cursor.moveToFirst()
        assertEquals("test_id_1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
        assertEquals("THREAT", cursor.getString(cursor.getColumnIndexOrThrow("recordType")))
        cursor.close()

        // Verify URL reputation table exists and DAO operations work
        val urlRepDao = sentinelDatabase.urlReputationDao()
        val urlEntity = UrlReputationEntity(
            target = "https://example.com",
            type = "URL",
            verdict = "MALICIOUS",
            confidence = 0.9f,
            firstSeenTimestamp = 1000L,
            lastSeenTimestamp = 2000L,
            scanCount = 1,
            latestHeuristicScore = 80f,
            latestMlScore = 85f,
            latestFinalScore = 88f,
            reasons = "Malicious URL"
        )
        urlRepDao.upsertReputation(urlEntity)
        
        val queried = urlRepDao.getReputation("https://example.com", "URL")
        assertNotNull(queried)
        assertEquals("MALICIOUS", queried?.verdict)
        
        sentinelDatabase.close()
    }
}
