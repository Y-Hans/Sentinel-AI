package com.sentinel.ai.core.fusion

import com.sentinel.ai.core.evidence.EvidenceCategory
import com.sentinel.ai.core.evidence.EvidenceSeverity
import com.sentinel.ai.core.evidence.EvidenceType
import com.sentinel.ai.core.evidence.ThreatEvidence
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanReason
import com.sentinel.ai.core.model.ScanReasonSource
import com.sentinel.ai.core.model.defaultHeadline
import com.sentinel.ai.core.model.toProtectionDecision
import com.sentinel.ai.core.sender.SenderType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Sole authoritative domain boundary responsible for synthesizing aggregated [ThreatEvidence]
 * observations and [FusionContext] into final risk scores, risk levels, and protection decisions.
 */
interface RiskFusionEngine {
    /**
     * Evaluates a collection of observed [evidence] items under the provided [context]
     * to produce a deterministic [RiskFusionResult].
     */
    fun fuse(
        evidence: List<ThreatEvidence>,
        context: FusionContext = FusionContext.EMPTY
    ): RiskFusionResult
}

/**
 * Standard pure-domain implementation of [RiskFusionEngine].
 *
 * Implements vector grouping, cross-signal correlation, confidence weighting,
 * contextual trust dampening, and critical indicator overrides.
 */
@Singleton
class DefaultRiskFusionEngine @Inject constructor() : RiskFusionEngine {

    override fun fuse(
        evidence: List<ThreatEvidence>,
        context: FusionContext
    ): RiskFusionResult {
        // Fast-path: Zero evidence
        if (evidence.isEmpty()) {
            return RiskFusionResult(
                riskScore = 0.0f,
                riskLevel = RiskLevel.GREEN,
                decision = ProtectionDecision.ALLOW,
                confidence = 1.0f,
                headline = ProtectionDecision.ALLOW.defaultHeadline(),
                summary = "No threat evidence detected.",
                explanation = "No threat evidence detected.",
                reasons = emptyList(),
                primaryEvidence = emptyList(),
                suppressedEvidence = emptyList()
            )
        }

        // 1. Deduplication
        val (primaryEvidence, suppressedEvidence) = deduplicateEvidence(evidence)

        // 2. Vector Clustering
        val payloadItems = primaryEvidence.filter { it.isPayloadVector() }
        val intentItems = primaryEvidence.filter { it.isIntentVector() }
        val contextItems = primaryEvidence.filter { it.isContextVector() }

        // 3. Vector-Specific Scoring
        val payloadScore = calculatePayloadScore(payloadItems)
        val intentScore = calculateIntentScore(intentItems)

        // 4. Cross-Vector Compounding
        val compoundBoost = calculateCrossVectorCompound(payloadItems, intentItems, context)

        // 5. Contextual Trust Dampening
        val rawCombinedScore = max(payloadScore, intentScore) + (min(payloadScore, intentScore) * 0.4f) + compoundBoost
        val dampenedScore = applyContextualDampening(rawCombinedScore, payloadScore, intentScore, primaryEvidence, context)

        // 6. Critical Indicator Escalation
        val hasCriticalOverride = primaryEvidence.any {
            it.severity == EvidenceSeverity.CRITICAL && it.confidence >= CRITICAL_CONFIDENCE_THRESHOLD
        }

        val finalScore = if (hasCriticalOverride) {
            max(dampenedScore, 90.0f).coerceIn(0.0f, 100.0f)
        } else {
            dampenedScore.coerceIn(0.0f, 100.0f)
        }

        // 7. RiskLevel & Decision Derivation
        val riskLevel = when {
            finalScore >= CRITICAL_THRESHOLD -> RiskLevel.CRITICAL
            finalScore >= RED_THRESHOLD -> RiskLevel.RED
            finalScore >= YELLOW_THRESHOLD -> RiskLevel.YELLOW
            else -> RiskLevel.GREEN
        }
        val decision = riskLevel.toProtectionDecision()

        // 8. Confidence Synthesis
        val aggregateConfidence = calculateAggregateConfidence(primaryEvidence)

        // 9. Reasons & Explanation Synthesis
        val reasons = synthesizeReasons(primaryEvidence)
        val explanation = synthesizeExplanation(primaryEvidence, decision)
        val headline = synthesizeHeadline(decision, primaryEvidence)
        val summary = explanation

        return RiskFusionResult(
            riskScore = finalScore,
            riskLevel = riskLevel,
            decision = decision,
            confidence = aggregateConfidence,
            headline = headline,
            summary = summary,
            explanation = explanation,
            reasons = reasons,
            primaryEvidence = primaryEvidence,
            suppressedEvidence = suppressedEvidence
        )
    }

