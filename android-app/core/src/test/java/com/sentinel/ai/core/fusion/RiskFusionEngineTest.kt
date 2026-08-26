package com.sentinel.ai.core.fusion

import com.sentinel.ai.core.evidence.EvidenceCategory
import com.sentinel.ai.core.evidence.EvidenceSeverity
import com.sentinel.ai.core.evidence.EvidenceType
import com.sentinel.ai.core.evidence.ThreatEvidence
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.sender.ContactResolution
import com.sentinel.ai.core.sender.SenderProfile
import com.sentinel.ai.core.sender.SenderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskFusionEngineTest {

    private val engine: RiskFusionEngine = DefaultRiskFusionEngine()

    @Test
    fun `empty evidence produces deterministic safe default result`() {
        val result = engine.fuse(emptyList())

        assertEquals(0.0f, result.riskScore, 0.001f)
        assertEquals(RiskLevel.GREEN, result.riskLevel)
        assertEquals(ProtectionDecision.ALLOW, result.decision)
        assertEquals(1.0f, result.confidence, 0.001f)
        assertTrue(result.reasons.isEmpty())
        assertTrue(result.primaryEvidence.isEmpty())
        assertTrue(result.suppressedEvidence.isEmpty())
    }

    @Test
    fun `informational evidence does not create unjustified risk`() {
        val infoEvidence = ThreatEvidence(
            category = EvidenceCategory.SENDER_IDENTITY,
            type = EvidenceType.SENDER_HEADER_PATTERN,
            severity = EvidenceSeverity.INFO,
            sourceName = "SenderClassifier",
            indicatorText = "Service Header",
            explanation = "Matched transactional header pattern"
        )

        val result = engine.fuse(listOf(infoEvidence))

        assertEquals(0.0f, result.riskScore, 0.001f)
        assertEquals(RiskLevel.GREEN, result.riskLevel)
        assertEquals(ProtectionDecision.ALLOW, result.decision)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun `single low severity signal produces bounded low risk score`() {
        val lowEvidence = ThreatEvidence(
            category = EvidenceCategory.MESSAGE_CONTENT,
            type = EvidenceType.GENERIC_SUSPICIOUS_PATTERN,
            severity = EvidenceSeverity.LOW,
            sourceName = "PatternDetector",
            indicatorText = "Unusual Punctuation",
            explanation = "Message contains repeated exclamation marks"
        )

        val result = engine.fuse(listOf(lowEvidence))

        assertEquals(20.0f, result.riskScore, 0.001f)
        assertEquals(RiskLevel.GREEN, result.riskLevel)
        assertEquals(ProtectionDecision.ALLOW, result.decision)
        assertEquals(1, result.reasons.size)
    }

    @Test
    fun `single medium severity signal maps to yellow warn range`() {
        val mediumEvidence = ThreatEvidence(
            category = EvidenceCategory.MESSAGE_CONTENT,
            type = EvidenceType.URGENCY_PRESSURE,
            severity = EvidenceSeverity.MEDIUM,
            sourceName = "UrgencyDetector",
            indicatorText = "Urgent Action Required",
            explanation = "Urgency language detected: immediately"
        )

        val result = engine.fuse(listOf(mediumEvidence))

        assertEquals(40.0f, result.riskScore, 0.001f)
        assertEquals(RiskLevel.YELLOW, result.riskLevel)
        assertEquals(ProtectionDecision.WARN, result.decision)
    }

    @Test
    fun `single high severity signal maps to red warn range`() {
        val highEvidence = ThreatEvidence(
            category = EvidenceCategory.MESSAGE_CONTENT,
            type = EvidenceType.OTP_SOLICITATION,
            severity = EvidenceSeverity.HIGH,
            sourceName = "OtpSolicitationRule",
            indicatorText = "OTP Request",
            explanation = "Message requests user to forward authentication code"
        )

        val result = engine.fuse(listOf(highEvidence))

        assertEquals(70.0f, result.riskScore, 0.001f)
        assertEquals(RiskLevel.RED, result.riskLevel)
        assertEquals(ProtectionDecision.WARN, result.decision)
    }

    @Test
    fun `high confidence critical severity evidence forces critical block verdict`() {
        val criticalEvidence = ThreatEvidence(
            category = EvidenceCategory.FILE_HEURISTIC,
            type = EvidenceType.SUSPICIOUS_FILE,
            severity = EvidenceSeverity.CRITICAL,
            sourceName = "DoubleExtensionRule",
            confidence = 0.95f,
            indicatorText = "Dangerous Double Extension",
            explanation = "Executable disguised with pdf.exe double extension"
        )

        val result = engine.fuse(listOf(criticalEvidence))

        assertTrue(result.riskScore >= 90.0f)
        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
        assertEquals(ProtectionDecision.BLOCK, result.decision)
    }

    @Test
    fun `confidence dampens low confidence findings`() {
        val highConfEvidence = ThreatEvidence(
            category = EvidenceCategory.URL_HEURISTIC,
            type = EvidenceType.SUSPICIOUS_LINK,
            severity = EvidenceSeverity.HIGH,
            sourceName = "LinkHeuristics",
            confidence = 1.0f,
            indicatorText = "Suspicious Link",
            explanation = "High confidence malicious domain"
        )

        val lowConfEvidence = highConfEvidence.copy(confidence = 0.3f)

        val highResult = engine.fuse(listOf(highConfEvidence))
        val lowResult = engine.fuse(listOf(lowConfEvidence))

        assertTrue(highResult.riskScore > lowResult.riskScore)
        assertEquals(ProtectionDecision.WARN, highResult.decision)
        assertEquals(ProtectionDecision.ALLOW, lowResult.decision)
    }

    @Test
    fun `duplicate evidence is deduplicated and placed in suppressed list without inflating score`() {
        val evidence1 = ThreatEvidence(
            category = EvidenceCategory.MESSAGE_CONTENT,
            type = EvidenceType.URGENCY_PRESSURE,
            severity = EvidenceSeverity.MEDIUM,
            sourceName = "RegexRule",
            confidence = 0.8f,
            indicatorText = "Urgent language",
            explanation = "Urgency detected"
        )
        val evidence2 = ThreatEvidence(
            category = EvidenceCategory.MESSAGE_CONTENT,
            type = EvidenceType.URGENCY_PRESSURE,
            severity = EvidenceSeverity.MEDIUM,
            sourceName = "KeywordRule",
            confidence = 0.9f,
            indicatorText = "Urgent language",
            explanation = "Urgency detected"
        )

        val singleResult = engine.fuse(listOf(evidence2))
        val duplicateResult = engine.fuse(listOf(evidence1, evidence2))

        assertEquals(singleResult.riskScore, duplicateResult.riskScore, 0.001f)
        assertEquals(1, duplicateResult.primaryEvidence.size)
        assertEquals(1, duplicateResult.suppressedEvidence.size)
        assertEquals(0.9f, duplicateResult.primaryEvidence.first().confidence, 0.001f)
    }

    @Test
    fun `cross vector compounding reinforces intent and payload threats`() {
        val intentEvidence = ThreatEvidence(
            category = EvidenceCategory.MESSAGE_CONTENT,
            type = EvidenceType.CREDENTIAL_REQUEST,
            severity = EvidenceSeverity.HIGH,
            sourceName = "CredentialRule",
            indicatorText = "Password Request",
            explanation = "Message asks for account credentials"
        )
        val payloadEvidence = ThreatEvidence(
            category = EvidenceCategory.URL_HEURISTIC,
            type = EvidenceType.SUSPICIOUS_LINK,
            severity = EvidenceSeverity.HIGH,
            sourceName = "LinkRule",
            indicatorText = "Phishing Link",
            explanation = "Phishing domain detected"
        )

        val intentOnlyResult = engine.fuse(listOf(intentEvidence))
        val compoundResult = engine.fuse(listOf(intentEvidence, payloadEvidence))

        assertTrue(compoundResult.riskScore > intentOnlyResult.riskScore)
        assertTrue(compoundResult.riskScore >= 90.0f)
        assertEquals(RiskLevel.CRITICAL, compoundResult.riskLevel)
        assertEquals(ProtectionDecision.BLOCK, compoundResult.decision)
    }

    @Test
    fun `service sender delivering OTP is recognized as safe`() {
        val otpEvidence = ThreatEvidence(
            category = EvidenceCategory.MESSAGE_CONTENT,
            type = EvidenceType.OTP_PRESENT,
            severity = EvidenceSeverity.INFO,
            sourceName = "OtpDetector",
            indicatorText = "Authentication OTP",
            explanation = "Transactional login code received"
        )
        val headerEvidence = ThreatEvidence(
            category = EvidenceCategory.SENDER_IDENTITY,
            type = EvidenceType.SENDER_HEADER_PATTERN,
            severity = EvidenceSeverity.INFO,
            sourceName = "SenderClassifier",
            indicatorText = "Service Header",
            explanation = "Indian DLT service sender AD-HDFCBK-S"
        )
        val context = FusionContext(
            source = "com.google.android.apps.messaging",
            senderProfile = SenderProfile(
                rawIdentifier = "AD-HDFCBK-S",
                normalizedIdentifier = "AD-HDFCBK-S",
                senderType = SenderType.SERVICE,
                isKnownContact = false,
                displayName = "HDFC Bank"
            )
        )

        val result = engine.fuse(listOf(otpEvidence, headerEvidence), context)

        assertEquals(0.0f, result.riskScore, 0.001f)
        assertEquals(RiskLevel.GREEN, result.riskLevel)
        assertEquals(ProtectionDecision.ALLOW, result.decision)
    }

    @Test
    fun `personal or unknown sender soliciting OTP is flagged as elevated threat`() {
        val otpSolicitation = ThreatEvidence(
            category = EvidenceCategory.MESSAGE_CONTENT,
            type = EvidenceType.OTP_SOLICITATION,
            severity = EvidenceSeverity.HIGH,
            sourceName = "OtpSolicitationDetector",
            indicatorText = "OTP Request",
            explanation = "Sender is asking user to share their verification code"
        )
        val urgency = ThreatEvidence(
            category = EvidenceCategory.MESSAGE_CONTENT,
            type = EvidenceType.URGENCY_PRESSURE,
            severity = EvidenceSeverity.MEDIUM,
            sourceName = "UrgencyDetector",
            indicatorText = "Immediate Action",
            explanation = "Urgent deadline claimed"
        )
        val context = FusionContext(
            source = "com.whatsapp",
            senderProfile = SenderProfile(
                rawIdentifier = "+919876543210",
                normalizedIdentifier = "+919876543210",
                senderType = SenderType.PERSONAL,
                isKnownContact = false
            )
        )

        val result = engine.fuse(listOf(otpSolicitation, urgency), context)

        assertTrue(result.riskScore >= 70.0f)
        assertEquals(ProtectionDecision.WARN, result.decision)
    }

    @Test
    fun `known contact status dampens conversational suspicion`() {
        val urgency = ThreatEvidence(
            category = EvidenceCategory.MESSAGE_CONTENT,
            type = EvidenceType.URGENCY_PRESSURE,
            severity = EvidenceSeverity.MEDIUM,
            sourceName = "UrgencyDetector",
            indicatorText = "Urgency",
            explanation = "Urgent language detected"
        )
        val financial = ThreatEvidence(
            category = EvidenceCategory.MESSAGE_CONTENT,
            type = EvidenceType.FINANCIAL_REQUEST,
            severity = EvidenceSeverity.LOW,
            sourceName = "FinancialDetector",
            indicatorText = "Payment Mention",
            explanation = "Payment mentioned"
        )

        val unknownContext = FusionContext(isKnownContact = false)
        val knownContext = FusionContext(
            isKnownContact = true,
            contactResolution = ContactResolution.matchFound("Alice", "+919876543210")
        )

        val unknownResult = engine.fuse(listOf(urgency, financial), unknownContext)
        val knownResult = engine.fuse(listOf(urgency, financial), knownContext)

        assertTrue(unknownResult.riskScore > knownResult.riskScore)
        assertEquals(ProtectionDecision.WARN, unknownResult.decision)
        assertEquals(ProtectionDecision.ALLOW, knownResult.decision)
    }

    @Test
    fun `known contact status does NOT suppress critical payload threat`() {
        val dangerousFile = ThreatEvidence(
            category = EvidenceCategory.FILE_HEURISTIC,
            type = EvidenceType.SUSPICIOUS_FILE,
            severity = EvidenceSeverity.CRITICAL,
            sourceName = "DangerousExtensionRule",
            confidence = 1.0f,
            indicatorText = "Executable File",
            explanation = "Dangerous executable file attachment: payload.apk"
        )
        val knownContext = FusionContext(
            isKnownContact = true,
            contactResolution = ContactResolution.matchFound("Bob", "+919876543210")
        )

        val result = engine.fuse(listOf(dangerousFile), knownContext)

        assertTrue(result.riskScore >= 90.0f)
        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
        assertEquals(ProtectionDecision.BLOCK, result.decision)
    }

    @Test
    fun `fusion evaluation is strictly deterministic`() {
        val evidenceList = listOf(
            ThreatEvidence(
                category = EvidenceCategory.MESSAGE_CONTENT,
                type = EvidenceType.URGENCY_PRESSURE,
                severity = EvidenceSeverity.MEDIUM,
                sourceName = "UrgencyRule",
                indicatorText = "Urgent",
                explanation = "Urgency detected"
            ),
            ThreatEvidence(
                category = EvidenceCategory.URL_HEURISTIC,
                type = EvidenceType.SUSPICIOUS_LINK,
                severity = EvidenceSeverity.HIGH,
                sourceName = "LinkRule",
                indicatorText = "Shortened Link",
                explanation = "Shortened URL detected"
            )
        )
        val context = FusionContext(source = "com.whatsapp", timestamp = 1719218400000L)

        val firstRun = engine.fuse(evidenceList, context)

        for (i in 1..100) {
            val nextRun = engine.fuse(evidenceList, context)
            assertEquals(firstRun.riskScore, nextRun.riskScore, 0.0f)
            assertEquals(firstRun.riskLevel, nextRun.riskLevel)
            assertEquals(firstRun.decision, nextRun.decision)
            assertEquals(firstRun.confidence, nextRun.confidence, 0.0f)
            assertEquals(firstRun.explanation, nextRun.explanation)
        }
    }

    @Test
    fun `risk score is strictly bounded in 0 to 100`() {
        // High stacking evidence
        val severeEvidence = (1..10).map { i ->
            ThreatEvidence(
                category = EvidenceCategory.MESSAGE_CONTENT,
                type = EvidenceType.ACCOUNT_THREAT,
                severity = EvidenceSeverity.CRITICAL,
                sourceName = "Rule$i",
                indicatorText = "Indicator $i",
                explanation = "Threat $i"
            )
        }

        val result = engine.fuse(severeEvidence)
        assertTrue(result.riskScore in 0.0f..100.0f)
        assertTrue(result.riskScore >= 90.0f)
        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
        assertEquals(ProtectionDecision.BLOCK, result.decision)
    }

    @Test
    fun `toScanResult maps fusion result cleanly into application boundary ScanResult`() {
        val evidence = ThreatEvidence(
            category = EvidenceCategory.URL_HEURISTIC,
            type = EvidenceType.SUSPICIOUS_LINK,
            severity = EvidenceSeverity.HIGH,
            sourceName = "PhishingDetector",
            indicatorText = "Phishing Domain",
            explanation = "Suspicious phishing domain detected"
        )
        val fusionResult = engine.fuse(listOf(evidence))

        val scanResult = fusionResult.toScanResult(
            id = "test-scan-123",
            source = "Intent (Link)",
            target = "https://phishing.example.com",
            timestamp = 1719218400000L,
            senderDisplayName = "Attacker",
            senderIdentifier = "+919876543210"
        )

        assertEquals("test-scan-123", scanResult.id)
        assertEquals("Intent (Link)", scanResult.source)
        assertEquals("https://phishing.example.com", scanResult.target)
        assertEquals(1719218400000L, scanResult.timestamp)
        assertEquals("Attacker", scanResult.senderDisplayName)
        assertEquals("+919876543210", scanResult.senderIdentifier)
        assertEquals(fusionResult.riskScore, scanResult.riskScore, 0.001f)
        assertEquals(fusionResult.riskLevel, scanResult.riskLevel)
        assertEquals(fusionResult.decision, scanResult.decision)
        assertEquals(fusionResult.headline, scanResult.headline)
        assertEquals(fusionResult.summary, scanResult.summary)
        assertEquals(1, scanResult.reasons.size)
    }
}
