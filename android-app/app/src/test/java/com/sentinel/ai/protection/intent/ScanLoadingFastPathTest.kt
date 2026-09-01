package com.sentinel.ai.protection.intent

import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.protection.intent.model.UrlPayload
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanLoadingFastPathTest {
    @Test
    fun `only green allow URL is automatic`() {
        assertTrue(shouldAutoLaunch(result(RiskLevel.GREEN, ProtectionDecision.ALLOW), UrlPayload("https://example.com")))
        listOf(
            RiskLevel.YELLOW to ProtectionDecision.WARN,
            RiskLevel.RED to ProtectionDecision.WARN,
            RiskLevel.CRITICAL to ProtectionDecision.BLOCK,
            RiskLevel.GREEN to ProtectionDecision.WARN,
            RiskLevel.CRITICAL to ProtectionDecision.ALLOW
        ).forEach { (level, decision) ->
            assertFalse(shouldAutoLaunch(result(level, decision), UrlPayload("https://example.com")))
        }
    }

    private fun result(level: RiskLevel, decision: ProtectionDecision) = ScanResult(
        id = "test", source = "test", riskLevel = level, riskScore = 0f,
        explanation = "test", timestamp = 0L, decision = decision
    )
}
