package com.sentinel.ai.core.fusion

import com.sentinel.ai.core.evidence.ThreatEvidence
import com.sentinel.ai.core.model.LocalEvidence
import com.sentinel.ai.core.model.ProtectionAction
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanReason
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.core.model.defaultAction
import com.sentinel.ai.core.model.defaultHeadline
import com.sentinel.ai.core.sender.ContactResolution
import com.sentinel.ai.core.sender.SenderProfile
import java.util.UUID

/**
 * Contextual metadata provided alongside evidence during risk fusion evaluation.
 *
 * NOTE: [timestamp] is a caller-provided value (not defaulted to System.currentTimeMillis())
 * to ensure that fusion evaluation remains strictly deterministic.
 */
data class FusionContext(
    val source: String = "",
    val target: String? = null,
    val senderProfile: SenderProfile? = null,
    val contactResolution: ContactResolution? = null,
    val isKnownContact: Boolean = contactResolution?.isKnownContact ?: senderProfile?.isKnownContact ?: false,
    val timestamp: Long = 0L
) {
    companion object {
        val EMPTY = FusionContext()
    }
}

/**
 * Domain evaluation result produced by [RiskFusionEngine].
 *
 * Contains all synthesized risk parameters required to construct application-facing [ScanResult]s.
 */
data class RiskFusionResult(
    val riskScore: Float,
    val riskLevel: RiskLevel,
    val decision: ProtectionDecision,
    val confidence: Float,
    val headline: String,
    val summary: String,
    val explanation: String,
    val reasons: List<ScanReason>,
    val primaryEvidence: List<ThreatEvidence>,
    val suppressedEvidence: List<ThreatEvidence> = emptyList()
) {
    /**
     * Maps this fusion verdict into the stable application-facing [ScanResult] contract.
     */
    fun toScanResult(
        id: String = UUID.randomUUID().toString(),
        source: String = "",
        target: String? = null,
        timestamp: Long = 0L,
        senderDisplayName: String? = null,
        senderIdentifier: String? = null,
        localEvidence: LocalEvidence? = null,
        recommendedAction: ProtectionAction = decision.defaultAction()
    ): ScanResult {
        return ScanResult(
            id = id,
            source = source,
            senderDisplayName = senderDisplayName,
            senderIdentifier = senderIdentifier,
            riskLevel = riskLevel,
            riskScore = riskScore,
            explanation = explanation,
            timestamp = timestamp,
            decision = decision,
            confidence = confidence,
            headline = headline,
            summary = summary,
            reasons = reasons,
            localEvidence = localEvidence,
            recommendedAction = recommendedAction,
            target = target
        )
    }
}