    private fun deduplicateEvidence(evidence: List<ThreatEvidence>): Pair<List<ThreatEvidence>, List<ThreatEvidence>> {
        val primary = mutableListOf<ThreatEvidence>()
        val suppressed = mutableListOf<ThreatEvidence>()
        val seenKeys = mutableMapOf<String, ThreatEvidence>()

        evidence.forEach { item ->
            val key = "${item.category}|${item.type}|${item.indicatorText.trim().lowercase()}"
            val existing = seenKeys[key]
            if (existing == null) {
                seenKeys[key] = item
                primary.add(item)
            } else {
                // Keep the one with higher severity or confidence
                if (item.severity.ordinal > existing.severity.ordinal ||
                    (item.severity.ordinal == existing.severity.ordinal && item.confidence > existing.confidence)
                ) {
                    seenKeys[key] = item
                    primary.remove(existing)
                    primary.add(item)
                    suppressed.add(existing)
                } else {
                    suppressed.add(item)
                }
            }
        }

        return primary to suppressed
    }

    private fun calculatePayloadScore(items: List<ThreatEvidence>): Float {
        if (items.isEmpty()) return 0.0f

        // Generic payload scoring: peak + corroboration
        val scores = items.map { it.effectiveScore() }
        val peak = scores.maxOrNull() ?: 0.0f
        val corroboratingCount = items.count { it.severity >= EvidenceSeverity.LOW && it.confidence >= 0.4f } - 1
        val corroborationBoost = (max(0, corroboratingCount) * 5.0f).coerceAtMost(15.0f)

        return (peak + corroborationBoost).coerceIn(0.0f, 100.0f)
    }

    private fun calculateIntentScore(items: List<ThreatEvidence>): Float {
        if (items.isEmpty()) return 0.0f

        val effectiveScores = items.map { it.effectiveScore() }
        val peak = effectiveScores.maxOrNull() ?: 0.0f

        // Distinct non-INFO intent types count
        val distinctIntentTypes = items
            .filter { it.severity >= EvidenceSeverity.LOW && it.confidence >= 0.4f }
            .map { it.type }
            .distinct()

        // Multiple distinct attack intent types compound (e.g. urgency + credential request + financial claim)
        val corroborationBoost = when {
            distinctIntentTypes.size >= 4 -> 30.0f
            distinctIntentTypes.size == 3 -> 20.0f
            distinctIntentTypes.size == 2 -> 10.0f
            else -> 0.0f
        }

        return (peak + corroborationBoost).coerceIn(0.0f, 100.0f)
    }

    private fun calculateCrossVectorCompound(
        payloadItems: List<ThreatEvidence>,
        intentItems: List<ThreatEvidence>,
        context: FusionContext
    ): Float {
        if (payloadItems.isEmpty() || intentItems.isEmpty()) return 0.0f

        val maxPayloadSeverity = payloadItems.maxOfOrNull { it.severity } ?: EvidenceSeverity.INFO
        val maxIntentSeverity = intentItems.maxOfOrNull { it.severity } ?: EvidenceSeverity.INFO

        // Significant attack intent + Suspicious payload is a classic credential harvesting / malware delivery vector
        return when {
            maxPayloadSeverity >= EvidenceSeverity.HIGH && maxIntentSeverity >= EvidenceSeverity.HIGH -> 25.0f
            maxPayloadSeverity >= EvidenceSeverity.MEDIUM && maxIntentSeverity >= EvidenceSeverity.MEDIUM -> 15.0f
            maxPayloadSeverity >= EvidenceSeverity.LOW && maxIntentSeverity >= EvidenceSeverity.MEDIUM -> 10.0f
            else -> 0.0f
        }
    }

    private fun applyContextualDampening(
        combinedScore: Float,
        payloadScore: Float,
        intentScore: Float,
        evidence: List<ThreatEvidence>,
        context: FusionContext
    ): Float {
        var dampened = combinedScore

        val isServiceSender = context.senderProfile?.senderType == SenderType.SERVICE ||
            context.senderProfile?.senderType == SenderType.GOVERNMENT

        // 1. Transactional OTP delivered by verified Service / Government sender
        if (isServiceSender) {
            val isOtpDeliveryOnly = evidence.all {
                it.type == EvidenceType.OTP_PRESENT ||
                    it.type == EvidenceType.OTP_GENERATION ||
                    it.type == EvidenceType.SENDER_HEADER_PATTERN ||
                    it.severity <= EvidenceSeverity.LOW
            }
            if (isOtpDeliveryOnly) {
                return min(dampened, 0.0f)
            }
        }

        // 2. Known Contact Contextual Dampening
        if (context.isKnownContact) {
            val maxPayloadSeverity = evidence.filter { it.isPayloadVector() }.maxOfOrNull { it.severity } ?: EvidenceSeverity.INFO

            // CRITICAL INVARIANT: Trust context NEVER suppresses HIGH/CRITICAL payload threats (e.g. compromised contact sending malware)
            if (maxPayloadSeverity < EvidenceSeverity.HIGH) {
                // Dampen mild conversational keywords
                val dampeningFactor = when {
                    intentScore <= 45.0f -> 30.0f
                    intentScore <= 65.0f -> 20.0f
                    else -> 10.0f
                }
                dampened = max(0.0f, dampened - dampeningFactor)
            }
        }

        return dampened
    }

