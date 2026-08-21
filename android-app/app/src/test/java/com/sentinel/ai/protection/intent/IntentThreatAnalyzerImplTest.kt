package com.sentinel.ai.protection.intent

import android.net.Uri
import com.sentinel.ai.core.event.ThreatEventBus
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.ml.MLInferenceEngine
import com.sentinel.ai.protection.intent.file.FileScanner
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRiskEngine
import com.sentinel.ai.protection.intent.link.LinkProtectionAgent
import com.sentinel.ai.protection.intent.link.LinkScanner
import com.sentinel.ai.protection.intent.model.UrlPayload
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.mockk.mockk

@OptIn(ExperimentalCoroutinesApi::class)
class IntentThreatAnalyzerImplTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `supported URL local result is returned`() = runTest(testDispatcher) {
        val local = scanResult(35f, RiskLevel.YELLOW)
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
    }

    @Test
    fun `invalid URL returns a controlled explainable result`() = runTest(testDispatcher) {
        val analyzer = analyzer(
            linkScanner = LinkProtectionAgent(LinkHeuristicRiskEngine()),
            mlScoreToReturn = null
        )

        val result = analyzer.analyze(UrlPayload("://broken"))

        assertEquals(ProtectionDecision.ALLOW, result.decision)
        assertTrue(result.riskScore in 0f..100f)
        assertNotNull(result.localEvidence)
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
            mlInferenceEngine = fakeMlEngine
        )
    }

    private fun scanResult(score: Float, level: RiskLevel) = ScanResult(
        id = "test-id",
        source = "Intent (Link)",
        riskLevel = level,
        riskScore = score,
        explanation = if (score == 0f) "No heuristic risk signals found." else "Local signal.",
        timestamp = 1L
    )
}
