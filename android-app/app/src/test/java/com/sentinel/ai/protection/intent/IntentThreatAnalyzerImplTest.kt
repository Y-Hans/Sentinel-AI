package com.sentinel.ai.protection.intent

import android.net.Uri
import com.sentinel.ai.core.data.local.ThreatDao
import com.sentinel.ai.core.data.local.ThreatRecordEntity
import com.sentinel.ai.core.event.ThreatEventBus
import com.sentinel.ai.core.event.ThreatJournal
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.ml.MLInferenceEngine
import com.sentinel.ai.protection.intent.file.FileScanner
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRiskEngine
import com.sentinel.ai.protection.intent.link.LinkProtectionAgent
import com.sentinel.ai.protection.intent.link.LinkScanner
import com.sentinel.ai.protection.intent.model.FilePayload
import com.sentinel.ai.protection.intent.model.UrlPayload
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class IntentThreatAnalyzerImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeThreatDao

    @Before
    fun setUp() {
        fakeDao = FakeThreatDao()
        ThreatJournal.resetForTesting()
        ThreatJournal.initialize(fakeDao, preload = false)
    }

    @After
    fun tearDown() {
        ThreatJournal.resetForTesting()
    }

    @Test
    fun `supported URL local result is returned and persisted`() = runTest(testDispatcher) {
        val local = scanResult(35f, RiskLevel.YELLOW, target = "https://example.xyz")
        var scannedUrl: String? = null
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): ScanResult {
                    scannedUrl = url
                    return local
                }
            },
            mlScoreToReturn = null
        )

        val result = analyzer.analyze(UrlPayload("https://example.xyz"))

        assertEquals("https://example.xyz", scannedUrl)
        assertEquals(35f, result.riskScore, 0.01f) // ML is null here
        assertEquals(RiskLevel.GREEN, result.riskLevel) // Because 35f < 40f

        // Verify persisted to ThreatJournal / FakeThreatDao exactly once
        assertEquals(1, fakeDao.persistedRecords.size)
        val persisted = fakeDao.persistedRecords.values.first()
        assertEquals(result.id, persisted.id)
        assertEquals("https://example.xyz", persisted.content)
        assertEquals(ThreatRecordEntity.TYPE_SCAN_RESULT, persisted.recordType)
        assertEquals(RiskLevel.GREEN.name, persisted.riskLevel)
        assertEquals(1, ThreatJournal.scanResults.value.size)
    }

    @Test
    fun `file scan is returned and persisted to ThreatJournal`() = runTest(testDispatcher) {
        val fileResult = ScanResult(
            id = "file-id-123",
            source = "File",
            target = "document.pdf.exe",
            riskLevel = RiskLevel.CRITICAL,
            riskScore = 95f,
            explanation = "Double extension detected",
            timestamp = 1000L
        )
        val fakeUri = io.mockk.mockk<Uri>(relaxed = true)
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): ScanResult = error("Link scanner should not run")
            },
            mlScoreToReturn = null,
            fileScanner = object : FileScanner {
                override suspend fun scan(uri: Uri): ScanResult {
                    assertEquals(fakeUri, uri)
                    return fileResult
                }
            }
        )

        val result = analyzer.analyze(FilePayload(fakeUri))

        assertEquals(fileResult, result)
        assertEquals(1, fakeDao.persistedRecords.size)
        val persisted = fakeDao.persistedRecords.values.first()
        assertEquals("file-id-123", persisted.id)
        assertEquals("document.pdf.exe", persisted.content)
        assertEquals(RiskLevel.CRITICAL.name, persisted.riskLevel)
    }

    @Test
    fun `invalid URL returns a controlled explainable result and persists`() = runTest(testDispatcher) {
        val analyzer = analyzer(
            linkScanner = LinkProtectionAgent(LinkHeuristicRiskEngine()),
            mlScoreToReturn = null
        )

        val result = analyzer.analyze(UrlPayload("://broken"))

        assertEquals(ProtectionDecision.ALLOW, result.decision)
        assertTrue(result.riskScore in 0f..100f)
        assertNotNull(result.localEvidence)
        assertEquals(1, fakeDao.persistedRecords.size)
    }

    @Test
    fun `cancellation during local scan is propagated`() = runTest(testDispatcher) {
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): ScanResult = awaitCancellation()
            },
            mlScoreToReturn = null
        )
        val result = async {
            analyzer.analyze(UrlPayload("https://example.com"))
        }
        testDispatcher.scheduler.runCurrent()

        result.cancel()
        testDispatcher.scheduler.runCurrent()

        assertTrue(result.isCancelled)
        assertEquals(0, fakeDao.persistedRecords.size)
    }

    @Test
    fun `persistence failure throws exception without swallowing`() = runTest(testDispatcher) {
        fakeDao.shouldFailUpsert = true
        val local = scanResult(50f, RiskLevel.YELLOW)
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): ScanResult = local
            },
            mlScoreToReturn = null
        )

        try {
            analyzer.analyze(UrlPayload("https://example.com"))
            fail("Expected IOException on persistence failure")
        } catch (e: IOException) {
            assertEquals("Simulated database write error", e.message)
        }
    }

    @Test
    fun `final score incorporates heuristics and ML when ML score is 0`() = runTest(testDispatcher) {
        val local = scanResult(50f, RiskLevel.YELLOW) // 50 * 0.7 = 35
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): ScanResult = local
            },
            mlScoreToReturn = 0f // 0 * 0.3 = 0
        )
        val result = analyzer.analyze(UrlPayload("https://example.com"))
        
        // Expected score = 35 + 0 = 35
        assertEquals(35f, result.riskScore, 0.01f)
        assertEquals(RiskLevel.GREEN, result.riskLevel)
        assertEquals(ProtectionDecision.ALLOW, result.decision)
        assertEquals(1, fakeDao.persistedRecords.size)
    }

    @Test
    fun `final score incorporates heuristics and ML when ML score is 50`() = runTest(testDispatcher) {
        val local = scanResult(50f, RiskLevel.YELLOW) // 50 * 0.7 = 35
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): ScanResult = local
            },
            mlScoreToReturn = 0.5f // 50 * 0.3 = 15
        )
        val result = analyzer.analyze(UrlPayload("https://example.com"))
        
        // Expected score = 35 + 15 = 50
        assertEquals(50f, result.riskScore, 0.01f)
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
        assertEquals(ProtectionDecision.WARN, result.decision)
        assertEquals(1, fakeDao.persistedRecords.size)
    }

    @Test
    fun `final score incorporates heuristics and ML when ML score is 100`() = runTest(testDispatcher) {
        val local = scanResult(100f, RiskLevel.CRITICAL) // 100 * 0.7 = 70
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): ScanResult = local
            },
            mlScoreToReturn = 1.0f // 100 * 0.3 = 30
        )
        val result = analyzer.analyze(UrlPayload("https://example.com"))
        
        // Expected score = 70 + 30 = 100
        assertEquals(100f, result.riskScore, 0.01f)
        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
        assertEquals(ProtectionDecision.BLOCK, result.decision)
        assertEquals(1, fakeDao.persistedRecords.size)
    }

    @Test
    fun `multiple scans persist distinct records without overwriting`() = runTest(testDispatcher) {
        val analyzer = analyzer(
            linkScanner = LinkProtectionAgent(LinkHeuristicRiskEngine()),
            mlScoreToReturn = null
        )

        val result1 = analyzer.analyze(UrlPayload("https://safe-example.com"))
        val result2 = analyzer.analyze(UrlPayload("https://suspicious-example.xyz"))

        assertEquals(2, fakeDao.persistedRecords.size)
        assertEquals(2, ThreatJournal.scanResults.value.size)
        assertTrue(fakeDao.persistedRecords.containsKey("${result1.id}|${ThreatRecordEntity.TYPE_SCAN_RESULT}"))
        assertTrue(fakeDao.persistedRecords.containsKey("${result2.id}|${ThreatRecordEntity.TYPE_SCAN_RESULT}"))
    }

    private fun analyzer(
        linkScanner: LinkScanner,
        mlScoreToReturn: Float?,
        fileScanner: FileScanner = object : FileScanner {
            override suspend fun scan(uri: Uri): ScanResult = error("File scanner should not run")
        }
    ): IntentThreatAnalyzerImpl {
        val fakeMlEngine = object : MLInferenceEngine {
            override fun predict(features: FloatArray): Float {
                if (mlScoreToReturn == null) throw RuntimeException("Simulated ML failure")
                return mlScoreToReturn
            }
        }
        
        return IntentThreatAnalyzerImpl(
            linkScanner = linkScanner,
            fileScanner = fileScanner,
            threatEventBus = ThreatEventBus(),
            mlInferenceEngine = fakeMlEngine,
            threatJournal = ThreatJournal
        )
    }

    private fun scanResult(score: Float, level: RiskLevel, target: String = "https://example.com") = ScanResult(
        id = "test-id-${System.nanoTime()}",
        source = "Intent (Link)",
        target = target,
        riskLevel = level,
        riskScore = score,
        explanation = if (score == 0f) "No heuristic risk signals found." else "Local signal.",
        timestamp = 1L
    )

    private class FakeThreatDao : ThreatDao {
        val persistedRecords = mutableMapOf<String, ThreatRecordEntity>()
        var shouldFailUpsert = false

        override suspend fun getRecentThreatRecords(limit: Int): List<com.sentinel.ai.core.data.local.ThreatRecordEntity> {
            return persistedRecords.values.sortedByDescending { it.timestamp }
        }

        override suspend fun getThreatRecordsBefore(recordType: String, cursorTimestamp: Long, cursorId: String, limit: Int): List<com.sentinel.ai.core.data.local.ThreatRecordEntity> {
            return persistedRecords.values
                .filter {
                    it.recordType == recordType &&
                        (it.timestamp < cursorTimestamp || (it.timestamp == cursorTimestamp && it.id < cursorId))
                }
                .sortedWith(compareByDescending<com.sentinel.ai.core.data.local.ThreatRecordEntity> { it.timestamp }.thenByDescending { it.id })
                .take(limit)
        }

        override suspend fun upsertThreatRecord(record: ThreatRecordEntity) {
            if (shouldFailUpsert) {
                throw IOException("Simulated database write error")
            }
            persistedRecords["${record.id}|${record.recordType}"] = record
        }
    }
}
