package com.sentinel.ai.protection.intent.reputation

import android.util.Log
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.protection.intent.heuristic.toRiskLevel
import javax.inject.Inject

class EvidenceCombiner @Inject constructor() {

    fun combine(
        heuristicResult: ScanResult,
        reputationEvidence: List<ReputationResult>
    ): ScanResult {
        if (reputationEvidence.isEmpty()) {
            return heuristicResult
        }

        // Convert baseline heuristic score to a probability (0.0 to 1.0)
        val pHeuristic = (heuristicResult.riskScore / 100f).coerceIn(0f, 1f)

        // Calculate complementary non-threat probability: (1 - P_heuristic) * Product(1 - P_i) for positive threat indicators
        var complementaryProb = 1f - pHeuristic

        for (evidence in reputationEvidence) {
            val pEvidence = when (evidence.reputation) {
                ReputationVerdict.MALICIOUS -> 0.90f * evidence.confidence.coerceIn(0f, 1f)
                ReputationVerdict.SUSPICIOUS -> 0.60f * evidence.confidence.coerceIn(0f, 1f)
                else -> 0.0f
            }
            complementaryProb *= (1f - pEvidence)
        }

        // threat probability: 1 - complementary probability
        val threatProbBeforeDiscount = (1f - complementaryProb).coerceIn(0f, 1f)
        val threatScoreBeforeDiscount = threatProbBeforeDiscount * 100f

        // Apply CLEAN verdict discounting if present, up to calibrated limits
        val maxCleanConfidence = reputationEvidence
            .filter { it.reputation == ReputationVerdict.CLEAN }
            .maxOfOrNull { it.confidence }
            ?: 0f

        val combinedScore = if (maxCleanConfidence > 0f) {
            // High heuristic threat (70%+) cannot be fully suppressed (max 20% discount).
            // Medium threat (30%-69%) can be partially discounted (max 50% discount).
            // Safe ranges can be discounted up to 100%.
            val maxDiscountLimit = when {
                threatScoreBeforeDiscount >= 70f -> 0.20f
                threatScoreBeforeDiscount >= 30f -> 0.50f
                else -> 1.00f
            }
            val cleanDiscount = maxCleanConfidence * maxDiscountLimit
            threatScoreBeforeDiscount * (1f - cleanDiscount)
        } else {
            threatScoreBeforeDiscount
        }

        val combinedLevel = combinedScore.toRiskLevel()

        // Debug Instrumentation for Calibration (does not affect production behavior)
        try {
            if (Log.isLoggable("SentinelCalibration", Log.DEBUG)) {
                Log.d("SentinelCalibration", "--- Reputation Fusion Adjustment ---")
                Log.d("SentinelCalibration", "Heuristic Total Score: ${heuristicResult.riskScore}")
                Log.d("SentinelCalibration", "Threat Score Before Discount: $threatScoreBeforeDiscount")
                Log.d("SentinelCalibration", "Max Clean Confidence: $maxCleanConfidence")
                Log.d("SentinelCalibration", "Final Score after Clean discount: $combinedScore")
                Log.d("SentinelCalibration", "------------------------------------")
            } else {
                Log.i("SentinelCalibration", "Heuristic: ${heuristicResult.riskScore} -> Final: $combinedScore (via clean confidence: $maxCleanConfidence)")
            }
        } catch (t: Throwable) {
            println("SentinelCalibration - Heuristic: ${heuristicResult.riskScore} -> Final: $combinedScore (via clean confidence: $maxCleanConfidence)")
        }

        return heuristicResult.copy(
            riskLevel = combinedLevel,
            riskScore = combinedScore,
            explanation = buildExplanation(heuristicResult.explanation, reputationEvidence)
        )
    }

    private fun buildExplanation(
        heuristicExplanation: String,
        reputationEvidence: List<ReputationResult>
    ): String {
        val reputationSummary = reputationEvidence.joinToString("; ") { evidence ->
            val confidencePercent = (evidence.confidence.coerceIn(0f, 1f) * 100).toInt()
            "${evidence.providerName}=${evidence.reputation.name.lowercase()} ($confidencePercent%): ${evidence.reason}"
        }

        return if (reputationSummary.isBlank()) {
            heuristicExplanation
        } else {
            "$heuristicExplanation Reputation evidence: $reputationSummary."
        }
    }
}
