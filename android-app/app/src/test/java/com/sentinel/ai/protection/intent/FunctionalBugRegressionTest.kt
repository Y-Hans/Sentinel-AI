package com.sentinel.ai.protection.intent

import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRiskEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FunctionalBugRegressionTest {

    private val engine = LinkHeuristicRiskEngine()

    @Test
    fun `HTTP warns while HTTPS remains allowed`() {
        assertEquals(com.sentinel.ai.core.model.RiskLevel.YELLOW, engine.analyze("http://google.com").riskLevel)
        assertEquals(com.sentinel.ai.core.model.RiskLevel.GREEN, engine.analyze("https://google.com").riskLevel)
    }

    @Test
    fun `embedded redirect URL reaches suspicious decision`() {
        val result = engine.analyze("https://example.com/?redirect=https://evil.com")

        assertEquals(45f, result.score, 0f)
        assertEquals(com.sentinel.ai.core.model.RiskLevel.YELLOW, result.riskLevel)
    }

    @Test
    fun `continue button follows protection decision`() {
        assertEquals("Continue", continueButtonText(ProtectionDecision.ALLOW))
        assertEquals("Continue Anyway", continueButtonText(ProtectionDecision.WARN))
        assertNull(continueButtonText(ProtectionDecision.BLOCK))
    }
}
