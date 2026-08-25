package com.sentinel.ai.core.evidence

/**
 * Top-level domain category identifying the originating signal source.
 */
enum class EvidenceCategory {
    SENDER_IDENTITY,
    CONTACT_STATUS,
    MESSAGE_CONTENT,
    MESSAGE_ML,
    URL_HEURISTIC,
    URL_ML,
    FILE_HEURISTIC,
    CONTEXTUAL_CORRELATION
}

/**
 * Coarse severity rating of an individual evidence observation.
 */
enum class EvidenceSeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Minimal cross-analyzer domain descriptors identifying specific threat or trust signals.
 */
enum class EvidenceType {
    // Sender & Contact
    SENDER_HEADER_PATTERN,
    CONTACT_STATUS,

    // Message Content & Heuristics
    OTP_PRESENT,
    OTP_GENERATION,
    OTP_SOLICITATION,
    URGENCY_PRESSURE,
    ACCOUNT_THREAT,
    CREDENTIAL_REQUEST,
    FINANCIAL_REQUEST,
    IMPERSONATION,

    // URL & File Signals
    SUSPICIOUS_LINK,
    SUSPICIOUS_FILE,

    // ML Inferences
    URL_ML_SCORE,
    MESSAGE_ML_SCORE,

    // General
    GENERIC_SUSPICIOUS_PATTERN
}

/**
 * Immutable atomic unit of threat or trust evidence produced by analyzers.
 *
 * Analyzers produce evidence describing observations; the Risk Fusion Engine
 * evaluates combinations of evidence to compute the final risk score and verdict.
 */
data class ThreatEvidence(
    val category: EvidenceCategory,
    val type: EvidenceType,
    val severity: EvidenceSeverity,
    val sourceName: String,
    val confidence: Float = 1.0f,
    val indicatorText: String,
    val explanation: String,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(confidence in 0f..1f) {
            "Confidence must be between 0.0 and 1.0, found: $confidence"
        }
    }
}
