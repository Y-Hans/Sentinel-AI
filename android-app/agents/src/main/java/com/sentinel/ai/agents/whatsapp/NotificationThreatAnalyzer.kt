package com.sentinel.ai.agents.whatsapp

import com.sentinel.ai.core.analyzer.AnalysisResult
import com.sentinel.ai.core.analyzer.ThreatAnalyzer
import com.sentinel.ai.core.event.schema.UrlAnalysisItem
import com.sentinel.ai.core.evidence.EvidenceCategory
import com.sentinel.ai.core.evidence.EvidenceSeverity
import com.sentinel.ai.core.evidence.EvidenceType
import com.sentinel.ai.core.evidence.ThreatEvidence
import com.sentinel.ai.core.ml.messages.MessageScanner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain payload input for notification threat evidence extraction.
 */
data class NotificationAnalysisInput(
    val messageText: String,
    val urls: List<UrlAnalysisItem> = emptyList(),
    val isKnownContact: Boolean = false,
    val sender: String? = null
)

/**
 * Signal analyzer responsible for extracting structured [ThreatEvidence] observations
 * from notification text, attached URLs, ML classification, and contact parameters.
 *
 * NOTE: This analyzer observes and emits evidence; it does NOT compute final global risk scores,
 * assign RiskLevels, or determine ProtectionDecisions.
 */
