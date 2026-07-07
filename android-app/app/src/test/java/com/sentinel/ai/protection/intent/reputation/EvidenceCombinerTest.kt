package com.sentinel.ai.protection.intent.reputation

import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceCombinerTest {

    private val combiner = EvidenceCombiner()

    private fun createBaseScanResult(score: Float, level: RiskLevel): ScanResult {
        return ScanResult(
            id = "test-id",
            source = "heuristic",
            riskLevel = level,
            riskScore = score,
            explanation = "Heuristic clean.",
            timestamp = System.currentTimeMillis()
        )
    }

    @Test
    fun emptyEvidenceReturnsHeuristicResult() {
        val base = createBaseScanResult(10f, RiskLevel.GREEN)
        val result = combiner.combine(base, emptyList())

        assertEquals(base, result)
    }

    @Test
    fun maliciousEvidenceTriggersRedRiskOnCleanHeuristic() {
        val base = createBaseScanResult(0f, RiskLevel.GREEN)
        val evidence = listOf(
            ReputationResult(
                providerName = "TestProvider",
                confidence = 0.95f,
                reputation = ReputationVerdict.MALICIOUS,
                reason = "Mock malicious match.",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = combiner.combine(base, evidence)

        // P_heuristic = 0.0
        // P_evidence = 0.90 * 0.95 = 0.855
        // P_fused = 1 - 1 * 0.145 = 0.855
        // Score = 85.5f -> RiskLevel.RED
        assertEquals(85.5f, result.riskScore, 0.001f)
        assertEquals(RiskLevel.RED, result.riskLevel)
        assertTrue(result.explanation.contains("TestProvider=malicious"))
        assertTrue(result.explanation.contains("Mock malicious match"))
    }

    @Test
    fun suspiciousEvidenceTriggersYellowRiskOnCleanHeuristic() {
        val base = createBaseScanResult(0f, RiskLevel.GREEN)
        val evidence = listOf(
            ReputationResult(
                providerName = "TestProvider",
                confidence = 0.80f,
                reputation = ReputationVerdict.SUSPICIOUS,
                reason = "Mock suspicious match.",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = combiner.combine(base, evidence)

        // P_heuristic = 0.0
        // P_evidence = 0.60 * 0.80 = 0.48
        // P_fused = 1 - 0.52 = 0.48
        // Score = 48f -> RiskLevel.YELLOW
        assertEquals(48.0f, result.riskScore, 0.001f)
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }

    @Test
    fun multipleSuspiciousEvidencePromoteToRedRisk() {
        val base = createBaseScanResult(0f, RiskLevel.GREEN)
        val evidence = listOf(
            ReputationResult(
                providerName = "ProviderA",
                confidence = 0.80f,
                reputation = ReputationVerdict.SUSPICIOUS,
                reason = "First suspect.",
                timestamp = System.currentTimeMillis()
            ),
            ReputationResult(
                providerName = "ProviderB",
                confidence = 0.80f,
                reputation = ReputationVerdict.SUSPICIOUS,
                reason = "Second suspect.",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = combiner.combine(base, evidence)

        // P_heuristic = 0.0
        // P_A = 0.60 * 0.80 = 0.48
        // P_B = 0.60 * 0.80 = 0.48
        // P_fused = 1 - (1 - 0.48) * (1 - 0.48) = 1 - 0.2704 = 0.7296
        // Score = 72.96f -> RiskLevel.RED
        assertEquals(72.96f, result.riskScore, 0.001f)
        assertEquals(RiskLevel.RED, result.riskLevel)
        assertTrue(result.explanation.contains("ProviderA=suspicious"))
        assertTrue(result.explanation.contains("ProviderB=suspicious"))
    }

    @Test
    fun cleanEvidenceDiscountsSuspiciousHeuristic() {
        val base = createBaseScanResult(40f, RiskLevel.YELLOW)
        val evidence = listOf(
            ReputationResult(
                providerName = "TestProvider",
                confidence = 0.90f,
                reputation = ReputationVerdict.CLEAN,
                reason = "Mock clean.",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = combiner.combine(base, evidence)

        // Threat Score before discount = 40f
        // maxCleanConfidence = 0.90
        // Since threat score is >= 30 and < 70, maxDiscountLimit = 0.50
        // cleanDiscount = 0.90 * 0.50 = 0.45
        // combinedScore = 40f * (1 - 0.45) = 40f * 0.55 = 22.0f
        // 22.0f -> RiskLevel.GREEN
        assertEquals(22.0f, result.riskScore, 0.001f)
        assertEquals(RiskLevel.GREEN, result.riskLevel)
    }

    @Test
    fun cleanEvidenceDoesNotFullySuppressMaliciousHeuristic() {
        val base = createBaseScanResult(80f, RiskLevel.RED)
        val evidence = listOf(
            ReputationResult(
                providerName = "TestProvider",
                confidence = 0.90f,
                reputation = ReputationVerdict.CLEAN,
                reason = "Mock clean.",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = combiner.combine(base, evidence)

        // Threat Score before discount = 80f
        // maxCleanConfidence = 0.90
        // Since threat score is >= 70, maxDiscountLimit = 0.20
        // cleanDiscount = 0.90 * 0.20 = 0.18
        // combinedScore = 80f * (1 - 0.18) = 80f * 0.82 = 65.6f
        // 65.6f -> RiskLevel.YELLOW (still Suspicious, not GREEN!)
        assertEquals(65.6f, result.riskScore, 0.001f)
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
    }
}
