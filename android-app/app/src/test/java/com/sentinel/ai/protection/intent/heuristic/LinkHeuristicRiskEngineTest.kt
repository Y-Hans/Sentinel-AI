package com.sentinel.ai.protection.intent.heuristic

import com.sentinel.ai.core.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkHeuristicRiskEngineTest {

    private val engine = LinkHeuristicRiskEngine()

    @Test
    fun cleanUrlReturnsGreenRisk() {
        val analysis = engine.analyze("https://example.com/help")

        assertEquals(RiskLevel.GREEN, analysis.riskLevel)
        assertEquals(0, analysis.triggeredRuleCount)
    }

    @Test
    fun suspiciousBrandUrlTriggersMultipleSignals() {
        val analysis = engine.analyze("https://paypal-secure-login.xyz/verify/account?redirect=https%3A%2F%2Fevil.test")

        assertTrue(analysis.score >= 70f)
        assertTrue(analysis.ruleResults.any { it.triggered && it.category == RuleCategory.BRAND_IMPERSONATION })
        assertTrue(analysis.ruleResults.any { it.triggered && it.category == RuleCategory.SOCIAL_ENGINEERING })
    }
}