@Singleton
class NotificationThreatAnalyzer @Inject constructor(
    private val messageScanner: MessageScanner
) : ThreatAnalyzer<NotificationAnalysisInput> {

    private var isTestingWithoutScanner: Boolean = false

    constructor(isTest: Boolean) : this(MessageScanner.createNoOp()) {
        this.isTestingWithoutScanner = isTest
    }

    constructor() : this(isTest = true)

    constructor(testScanner: MessageScanner, @Suppress("UNUSED_PARAMETER") forTesting: Unit = Unit) : this(testScanner) {
        this.isTestingWithoutScanner = false
    }

    override val analyzerName: String = "NotificationThreatAnalyzer"

    override suspend fun analyze(input: NotificationAnalysisInput): AnalysisResult {
        return AnalysisResult(
            evidence = extractEvidence(
                messageText = input.messageText,
                urls = input.urls,
                isKnownContact = input.isKnownContact,
                senderHeader = input.sender
            )
        )
    }

    fun extractEvidence(
        messageText: String,
        urls: List<UrlAnalysisItem> = emptyList(),
        isKnownContact: Boolean = false,
        senderHeader: String? = null
    ): List<ThreatEvidence> {
        val evidenceList = mutableListOf<ThreatEvidence>()

        // 1. Messages-ML Champion V2 evaluation
        if (!isTestingWithoutScanner && messageText.isNotBlank()) {
            var mlFailure: Throwable? = null
            val mlResult = try {
                messageScanner.scan(messageText, senderHeader)
            } catch (e: Throwable) {
                mlFailure = e
                null
            }

            if (mlResult != null && mlResult.probabilities.size >= 3) {
                val pNonBenign = mlResult.pNonBenign
                val mlScore = (pNonBenign * 100f).coerceIn(0f, 100f)
                val severity = when {
                    pNonBenign >= 0.90f -> EvidenceSeverity.CRITICAL
                    pNonBenign >= 0.704f -> EvidenceSeverity.HIGH
                    pNonBenign >= 0.40f -> EvidenceSeverity.MEDIUM
                    else -> EvidenceSeverity.LOW
                }

                evidenceList.add(
                    ThreatEvidence(
                        category = EvidenceCategory.MESSAGE_ML,
                        type = EvidenceType.MESSAGE_ML_SCORE,
                        severity = severity,
                        sourceName = "Messages-ML Champion V2",
                        confidence = 0.95f,
                        indicatorText = "${mlScore.toInt()}/100 (${mlResult.label})",
                        explanation = "Messages ML model predicted ${mlResult.label} (score: ${mlScore.toInt()}/100)",
                        metadata = mapOf(
                            "score" to mlScore.toString(),
                            "label" to mlResult.label,
                            "pNonBenign" to pNonBenign.toString(),
                            "classIndex" to mlResult.classIndex.toString(),
                            "pBenign" to mlResult.probabilities[0].toString(),
                            "pSuspicious" to mlResult.probabilities[1].toString(),
                            "pMalicious" to mlResult.probabilities[2].toString()
                        )
                    )
                )
            }

            if (mlFailure != null) {
                evidenceList.add(
                    ThreatEvidence(
                        category = EvidenceCategory.MESSAGE_ML,
                        type = EvidenceType.MESSAGE_ML_SCORE,
                        severity = EvidenceSeverity.CRITICAL,
                        sourceName = "Messages-ML Champion V2",
                        confidence = 1.0f,
                        indicatorText = "Messages ML unavailable",
                        explanation = "Message threat model could not be evaluated; blocking until local analysis is available",
                        metadata = mapOf("status" to "unavailable", "score" to "100.0")
                    )
                )
            }
        }

        // 2. URL-level observations
        urls.forEach { url ->
            if (url.isShortened) {
                evidenceList.add(
                    ThreatEvidence(
                        category = EvidenceCategory.URL_HEURISTIC,
                        type = EvidenceType.SUSPICIOUS_LINK,
                        severity = EvidenceSeverity.MEDIUM,
                        sourceName = analyzerName,
                        confidence = 0.9f,
                        indicatorText = "Shortened URL",
                        explanation = "Shortened URL detected: ${url.domain}",
                        metadata = mapOf("domain" to url.domain, "rawUrl" to url.rawUrl)
                    )
                )
            }
            if (url.isIpAddressUrl) {
                evidenceList.add(
                    ThreatEvidence(
                        category = EvidenceCategory.URL_HEURISTIC,
                        type = EvidenceType.SUSPICIOUS_LINK,
                        severity = EvidenceSeverity.HIGH,
                        sourceName = analyzerName,
                        confidence = 0.95f,
                        indicatorText = "Raw IP URL",
                        explanation = "Raw IP address URL detected: ${url.rawUrl}",
                        metadata = mapOf("domain" to url.domain, "rawUrl" to url.rawUrl)
                    )
                )
            }
        }

        // 3. Message text observations
        val lower = messageText.lowercase()

        if (lower.contains("urgent")) {
            evidenceList.add(
                ThreatEvidence(
                    category = EvidenceCategory.MESSAGE_CONTENT,
                    type = EvidenceType.URGENCY_PRESSURE,
                    severity = EvidenceSeverity.MEDIUM,
                    sourceName = analyzerName,
                    confidence = 1.0f,
                    indicatorText = "Urgency language",
                    explanation = "Urgency language detected"
                )
            )
        }

        if (VERIFY_REQUEST_REGEX.containsMatchIn(lower)) {
            evidenceList.add(
                ThreatEvidence(
                    category = EvidenceCategory.MESSAGE_CONTENT,
                    type = EvidenceType.CREDENTIAL_REQUEST,
                    severity = EvidenceSeverity.MEDIUM,
                    sourceName = analyzerName,
                    confidence = 1.0f,
                    indicatorText = "Verification request",
                    explanation = "Account or identity verification request detected"
                )
            )
        }

        if (lower.contains("account")) {
            evidenceList.add(
                ThreatEvidence(
                    category = EvidenceCategory.MESSAGE_CONTENT,
                    type = EvidenceType.ACCOUNT_THREAT,
                    severity = EvidenceSeverity.LOW,
                    sourceName = analyzerName,
                    confidence = 1.0f,
                    indicatorText = "Account keyword",
                    explanation = "Account-related language detected"
                )
            )
        }

        if (lower.contains("offer")) {
            evidenceList.add(
                ThreatEvidence(
                    category = EvidenceCategory.MESSAGE_CONTENT,
                    type = EvidenceType.GENERIC_SUSPICIOUS_PATTERN,
                    severity = EvidenceSeverity.LOW,
                    sourceName = analyzerName,
                    confidence = 1.0f,
                    indicatorText = "Promotional offer",
                    explanation = "Promotional offer language detected"
                )
            )
        }

        val hasOtpKeyword = OTP_GENERIC_REGEX.containsMatchIn(lower)
        if (hasOtpKeyword) {
            val hasSolicitationDemand = OTP_SOLICITATION_REGEX.containsMatchIn(lower)
            val hasNegation = OTP_NEGATION_WARNING_REGEX.containsMatchIn(lower)
            val isGenuineSolicitation = hasSolicitationDemand && !(hasNegation && !lower.contains("send") && !lower.contains("tell me") && !lower.contains("forward"))

            if (isGenuineSolicitation) {
                evidenceList.add(
                    ThreatEvidence(
                        category = EvidenceCategory.MESSAGE_CONTENT,
                        type = EvidenceType.OTP_SOLICITATION,
                        severity = EvidenceSeverity.HIGH,
                        sourceName = analyzerName,
                        confidence = 1.0f,
                        indicatorText = "Sensitive request: OTP solicitation",
                        explanation = "Sensitive request pattern detected: OTP solicitation"
                    )
                )
            } else {
                evidenceList.add(
                    ThreatEvidence(
                        category = EvidenceCategory.MESSAGE_CONTENT,
                        type = EvidenceType.OTP_PRESENT,
                        severity = EvidenceSeverity.LOW,
                        sourceName = analyzerName,
                        confidence = 1.0f,
                        indicatorText = "Transactional OTP present",
                        explanation = "OTP or verification code present in message"
                    )
                )
            }
        }

        URGENCY_TERMS.firstOrNull { lower.contains(it) }?.let { term ->
            evidenceList.add(
                ThreatEvidence(
                    category = EvidenceCategory.MESSAGE_CONTENT,
                    type = EvidenceType.URGENCY_PRESSURE,
                    severity = EvidenceSeverity.MEDIUM,
                    sourceName = analyzerName,
                    confidence = 1.0f,
                    indicatorText = "Urgency term: $term",
                    explanation = "Urgency language detected: $term"
                )
            )
        }

        FINANCIAL_TERMS.firstOrNull { lower.contains(it) }?.let { term ->
            evidenceList.add(
                ThreatEvidence(
                    category = EvidenceCategory.MESSAGE_CONTENT,
                    type = EvidenceType.FINANCIAL_REQUEST,
                    severity = EvidenceSeverity.LOW,
                    sourceName = analyzerName,
                    confidence = 1.0f,
                    indicatorText = "Financial language: $term",
                    explanation = "Financial language detected: $term"
                )
            )
        }

        if (CREDENTIAL_HARVESTING_REGEX.containsMatchIn(lower)) {
            evidenceList.add(
                ThreatEvidence(
                    category = EvidenceCategory.MESSAGE_CONTENT,
                    type = EvidenceType.CREDENTIAL_REQUEST,
                    severity = EvidenceSeverity.HIGH,
                    sourceName = analyzerName,
                    confidence = 1.0f,
                    indicatorText = "Credential harvesting indicator",
                    explanation = "Explicit credential or password solicitation detected"
                )
            )
        }

        // 4. Contact status observation
        if (!isKnownContact) {
            evidenceList.add(
                ThreatEvidence(
                    category = EvidenceCategory.CONTACT_STATUS,
                    type = EvidenceType.CONTACT_STATUS,
                    severity = EvidenceSeverity.LOW,
                    sourceName = analyzerName,
                    confidence = 1.0f,
                    indicatorText = "Unknown Contact",
                    explanation = "Sender is not a known contact"
                )
            )
        }

        return evidenceList
    }

    companion object {
        private val VERIFY_REQUEST_REGEX = Regex(
            """\bverify\s+(?:your\s+)?(?:account|identity|details|card|kyc|profile|login|information)\b|\bverification\s+required\b""",
            RegexOption.IGNORE_CASE
        )

        private val CREDENTIAL_HARVESTING_REGEX = Regex(
            """\b(?:enter|send|share|tell|submit|provide|update|reset)\s+(?:your\s+)?(?:password|pin|credentials|passcode|cvv|secret\s+key|login\s+details)\b|\b(?:password|pin|credentials)\s+(?:required|needed|to\s+(?:unlock|continue|access))\b""",
            RegexOption.IGNORE_CASE
        )

        private val OTP_SOLICITATION_REGEX = Regex(
            """\b(?:send|share|tell|forward|provide|give|reply\s+with|message)\s+(?:(?:me|us|your|the|this|that|secret|account)\s+)*(?:otp|code|pin|password|one\s*time\s*password|verification\s*code)\b|\b(?:otp|code|pin)\s+(?:to\s+(?:cancel|stop|verify|customer\s*care|support|executive|officer|manager|number)|immediately)\b|\bverify\s+(?:(?:your|account|login)\s+)*otp\b""",
            RegexOption.IGNORE_CASE
        )

        private val OTP_NEGATION_WARNING_REGEX = Regex(
            """\b(never\s+(?:share|tell|disclose|give)|do\s+not\s+(?:share|disclose|give|tell|forward)|don'?t\s+(?:share|tell|disclose|give)|bank\s+never\s+asks|strictly\s+confidential|keep\s+(?:it\s+)?confidential)\b""",
            RegexOption.IGNORE_CASE
        )

        private val OTP_GENERIC_REGEX = Regex(
            """\b(otp|one\s*time\s*password|verification\s*code|auth\s*code|security\s*code|login\s*pin|2fa)\b""",
            RegexOption.IGNORE_CASE
        )

        private val URGENCY_TERMS = listOf(
            "immediately",
            "act now",
            "account suspended",
            "account blocked"
        )

        private val FINANCIAL_TERMS = listOf(
            "bank",
            "payment",
            "refund",
            "wallet",
            "upi",
            "transaction"
        )
    }
}