    private fun ThreatEvidence.effectiveScore(): Float {
        val explicitScore = metadata["score"]?.toFloatOrNull()
        val baseScore = explicitScore ?: when (severity) {
            EvidenceSeverity.CRITICAL -> 95.0f
            EvidenceSeverity.HIGH -> 70.0f
            EvidenceSeverity.MEDIUM -> 40.0f
            EvidenceSeverity.LOW -> 20.0f
            EvidenceSeverity.INFO -> 0.0f
        }
        return (baseScore * confidence.coerceIn(0.0f, 1.0f))
    }

    private fun ThreatEvidence.isPayloadVector(): Boolean =
        category == EvidenceCategory.URL_HEURISTIC ||
            category == EvidenceCategory.URL_ML ||
            category == EvidenceCategory.FILE_HEURISTIC ||
            type == EvidenceType.SUSPICIOUS_LINK ||
            type == EvidenceType.SUSPICIOUS_FILE ||
            type == EvidenceType.URL_ML_SCORE

    private fun ThreatEvidence.isIntentVector(): Boolean =
        category == EvidenceCategory.MESSAGE_CONTENT ||
            category == EvidenceCategory.MESSAGE_ML ||
            type == EvidenceType.OTP_SOLICITATION ||
            type == EvidenceType.CREDENTIAL_REQUEST ||
            type == EvidenceType.ACCOUNT_THREAT ||
            type == EvidenceType.URGENCY_PRESSURE ||
            type == EvidenceType.FINANCIAL_REQUEST ||
            type == EvidenceType.IMPERSONATION ||
            type == EvidenceType.GENERIC_SUSPICIOUS_PATTERN

    private fun ThreatEvidence.isContextVector(): Boolean =
        category == EvidenceCategory.SENDER_IDENTITY ||
            category == EvidenceCategory.CONTACT_STATUS ||
            category == EvidenceCategory.CONTEXTUAL_CORRELATION ||
            type == EvidenceType.SENDER_HEADER_PATTERN ||
            type == EvidenceType.CONTACT_STATUS ||
            type == EvidenceType.OTP_PRESENT ||
            type == EvidenceType.OTP_GENERATION

    private fun calculateAggregateConfidence(evidence: List<ThreatEvidence>): Float {
        val nonInfoEvidence = evidence.filter { it.severity > EvidenceSeverity.INFO }
        if (nonInfoEvidence.isEmpty()) return 1.0f

        val totalWeightedConfidence = nonInfoEvidence.sumOf { (it.confidence * (it.severity.ordinal + 1)).toDouble() }
        val totalWeights = nonInfoEvidence.sumOf { (it.severity.ordinal + 1).toDouble() }
        return if (totalWeights > 0.0) (totalWeightedConfidence / totalWeights).toFloat().coerceIn(0.0f, 1.0f) else 1.0f
    }

    private fun synthesizeReasons(evidence: List<ThreatEvidence>): List<ScanReason> {
        return evidence
            .filter { it.severity >= EvidenceSeverity.LOW }
            .map { item ->
                ScanReason(
                    source = ScanReasonSource.LOCAL_HEURISTIC,
                    sourceName = item.sourceName,
                    message = item.explanation,
                    riskLevel = when (item.severity) {
                        EvidenceSeverity.CRITICAL -> RiskLevel.CRITICAL
                        EvidenceSeverity.HIGH -> RiskLevel.RED
                        EvidenceSeverity.MEDIUM -> RiskLevel.YELLOW
                        EvidenceSeverity.LOW, EvidenceSeverity.INFO -> null
                    }
                )
            }
    }

    private fun synthesizeExplanation(evidence: List<ThreatEvidence>, decision: ProtectionDecision): String {
        val relevantExplanations = evidence
            .filter { it.severity >= EvidenceSeverity.LOW && it.explanation.isNotBlank() }
            .map { it.explanation.trim().trimEnd('.') }
            .distinct()

        return if (relevantExplanations.isNotEmpty()) {
            relevantExplanations.joinToString("; ")
        } else {
            when (decision) {
                ProtectionDecision.ALLOW -> "No strong threat evidence detected"
                ProtectionDecision.WARN -> "Suspicious signals detected"
                ProtectionDecision.BLOCK -> "Critical threat evidence detected"
            }
        }
    }

    private fun synthesizeHeadline(decision: ProtectionDecision, evidence: List<ThreatEvidence>): String {
        val hasLinkThreat = evidence.any { it.isPayloadVector() && it.severity >= EvidenceSeverity.MEDIUM }
        return when (decision) {
            ProtectionDecision.ALLOW -> ProtectionDecision.ALLOW.defaultHeadline()
            ProtectionDecision.WARN -> if (hasLinkThreat) "Suspicious link evidence detected" else "Suspicious message activity detected"
            ProtectionDecision.BLOCK -> ProtectionDecision.BLOCK.defaultHeadline()
        }
    }

    companion object {
        const val YELLOW_THRESHOLD = 40.0f
        const val RED_THRESHOLD = 70.0f
        const val CRITICAL_THRESHOLD = 90.0f
        const val CRITICAL_CONFIDENCE_THRESHOLD = 0.75f
    }
}
