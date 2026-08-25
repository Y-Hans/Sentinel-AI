package com.sentinel.ai.core.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreatEvidenceTest {

    @Test
    fun `creates valid ThreatEvidence with default confidence and empty metadata`() {
        val evidence = ThreatEvidence(
            category = EvidenceCategory.MESSAGE_CONTENT,
            type = EvidenceType.OTP_PRESENT,
            severity = EvidenceSeverity.INFO,
            sourceName = "MessageRuleEngine",
            indicatorText = "Transactional OTP",
            explanation = "Message contains an authentication code"
        )

        assertEquals(EvidenceCategory.MESSAGE_CONTENT, evidence.category)
        assertEquals(EvidenceType.OTP_PRESENT, evidence.type)
        assertEquals(EvidenceSeverity.INFO, evidence.severity)
        assertEquals("MessageRuleEngine", evidence.sourceName)
        assertEquals(1.0f, evidence.confidence, 0.001f)
        assertEquals("Transactional OTP", evidence.indicatorText)
        assertEquals("Message contains an authentication code", evidence.explanation)
        assertTrue(evidence.metadata.isEmpty())
    }

    @Test
    fun `allows boundary confidence values of 0 and 1`() {
        val minConfidenceEvidence = ThreatEvidence(
            category = EvidenceCategory.URL_HEURISTIC,
            type = EvidenceType.SUSPICIOUS_LINK,
            severity = EvidenceSeverity.LOW,
            sourceName = "LinkHeuristicEngine",
            confidence = 0.0f,
            indicatorText = "Suspicious Link",
            explanation = "Low confidence heuristic finding"
        )
        assertEquals(0.0f, minConfidenceEvidence.confidence, 0.001f)

        val maxConfidenceEvidence = ThreatEvidence(
            category = EvidenceCategory.URL_HEURISTIC,
            type = EvidenceType.SUSPICIOUS_LINK,
            severity = EvidenceSeverity.CRITICAL,
            sourceName = "LinkHeuristicEngine",
            confidence = 1.0f,
            indicatorText = "Malicious Link",
            explanation = "Confirmed phishing domain"
        )
        assertEquals(1.0f, maxConfidenceEvidence.confidence, 0.001f)
    }

    @Test
    fun `preserves custom metadata key-value pairs`() {
        val metadata = mapOf(
            "token" to "482913",
            "matchedKeyword" to "otp",
            "headerSuffix" to "-S"
        )
        val evidence = ThreatEvidence(
            category = EvidenceCategory.SENDER_IDENTITY,
            type = EvidenceType.SENDER_HEADER_PATTERN,
            severity = EvidenceSeverity.INFO,
            sourceName = "SenderClassifier",
            confidence = 0.95f,
            indicatorText = "Service Sender Header",
            explanation = "Sender matched Indian DLT service convention",
            metadata = metadata
        )

        assertEquals(3, evidence.metadata.size)
        assertEquals("482913", evidence.metadata["token"])
        assertEquals("otp", evidence.metadata["matchedKeyword"])
        assertEquals("-S", evidence.metadata["headerSuffix"])
    }

    @Test
    fun `rejects negative confidence with IllegalArgumentException`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ThreatEvidence(
                category = EvidenceCategory.MESSAGE_CONTENT,
                type = EvidenceType.URGENCY_PRESSURE,
                severity = EvidenceSeverity.MEDIUM,
                sourceName = "MessageRuleEngine",
                confidence = -0.01f,
                indicatorText = "Urgency",
                explanation = "Invalid confidence"
            )
        }
        assertTrue(exception.message?.contains("Confidence must be between 0.0 and 1.0") == true)
    }

    @Test
    fun `rejects confidence greater than 1 with IllegalArgumentException`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ThreatEvidence(
                category = EvidenceCategory.MESSAGE_CONTENT,
                type = EvidenceType.URGENCY_PRESSURE,
                severity = EvidenceSeverity.MEDIUM,
                sourceName = "MessageRuleEngine",
                confidence = 1.01f,
                indicatorText = "Urgency",
                explanation = "Invalid confidence"
            )
        }
        assertTrue(exception.message?.contains("Confidence must be between 0.0 and 1.0") == true)
    }
}
