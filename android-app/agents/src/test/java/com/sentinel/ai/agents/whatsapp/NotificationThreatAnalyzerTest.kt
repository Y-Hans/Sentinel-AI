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
    fun `benign OTP messages emit OTP_PRESENT and no OTP_SOLICITATION`() {
        val benignMessages = listOf(
            "Your Swiggy OTP is 123456. Do not share this OTP.",
            "Never share your OTP",
            "Your OTP is 123456",
            "Use OTP 123456 to complete login",
            "123456 is your verification code for Blinkit. Do not share it with anyone."
        )

        for (msg in benignMessages) {
            val evidence = analyzer.extractEvidence(msg, emptyList(), isKnownContact = true)
            assertTrue("Expected OTP_PRESENT for: $msg", evidence.any { it.type == EvidenceType.OTP_PRESENT })
            assertFalse("Expected NO OTP_SOLICITATION for: $msg", evidence.any { it.type == EvidenceType.OTP_SOLICITATION })
        }
    }

    @Test
    fun `malicious OTP solicitation messages emit OTP_SOLICITATION with HIGH severity`() {
        val solicitationMessages = listOf(
            "Send me the OTP immediately.",
            "Tell me the OTP",
            "Forward the verification code",
            "Share your OTP",
            "Your account will be blocked. Send the OTP immediately."
        )

        for (msg in solicitationMessages) {
            val evidence = analyzer.extractEvidence(msg, emptyList(), isKnownContact = true)
            assertTrue(
                "Expected OTP_SOLICITATION for: $msg",
                evidence.any { it.type == EvidenceType.OTP_SOLICITATION && it.severity == EvidenceSeverity.HIGH }
            )
        }
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

    @Test
    fun `Message ML runtime exception emits explicit CRITICAL ML-unavailable evidence and preserves heuristics`() {
        val throwingScanner = object : com.sentinel.ai.core.ml.messages.MessageScanner(
            com.sentinel.ai.core.ml.messages.DualTfidfVectorizer(emptyMap(), DoubleArray(0), emptySet(), emptyMap(), DoubleArray(0)),
            com.sentinel.ai.core.ml.messages.FeatureScaler(2070, DoubleArray(2070), DoubleArray(2070) { 1.0 }),
            com.sentinel.ai.core.ml.messages.MultiClassTreeEvaluator(3, 0, doubleArrayOf(10.0, -10.0, -10.0), emptyArray(), emptyArray()),
            com.sentinel.ai.core.ml.messages.MessageAdjudicator(0.704f)
        ) {
            override fun scan(messageText: String, senderHeader: String?): com.sentinel.ai.core.ml.messages.MessageScanResult {
                throw RuntimeException("Simulated ML model runtime failure")
            }
        }

        val testAnalyzer = NotificationThreatAnalyzer(throwingScanner, Unit)
        val message = "Urgent: verify your account OTP immediately for bank login"
        val evidence = testAnalyzer.extractEvidence(message, emptyList(), isKnownContact = true)

        // 1. Must emit CRITICAL ML-unavailable evidence
        val mlFailureItem = evidence.find { it.category == EvidenceCategory.MESSAGE_ML }
        assertTrue("Must contain MESSAGE_ML evidence", mlFailureItem != null)
        assertEquals(EvidenceType.MESSAGE_ML_SCORE, mlFailureItem!!.type)
        assertEquals(EvidenceSeverity.CRITICAL, mlFailureItem.severity)
        assertEquals("Messages ML unavailable", mlFailureItem.indicatorText)
        assertEquals("unavailable", mlFailureItem.metadata["status"])

        // 2. Must preserve heuristic observations
        assertTrue(evidence.any { it.type == EvidenceType.URGENCY_PRESSURE })
        assertTrue(evidence.any { it.type == EvidenceType.OTP_SOLICITATION })
        assertTrue(evidence.any { it.type == EvidenceType.CREDENTIAL_REQUEST })
        assertTrue(evidence.any { it.type == EvidenceType.ACCOUNT_THREAT })
        assertTrue(evidence.any { it.type == EvidenceType.FINANCIAL_REQUEST })
    }

    @Test
    fun `Message ML failure when evaluated by RiskFusionEngine produces BLOCK and prevents ALLOW`() {
        val throwingScanner = object : com.sentinel.ai.core.ml.messages.MessageScanner(
            com.sentinel.ai.core.ml.messages.DualTfidfVectorizer(emptyMap(), DoubleArray(0), emptySet(), emptyMap(), DoubleArray(0)),
            com.sentinel.ai.core.ml.messages.FeatureScaler(2070, DoubleArray(2070), DoubleArray(2070) { 1.0 }),
            com.sentinel.ai.core.ml.messages.MultiClassTreeEvaluator(3, 0, doubleArrayOf(10.0, -10.0, -10.0), emptyArray(), emptyArray()),
            com.sentinel.ai.core.ml.messages.MessageAdjudicator(0.704f)
        ) {
            override fun scan(messageText: String, senderHeader: String?): com.sentinel.ai.core.ml.messages.MessageScanResult {
                throw java.io.FileNotFoundException("champion_v2_trees.json not found")
            }
        }

        val testAnalyzer = NotificationThreatAnalyzer(throwingScanner, Unit)
        // Message with NO heuristic triggers from a known contact
        val evidence = testAnalyzer.extractEvidence("Hey, are you free for lunch?", emptyList(), isKnownContact = true)

        val fusionEngine = com.sentinel.ai.core.fusion.DefaultRiskFusionEngine()
        val fusionResult = fusionEngine.fuse(evidence, com.sentinel.ai.core.fusion.FusionContext(source = "WhatsApp", isKnownContact = true))

        // Security invariant: ML failure cannot produce ALLOW
        assertEquals(com.sentinel.ai.core.model.ProtectionDecision.BLOCK, fusionResult.decision)
        assertEquals(com.sentinel.ai.core.model.RiskLevel.CRITICAL, fusionResult.riskLevel)
        assertTrue(fusionResult.riskScore >= 90f)
    }

    @Test
    fun `successful Message ML inference emits expected evidence without failure markers`() {
        val successfulScanner = object : com.sentinel.ai.core.ml.messages.MessageScanner(
            com.sentinel.ai.core.ml.messages.DualTfidfVectorizer(emptyMap(), DoubleArray(0), emptySet(), emptyMap(), DoubleArray(0)),
            com.sentinel.ai.core.ml.messages.FeatureScaler(2070, DoubleArray(2070), DoubleArray(2070) { 1.0 }),
            com.sentinel.ai.core.ml.messages.MultiClassTreeEvaluator(3, 0, doubleArrayOf(10.0, -10.0, -10.0), emptyArray(), emptyArray()),
            com.sentinel.ai.core.ml.messages.MessageAdjudicator(0.704f)
        ) {
            override fun scan(messageText: String, senderHeader: String?): com.sentinel.ai.core.ml.messages.MessageScanResult {
                return com.sentinel.ai.core.ml.messages.MessageScanResult(
                    label = "MALICIOUS",
                    classIndex = 2,
                    probabilities = floatArrayOf(0.05f, 0.15f, 0.80f),
                    isNonBenign = true,
                    pNonBenign = 0.95f
                )
            }
        }

        val testAnalyzer = NotificationThreatAnalyzer(successfulScanner, Unit)
        val evidence = testAnalyzer.extractEvidence("Suspicious text", emptyList(), isKnownContact = true)

        val mlItem = evidence.find { it.category == EvidenceCategory.MESSAGE_ML }
        assertTrue("Must contain MESSAGE_ML evidence", mlItem != null)
        assertEquals(EvidenceSeverity.CRITICAL, mlItem!!.severity) // pNonBenign = 0.95f >= 0.90f -> CRITICAL
        assertEquals("MALICIOUS", mlItem.metadata["label"])
        assertFalse(evidence.any { it.indicatorText == "Messages ML unavailable" })
    }
}
