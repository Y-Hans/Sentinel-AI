package com.sentinel.ai.agents.whatsapp

import com.sentinel.ai.core.analyzer.AnalysisResult
import com.sentinel.ai.core.analyzer.ThreatAnalyzer
import com.sentinel.ai.core.event.schema.UrlAnalysisItem
import com.sentinel.ai.core.evidence.EvidenceCategory
import com.sentinel.ai.core.evidence.EvidenceSeverity
import com.sentinel.ai.core.evidence.EvidenceType
import com.sentinel.ai.core.evidence.ThreatEvidence
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain payload input for notification threat evidence extraction.
 */
data class NotificationAnalysisInput(
    val messageText: String,
    val urls: List<UrlAnalysisItem> = emptyList(),
    val isKnownContact: Boolean = false
)

/**
 * Signal analyzer responsible for extracting structured [ThreatEvidence] observations
 * from notification text, attached URLs, and contact parameters.
 *
 * NOTE: This analyzer observes and emits evidence; it does NOT compute final global risk scores,
 * assign RiskLevels, or determine ProtectionDecisions.
 */
@Singleton
class NotificationThreatAnalyzer @Inject constructor() : ThreatAnalyzer<NotificationAnalysisInput> {

    override val analyzerName: String = "NotificationThreatAnalyzer"

    override suspend fun analyze(input: NotificationAnalysisInput): AnalysisResult {
        return AnalysisResult(
            evidence = extractEvidence(input.messageText, input.urls, input.isKnownContact)
        )
    }

    fun extractEvidence(
        messageText: String,
        urls: List<UrlAnalysisItem> = emptyList(),
        isKnownContact: Boolean = false
    ): List<ThreatEvidence> {
        val evidenceList = mutableListOf<ThreatEvidence>()

        // 1. URL-level observations
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

        // 2. Message text observations
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

        if (lower.contains("verify")) {
            evidenceList.add(
                ThreatEvidence(
                    category = EvidenceCategory.MESSAGE_CONTENT,
                    type = EvidenceType.CREDENTIAL_REQUEST,
                    severity = EvidenceSeverity.MEDIUM,
                    sourceName = analyzerName,
                    confidence = 1.0f,
                    indicatorText = "Sensitive request",
                    explanation = "Sensitive request pattern detected"
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

        if (lower.contains("otp")) {
            evidenceList.add(
                ThreatEvidence(
                    category = EvidenceCategory.MESSAGE_CONTENT,
                    type = EvidenceType.OTP_SOLICITATION,
                    severity = EvidenceSeverity.HIGH,
                    sourceName = analyzerName,
                    confidence = 1.0f,
                    indicatorText = "Sensitive request: OTP",
                    explanation = "Sensitive request pattern detected"
                )
            )
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

        CREDENTIAL_TERMS.firstOrNull { lower.contains(it) }?.let { term ->
            evidenceList.add(
                ThreatEvidence(
                    category = EvidenceCategory.MESSAGE_CONTENT,
                    type = EvidenceType.CREDENTIAL_REQUEST,
                    severity = EvidenceSeverity.HIGH,
                    sourceName = analyzerName,
                    confidence = 1.0f,
                    indicatorText = "Credential harvesting: $term",
                    explanation = "Credential harvesting indicator detected: $term"
                )
            )
        }

        // 3. Contact status observation
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

        private val CREDENTIAL_TERMS = listOf(
            "login",
            "password",
            "verification code",
            "security code"
        )
    }
}
