package com.sentinel.ai.protection.intent

import android.net.Uri
import com.sentinel.ai.core.data.local.ThreatDao
import com.sentinel.ai.core.data.local.ThreatRecordEntity
import com.sentinel.ai.core.event.ThreatEventBus
import com.sentinel.ai.core.event.ThreatJournal
import com.sentinel.ai.core.fusion.DefaultRiskFusionEngine
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
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
        val local = mockEvidence(30f, RiskLevel.GREEN, target = "https://example.xyz")
        var scannedUrl: String? = null
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): List<com.sentinel.ai.core.evidence.ThreatEvidence> {
                    scannedUrl = url
                    return local
                }
            },
            mlScoreToReturn = 0f
        )

        val result = analyzer.analyze(UrlPayload("https://example.xyz"))

        assertEquals("https://example.xyz", scannedUrl)
        assertEquals(35f, result.riskScore, 0.01f) // 30 peak + 5 corroboration
        assertEquals(RiskLevel.GREEN, result.riskLevel) // Because 35f < 40f
        assertEquals(ProtectionDecision.ALLOW, result.decision)

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
        val fileEvidence = mockEvidence(95f, RiskLevel.CRITICAL)
        val fakeUri = io.mockk.mockk<Uri>(relaxed = true)
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): List<com.sentinel.ai.core.evidence.ThreatEvidence> = error("Link scanner should not run")
            },
            mlScoreToReturn = 0f,
            fileScanner = object : FileScanner {
                override suspend fun scan(uri: Uri): List<com.sentinel.ai.core.evidence.ThreatEvidence> {
                    assertEquals(fakeUri, uri)
                    return fileEvidence
                }
            }
        )

        val result = analyzer.analyze(FilePayload(fakeUri))

        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
        assertEquals(1, fakeDao.persistedRecords.size)
        val persisted = fakeDao.persistedRecords.values.first()
        assertEquals(fakeUri.toString(), persisted.content)
        assertEquals(RiskLevel.CRITICAL.name, persisted.riskLevel)
    }

    @Test
    fun `invalid URL returns a controlled explainable result and persists`() = runTest(testDispatcher) {
        val analyzer = analyzer(
            linkScanner = LinkProtectionAgent(LinkHeuristicRiskEngine()),
            mlScoreToReturn = 0f
        )

        val result = analyzer.analyze(UrlPayload("://broken"))

        assertEquals(ProtectionDecision.ALLOW, result.decision)
        assertTrue(result.riskScore in 0f..100f)
        assertEquals(1, fakeDao.persistedRecords.size)
    }

    @Test
    fun `cancellation during local scan is propagated`() = runTest(testDispatcher) {
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): List<com.sentinel.ai.core.evidence.ThreatEvidence> = awaitCancellation()
            },
            mlScoreToReturn = 0f
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
        val local = mockEvidence(50f, RiskLevel.YELLOW)
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): List<com.sentinel.ai.core.evidence.ThreatEvidence> = local
            },
            mlScoreToReturn = 0f
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
        val local = mockEvidence(50f, RiskLevel.YELLOW)
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): List<com.sentinel.ai.core.evidence.ThreatEvidence> = local
            },
            mlScoreToReturn = 0f
        )
        val result = analyzer.analyze(UrlPayload("https://example.com"))
        
        // Expected score = peak(50) + corroboration(5) = 55
        assertEquals(55f, result.riskScore, 0.01f)
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
        assertEquals(ProtectionDecision.WARN, result.decision)
        assertEquals(1, fakeDao.persistedRecords.size)
    }

    @Test
    fun `final score incorporates heuristics and ML when ML score is 50`() = runTest(testDispatcher) {
        val local = mockEvidence(50f, RiskLevel.YELLOW)
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): List<com.sentinel.ai.core.evidence.ThreatEvidence> = local
            },
            mlScoreToReturn = 50f
        )
        val result = analyzer.analyze(UrlPayload("https://example.com"))
        
        // Expected score = peak(50) + corroboration(5) = 55
        assertEquals(55f, result.riskScore, 0.01f)
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
        assertEquals(ProtectionDecision.WARN, result.decision)
        assertEquals(1, fakeDao.persistedRecords.size)
    }

    @Test
    fun `final score incorporates heuristics and ML when ML score is 100`() = runTest(testDispatcher) {
        val local = mockEvidence(100f, RiskLevel.CRITICAL)
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): List<com.sentinel.ai.core.evidence.ThreatEvidence> = local
            },
            mlScoreToReturn = 100f
        )
        val result = analyzer.analyze(UrlPayload("https://example.com"))
        
        // Expected score = 100 (due to Critical Override)
        assertEquals(100f, result.riskScore, 0.01f)
        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
        assertEquals(ProtectionDecision.BLOCK, result.decision)
        assertEquals(1, fakeDao.persistedRecords.size)
    }

    @Test
    fun `multiple scans persist distinct records without overwriting`() = runTest(testDispatcher) {
        val analyzer = analyzer(
            linkScanner = LinkProtectionAgent(LinkHeuristicRiskEngine()),
            mlScoreToReturn = 0f
        )

        val result1 = analyzer.analyze(UrlPayload("https://safe-example.com"))
        val result2 = analyzer.analyze(UrlPayload("https://suspicious-example.xyz"))

        assertEquals(2, fakeDao.persistedRecords.size)
        assertEquals(2, ThreatJournal.scanResults.value.size)
        assertTrue(fakeDao.persistedRecords.containsKey("${result1.id}|${ThreatRecordEntity.TYPE_SCAN_RESULT}"))
        assertTrue(fakeDao.persistedRecords.containsKey("${result2.id}|${ThreatRecordEntity.TYPE_SCAN_RESULT}"))
    }

    @Test
    fun `URL ML runtime exception produces explicit CRITICAL ML-unavailable evidence and forces BLOCK`() = runTest(testDispatcher) {
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): List<com.sentinel.ai.core.evidence.ThreatEvidence> = emptyList()
            },
            mlScoreToReturn = null // Simulates RuntimeException in UrlScanner.scan
        )

        val result = analyzer.analyze(UrlPayload("https://example.com"))

        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
        assertEquals(ProtectionDecision.BLOCK, result.decision)
        assertTrue("Risk score must be at least 90 for CRITICAL", result.riskScore >= 90f)
        assertTrue(result.explanation.contains("URL threat model could not be evaluated"))
        assertEquals(1, fakeDao.persistedRecords.size)
        val persisted = fakeDao.persistedRecords.values.first()
        assertEquals(RiskLevel.CRITICAL.name, persisted.riskLevel)
    }

    @Test
    fun `URL ML failure preserves heuristic evidence alongside critical failure evidence`() = runTest(testDispatcher) {
        val local = mockEvidence(50f, RiskLevel.YELLOW)
        val analyzer = analyzer(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): List<com.sentinel.ai.core.evidence.ThreatEvidence> = local
            },
            mlScoreToReturn = null // Simulates RuntimeException in UrlScanner.scan
        )

        val result = analyzer.analyze(UrlPayload("https://example.com"))

        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
        assertEquals(ProtectionDecision.BLOCK, result.decision)
        assertTrue(result.explanation.contains("Local signal"))
        assertTrue(result.explanation.contains("URL threat model could not be evaluated"))
    }

    @Test
    fun `URL ML missing asset or malformed model exception cannot produce unauthorized ALLOW`() = runTest(testDispatcher) {
        val fakeUrlScanner = io.mockk.mockk<com.sentinel.ai.core.ml.url.UrlScanner>()
        io.mockk.every { fakeUrlScanner.scan(any()) } throws java.io.FileNotFoundException("v7_champion_portable.json not found")

        val analyzer = IntentThreatAnalyzerImpl(
            linkScanner = object : LinkScanner {
                override suspend fun scan(url: String): List<com.sentinel.ai.core.evidence.ThreatEvidence> = emptyList()
            },
            fileScanner = object : FileScanner {
                override suspend fun scan(uri: Uri): List<com.sentinel.ai.core.evidence.ThreatEvidence> = error("File scanner should not run")
            },
            threatEventBus = ThreatEventBus(),
            urlScanner = fakeUrlScanner,
            threatJournal = ThreatJournal,
            riskFusionEngine = DefaultRiskFusionEngine()
        )

        val result = analyzer.analyze(UrlPayload("https://example.com"))

        // Security invariant: ML failure MUST NOT allow the request
        assertEquals(ProtectionDecision.BLOCK, result.decision)
        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
    }

    private fun analyzer(
        linkScanner: LinkScanner,
        mlScoreToReturn: Float?,
        fileScanner: FileScanner = object : FileScanner {
            override suspend fun scan(uri: Uri): List<com.sentinel.ai.core.evidence.ThreatEvidence> = error("File scanner should not run")
        }
    ): IntentThreatAnalyzerImpl {
        val fakeUrlScanner = io.mockk.mockk<com.sentinel.ai.core.ml.url.UrlScanner>()
        if (mlScoreToReturn == null) {
            io.mockk.every { fakeUrlScanner.scan(any()) } throws RuntimeException("Simulated ML failure")
        } else {
            val prob = (mlScoreToReturn / 100f).coerceIn(0f, 1f)
            val isMal = prob >= 0.22588723f
            val label = if (isMal) "MALICIOUS" else "BENIGN"
            io.mockk.every { fakeUrlScanner.scan(any()) } returns com.sentinel.ai.core.ml.url.UrlScanResult(
                label = label,
                probability = prob,
                isMalicious = isMal,
                rawProbability = prob,
                isSafeBrandGated = false
            )
        }

        return IntentThreatAnalyzerImpl(
            linkScanner = linkScanner,
            fileScanner = fileScanner,
            threatEventBus = ThreatEventBus(),
            urlScanner = fakeUrlScanner,
            threatJournal = ThreatJournal,
            riskFusionEngine = DefaultRiskFusionEngine()
        )
    }

    private fun mockEvidence(score: Float, level: com.sentinel.ai.core.model.RiskLevel, target: String = "https://example.com") = listOf(
        com.sentinel.ai.core.evidence.ThreatEvidence(
            category = com.sentinel.ai.core.evidence.EvidenceCategory.URL_HEURISTIC,
            type = com.sentinel.ai.core.evidence.EvidenceType.SUSPICIOUS_LINK,
            severity = when (level) {
                com.sentinel.ai.core.model.RiskLevel.CRITICAL -> com.sentinel.ai.core.evidence.EvidenceSeverity.CRITICAL
                com.sentinel.ai.core.model.RiskLevel.RED -> com.sentinel.ai.core.evidence.EvidenceSeverity.HIGH
                com.sentinel.ai.core.model.RiskLevel.YELLOW -> com.sentinel.ai.core.evidence.EvidenceSeverity.MEDIUM
                com.sentinel.ai.core.model.RiskLevel.GREEN -> com.sentinel.ai.core.evidence.EvidenceSeverity.LOW
            },
            sourceName = "LinkHeuristicRiskEngine",
            confidence = 1.0f,
            indicatorText = "Link Heuristics",
            explanation = if (score == 0f) "No heuristic risk signals found." else "Local signal.",
            metadata = mapOf("score" to score.toString())
        )
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
