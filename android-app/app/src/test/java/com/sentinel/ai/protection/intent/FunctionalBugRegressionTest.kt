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
        assertEquals(ProtectionDecision.WARN, engine.toScanResult("http://google.com").decision)
        assertEquals(ProtectionDecision.ALLOW, engine.toScanResult("https://google.com").decision)
    }

    @Test
    fun `embedded redirect URL reaches suspicious decision`() {
        val result = engine.toScanResult("https://example.com/?redirect=https://evil.com")

        assertEquals(45f, result.riskScore, 0f)
        assertEquals(ProtectionDecision.WARN, result.decision)
    }

    @Test
    fun `continue button follows protection decision`() {
        assertEquals("Continue", continueButtonText(ProtectionDecision.ALLOW))
        assertEquals("Continue Anyway", continueButtonText(ProtectionDecision.WARN))
        assertNull(continueButtonText(ProtectionDecision.BLOCK))
    }
}
