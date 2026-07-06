package com.sentinel.ai.agents.whatsapp

import com.sentinel.ai.core.event.schema.ScamRiskLevel
import com.sentinel.ai.core.event.schema.UrlAnalysisItem
import com.sentinel.ai.core.event.schema.UrlScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamRuleEngineTest {

    @Test
    fun `flags shortened url urgency and unknown sender`() {
        val result = ScamRuleEngine.evaluate(
            messageText = "Urgent verify now to keep your account active",
            urls = listOf(
                UrlAnalysisItem(
                    urlId = "a1b2c3d4-e5f6-4890-ab12-cd34ef567890",
                    rawUrl = "http://bit.ly/login",
                    normalizedUrl = "http://bit.ly/login",
                    domain = "bit.ly",
                    tld = "ly",
                    urlScheme = UrlScheme.HTTP,
                    isShortened = true,
                    isIpAddressUrl = false,
                    brandImpersonationDetected = false,
                    phishingFeedMatch = false,
                    urlRiskScore = 0.0,
                    analyzedAt = "2026-06-23T10:15:33.201Z"
                )
            ),
            isKnownContact = false
        )

        assertEquals(ScamRiskLevel.HIGH, result.riskLevel)
        assertTrue(result.riskScore >= 51)
        assertTrue(result.explanations.any { it.contains("Shortened URL detected") })
        assertTrue(result.explanations.any { it.contains("Urgency language detected") })
        assertTrue(result.explanations.any { it.contains("Sender is not a known contact") })
    }

    @Test
    fun `detects raw ip url and credential harvesting`() {
        val result = ScamRuleEngine.evaluate(
            messageText = "Login with your OTP and security code",
            urls = listOf(
                UrlAnalysisItem(
                    urlId = "b1b2c3d4-e5f6-4890-ab12-cd34ef567890",
                    rawUrl = "http://192.168.1.1/login",
                    normalizedUrl = "http://192.168.1.1/login",
                    domain = "192.168.1.1",
                    tld = "1",
                    urlScheme = UrlScheme.HTTP,
                    isShortened = false,
                    isIpAddressUrl = true,
                    brandImpersonationDetected = false,
                    phishingFeedMatch = false,
                    urlRiskScore = 0.0,
                    analyzedAt = "2026-06-23T10:15:33.201Z"
                )
            ),
            isKnownContact = true
        )

        assertEquals(ScamRiskLevel.HIGH, result.riskLevel)
        assertTrue(result.explanations.any { it.contains("Raw IP address URL detected") })
        assertTrue(result.explanations.any { it.contains("Credential harvesting indicator detected") })
    }
}
