package com.sentinel.ai.agents.whatsapp

import com.sentinel.ai.core.event.schema.UrlAnalysisItem
import com.sentinel.ai.core.event.schema.UrlScheme
import com.sentinel.ai.core.evidence.EvidenceCategory
import com.sentinel.ai.core.evidence.EvidenceSeverity
import com.sentinel.ai.core.evidence.EvidenceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationThreatAnalyzerTest {

    private val analyzer = NotificationThreatAnalyzer()

    @Test
    fun `extracts empty evidence for empty text and known contact`() {
        val evidence = analyzer.extractEvidence("", emptyList(), isKnownContact = true)
        assertTrue(evidence.isEmpty())
    }

    @Test
    fun `extracts unknown contact evidence when isKnownContact is false`() {
        val evidence = analyzer.extractEvidence("hello", emptyList(), isKnownContact = false)
        assertEquals(1, evidence.size)
        val item = evidence.first()
        assertEquals(EvidenceCategory.CONTACT_STATUS, item.category)
        assertEquals(EvidenceType.CONTACT_STATUS, item.type)
        assertEquals(EvidenceSeverity.LOW, item.severity)
        assertEquals("Sender is not a known contact", item.explanation)
    }

    @Test
    fun `extracts shortened and raw ip url evidence`() {
        val urls = listOf(
            UrlAnalysisItem(
                urlId = "1",
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
            ),
            UrlAnalysisItem(
                urlId = "2",
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
        )

        val evidence = analyzer.extractEvidence("Check this link", urls, isKnownContact = true)

        assertEquals(2, evidence.size)
        assertTrue(evidence.any { it.type == EvidenceType.SUSPICIOUS_LINK && it.severity == EvidenceSeverity.MEDIUM })
        assertTrue(evidence.any { it.type == EvidenceType.SUSPICIOUS_LINK && it.severity == EvidenceSeverity.HIGH })
    }

    @Test
    fun `extracts multiple intent signals from text`() {
        val message = "Urgent: verify your account OTP immediately for bank login"
        val evidence = analyzer.extractEvidence(message, emptyList(), isKnownContact = true)

        assertTrue(evidence.any { it.type == EvidenceType.URGENCY_PRESSURE })
        assertTrue(evidence.any { it.type == EvidenceType.OTP_SOLICITATION })
        assertTrue(evidence.any { it.type == EvidenceType.CREDENTIAL_REQUEST })
        assertTrue(evidence.any { it.type == EvidenceType.ACCOUNT_THREAT })
        assertTrue(evidence.any { it.type == EvidenceType.FINANCIAL_REQUEST })
    }

    @Test
    fun `asynchronously analyzes input via ThreatAnalyzer interface`() = runTest {
        val input = NotificationAnalysisInput(
            messageText = "Urgent payment required",
            urls = emptyList(),
            isKnownContact = true
        )
        val result = analyzer.analyze(input)

        assertTrue(result.hasEvidence)
        assertTrue(result.evidence.any { it.type == EvidenceType.URGENCY_PRESSURE })
        assertTrue(result.evidence.any { it.type == EvidenceType.FINANCIAL_REQUEST })
    }
}
