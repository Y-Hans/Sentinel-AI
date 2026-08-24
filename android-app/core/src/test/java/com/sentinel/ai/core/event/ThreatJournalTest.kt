package com.sentinel.ai.core.event

import com.sentinel.ai.core.data.local.ThreatDao
import com.sentinel.ai.core.data.local.ThreatRecordEntity
import com.sentinel.ai.core.data.local.toEntity
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.core.model.Threat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ThreatJournalTest {

    private lateinit var fakeDao: FakeThreatDao

    @Before
    fun setUp() {
        fakeDao = FakeThreatDao()
        ThreatJournal.resetForTesting()
    }

    @After
    fun tearDown() {
        ThreatJournal.resetForTesting()
    }

    @Test
    fun `test1 A ScanResult is persisted to Room`() = runTest {
        ThreatJournal.initialize(fakeDao, preload = false)

        val scanResult = createSampleScanResult(id = "scan-1", timestamp = 1_700_000_000_000L)
        ThreatJournal.recordScanResult(scanResult)

        assertEquals(1, fakeDao.persistedRecords.size)
        val entity = fakeDao.persistedRecords["scan-1|${ThreatRecordEntity.TYPE_SCAN_RESULT}"]
        assertNotNull("Record should be persisted in DAO", entity)
        assertEquals("scan-1", entity?.id)
        assertEquals(ThreatRecordEntity.TYPE_SCAN_RESULT, entity?.recordType)
        assertEquals("com.whatsapp", entity?.source)
        assertEquals("RED", entity?.riskLevel)
        assertEquals(80f, entity?.riskScore)
    }

    @Test
    fun `test2 In-memory StateFlow is updated after successful persistence`() = runTest {
        ThreatJournal.initialize(fakeDao, preload = false)

        val scanResult = createSampleScanResult(id = "scan-2", timestamp = 1_700_000_000_000L)
        ThreatJournal.recordScanResult(scanResult)

        val inMemoryScans = ThreatJournal.scanResults.value
        assertEquals(1, inMemoryScans.size)
        assertEquals("scan-2", inMemoryScans.first().id)

        val inMemoryAlerts = ThreatJournal.alerts.value
        assertEquals(1, inMemoryAlerts.size)
        assertEquals("scan-2", inMemoryAlerts.first().id)
    }

    @Test
    fun `test3 A Room failure does not silently appear as successful persistence`() = runTest {
        fakeDao.shouldFailUpsert = true
        ThreatJournal.initialize(fakeDao, preload = false)

        val scanResult = createSampleScanResult(id = "scan-fail", timestamp = 1_700_000_000_000L)

        try {
            ThreatJournal.recordScanResult(scanResult)
            fail("Expected exception was not thrown on Room persistence failure")
        } catch (e: Exception) {
            assertTrue("Expected IOException", e is IOException)
        }

        // Verify in-memory state was NOT updated when DB write failed
        assertTrue("scanResults StateFlow must be empty on DB failure", ThreatJournal.scanResults.value.isEmpty())
        assertTrue("alerts StateFlow must be empty on DB failure", ThreatJournal.alerts.value.isEmpty())
    }

    @Test
    fun `test4 Suspending method does not complete before DAO operation completes`() = runTest {
        val daoGate = CompletableDeferred<Unit>()
        val executionOrder = mutableListOf<String>()

        val blockingDao = object : ThreatDao {
            val records = mutableMapOf<String, ThreatRecordEntity>()

            override suspend fun getRecentThreatRecords(limit: Int): List<ThreatRecordEntity> = records.values.toList()

            override suspend fun getThreatRecordsBefore(recordType: String, cursorTimestamp: Long, cursorId: String, limit: Int): List<ThreatRecordEntity> = emptyList()

            override suspend fun upsertThreatRecord(record: ThreatRecordEntity) {
                executionOrder += "dao_start"
                daoGate.await() // Hold DAO write until resumed
                records["${record.id}|${record.recordType}"] = record
                executionOrder += "dao_end"
            }
        }

        ThreatJournal.initialize(blockingDao, preload = false)

        val scanResult = createSampleScanResult(id = "scan-order", timestamp = 1_700_000_000_000L)

        val recordDeferred = async {
            ThreatJournal.recordScanResult(scanResult)
            executionOrder += "method_return"
        }

        // At this point, the DAO write is suspended
        assertTrue("StateFlow must not be updated before DAO completion", ThreatJournal.scanResults.value.isEmpty())

        // Release the DAO gate
        daoGate.complete(Unit)
        recordDeferred.await()

        assertEquals(
            listOf("dao_start", "dao_end", "method_return"),
            executionOrder
        )
        assertEquals(1, ThreatJournal.scanResults.value.size)
        assertEquals("scan-order", ThreatJournal.scanResults.value.first().id)
    }

    @Test
    fun `test5 Existing history initialization behavior still works`() = runTest {
        val preExistingScan = createSampleScanResult(id = "pre-scan-1", timestamp = 1_700_000_001_000L)
        val preExistingThreat = Threat(
            id = "pre-threat-1",
            source = "com.google.android.gm",
            senderDisplayName = "Phisher",
            senderIdentifier = "attacker@scam.com",
            content = "Suspicious email content",
            riskLevel = RiskLevel.CRITICAL,
            riskScore = 95f,
            explanation = "Known credential scam",
            recommendation = "Block sender",
            timestamp = 1_700_000_002_000L
        )

        fakeDao.persistedRecords["pre-scan-1|${ThreatRecordEntity.TYPE_SCAN_RESULT}"] = preExistingScan.toEntity()
        fakeDao.persistedRecords["pre-threat-1|${ThreatRecordEntity.TYPE_THREAT}"] = preExistingThreat.toEntity()

        ThreatJournal.initialize(fakeDao, preload = true)

        assertEquals(1, ThreatJournal.scanResults.value.size)
        assertEquals("pre-scan-1", ThreatJournal.scanResults.value.first().id)

        assertEquals(1, ThreatJournal.threats.value.size)
        assertEquals("pre-threat-1", ThreatJournal.threats.value.first().id)

        assertEquals(2, ThreatJournal.alerts.value.size)
        // Alerts should be sorted descending by timestamp
        assertEquals("pre-threat-1", ThreatJournal.alerts.value[0].id)
        assertEquals("pre-scan-1", ThreatJournal.alerts.value[1].id)
    }

    @Test
    fun `recordThreat persists and updates in-memory StateFlows`() = runTest {
        ThreatJournal.initialize(fakeDao, preload = false)

        val threat = Threat(
            id = "threat-1",
            source = "com.whatsapp",
            senderDisplayName = "Scammer",
            senderIdentifier = "+1234567890",
            content = "Send money",
            riskLevel = RiskLevel.RED,
            riskScore = 75f,
            explanation = "Financial urgency",
            recommendation = "Do not pay",
            timestamp = 1_700_000_003_000L
        )

        ThreatJournal.recordThreat(threat)

        assertEquals(1, fakeDao.persistedRecords.size)
        assertEquals(1, ThreatJournal.threats.value.size)
        assertEquals("threat-1", ThreatJournal.threats.value.first().id)
        assertEquals(1, ThreatJournal.alerts.value.size)
    }

    @Test
    fun `record delegating ThreatEvent persists scanResult properly`() = runTest {
        ThreatJournal.initialize(fakeDao, preload = false)

        val scanResult = createSampleScanResult(id = "event-scan-1", timestamp = 1_700_000_004_000L)
        ThreatJournal.record(ThreatEvent.WhatsAppThreatDetected(scanResult))

        assertEquals(1, fakeDao.persistedRecords.size)
        assertEquals(1, ThreatJournal.scanResults.value.size)
        assertEquals("event-scan-1", ThreatJournal.scanResults.value.first().id)
    }

    @Test
    fun `uninitialized ThreatJournal throws IllegalStateException`() = runTest {
        val scanResult = createSampleScanResult(id = "uninit-scan", timestamp = 1_700_000_000_000L)

        try {
            ThreatJournal.recordScanResult(scanResult)
            fail("Expected IllegalStateException for uninitialized ThreatJournal")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("not initialized") == true)
        }
    }

    @Test
    fun `recording duplicate scanResult replaces existing entry by ID`() = runTest {
        ThreatJournal.initialize(fakeDao, preload = false)

        val scan1 = createSampleScanResult(id = "duplicate-id", timestamp = 1_700_000_000_000L, score = 50f)
        val scan2 = createSampleScanResult(id = "duplicate-id", timestamp = 1_700_000_010_000L, score = 90f)

        ThreatJournal.recordScanResult(scan1)
        ThreatJournal.recordScanResult(scan2)

        assertEquals(1, fakeDao.persistedRecords.size)
        assertEquals(1, ThreatJournal.scanResults.value.size)
        assertEquals(90f, ThreatJournal.scanResults.value.first().riskScore)
    }

    @Test
    fun `threatFor and observeThreat return matching item`() = runTest {
        ThreatJournal.initialize(fakeDao, preload = false)

        val scan = createSampleScanResult(id = "lookup-id", timestamp = 1_700_000_000_000L)
        ThreatJournal.recordScanResult(scan)

        val found = ThreatJournal.threatFor("lookup-id")
        assertNotNull(found)
        assertEquals("lookup-id", found?.id)
        assertEquals("com.whatsapp", found?.source)
    }

    @Test
    fun `concurrent writes to recordScanResult maintain StateFlow consistency and thread safety`() = runTest {
        ThreatJournal.initialize(fakeDao, preload = false)

        val count = 20
        val jobs = (1..count).map { i ->
            async {
                val scan = createSampleScanResult(
                    id = "concurrent-scan-$i",
                    timestamp = 1_700_000_000_000L + (i * 1000L),
                    score = (50 + i).toFloat()
                )
                ThreatJournal.recordScanResult(scan)
            }
        }
        jobs.awaitAll()

        assertEquals(count, fakeDao.persistedRecords.size)
        assertEquals(count, ThreatJournal.scanResults.value.size)
        assertEquals(count, ThreatJournal.alerts.value.size)
        // Verify list is strictly sorted descending by timestamp
        val timestamps = ThreatJournal.scanResults.value.map { it.timestamp }
        assertEquals(timestamps.sortedDescending(), timestamps)
    }

    @Test
    fun `test6 Concurrent initialization and record keeps newly recorded scan in memory`() = runTest {
        val daoGate = CompletableDeferred<Unit>()
        val executionOrder = mutableListOf<String>()
        val blockingDao = object : ThreatDao {
            val records = mutableMapOf<String, ThreatRecordEntity>()
            override suspend fun getRecentThreatRecords(limit: Int): List<ThreatRecordEntity> {
                executionOrder += "restore_start"
                daoGate.await()
                executionOrder += "restore_end"
                return records.values.toList()
            }
            override suspend fun getThreatRecordsBefore(recordType: String, cursorTimestamp: Long, cursorId: String, limit: Int): List<ThreatRecordEntity> = emptyList()
            override suspend fun upsertThreatRecord(record: ThreatRecordEntity) {
                records["${record.id}|${record.recordType}"] = record
            }
        }

        ThreatJournal.initialize(blockingDao, preload = false)
        val deferredRestore = async(kotlinx.coroutines.Dispatchers.IO) { ThreatJournal.restoreState() }

        val newScan = createSampleScanResult(id = "concurrent-scan", timestamp = 1_700_000_000_000L)
        ThreatJournal.recordScanResult(newScan)

        daoGate.complete(Unit)
        deferredRestore.await()

        assertEquals("concurrent-scan", ThreatJournal.scanResults.value.first().id)
    }

    private fun createSampleScanResult(
        id: String,
        timestamp: Long,
        score: Float = 80f
    ): ScanResult = ScanResult(
        id = id,
        source = "com.whatsapp",
        senderDisplayName = "John Doe",
        senderIdentifier = "+919876543210",
        riskLevel = RiskLevel.RED,
        riskScore = score,
        explanation = "Suspicious financial request detected",
        timestamp = timestamp,
        decision = ProtectionDecision.WARN
    )

    private class FakeThreatDao : ThreatDao {
        val persistedRecords = mutableMapOf<String, ThreatRecordEntity>()
        var shouldFailUpsert = false

        override suspend fun getRecentThreatRecords(limit: Int): List<ThreatRecordEntity> {
            return persistedRecords.values.sortedByDescending { it.timestamp }
        }

        override suspend fun getThreatRecordsBefore(recordType: String, cursorTimestamp: Long, cursorId: String, limit: Int): List<ThreatRecordEntity> {
            return persistedRecords.values
                .filter {
                    it.recordType == recordType &&
                        (it.timestamp < cursorTimestamp || (it.timestamp == cursorTimestamp && it.id < cursorId))
                }
                .sortedWith(compareByDescending<ThreatRecordEntity> { it.timestamp }.thenByDescending { it.id })
                .take(limit)
        }

        override suspend fun upsertThreatRecord(record: ThreatRecordEntity) {
            if (shouldFailUpsert) {
                throw IOException("Simulated disk I/O / SQLite failure")
            }
            persistedRecords["${record.id}|${record.recordType}"] = record
        }
    }
}
