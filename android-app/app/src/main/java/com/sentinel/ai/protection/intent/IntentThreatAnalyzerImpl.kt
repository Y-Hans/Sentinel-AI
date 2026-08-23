package com.sentinel.ai.protection.intent

import android.content.Context
import com.sentinel.ai.core.event.ThreatEvent
import com.sentinel.ai.core.event.ThreatEventBus
import com.sentinel.ai.core.event.ThreatJournal
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.ml.FeatureExtractor
import com.sentinel.ai.ml.MLInferenceEngine
import com.sentinel.ai.protection.intent.file.FileScanner
import com.sentinel.ai.protection.intent.link.LinkScanner
import com.sentinel.ai.protection.intent.model.FilePayload
import com.sentinel.ai.protection.intent.model.IntentPayload
import com.sentinel.ai.protection.intent.model.UrlPayload
import javax.inject.Inject
import javax.inject.Singleton
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanReason
import com.sentinel.ai.core.model.ScanReasonSource
import com.sentinel.ai.core.model.LocalEvidence
import com.sentinel.ai.core.model.toProtectionDecision
import com.sentinel.ai.core.model.defaultHeadline
import com.sentinel.ai.core.model.defaultAction

@Singleton
class IntentThreatAnalyzerImpl @Inject constructor(
    private val linkScanner: LinkScanner,
    private val fileScanner: FileScanner,
    private val threatEventBus: ThreatEventBus,
    private val mlInferenceEngine: MLInferenceEngine,
    private val threatJournal: ThreatJournal
) : IntentThreatAnalyzer {

    override suspend fun analyze(payload: IntentPayload): ScanResult {
        return when (payload) {
            is UrlPayload -> {
                val heuristicResult = linkScanner.scan(payload.url)
                val mlScore = try {
                    val features = FeatureExtractor.extract(payload.url)
                    if (features.size == FeatureExtractor.FEATURE_COUNT) {
                        (mlInferenceEngine.predict(features) * 100f).coerceIn(0f, 100f)
                    } else null
                } catch (e: Exception) {
                    null
                }

                // Combine heuristics and ML
                val finalRiskScore = if (mlScore != null) {
                    (heuristicResult.riskScore * 0.7f + mlScore * 0.3f).coerceIn(0f, 100f)
                } else {
                    heuristicResult.riskScore
                }

                val finalRiskLevel = when {
                    finalRiskScore >= 90f -> RiskLevel.CRITICAL
                    finalRiskScore >= 70f -> RiskLevel.RED
                    finalRiskScore >= 40f -> RiskLevel.YELLOW
                    else -> RiskLevel.GREEN
                }
                
                val reasons = heuristicResult.reasons.toMutableList()
                if (mlScore != null) {
                    reasons.add(ScanReason(
                        source = ScanReasonSource.LOCAL_HEURISTIC,
                        sourceName = "ML Classifier",
                        message = "Machine learning model score: ${mlScore.toInt()}/100",
                        riskLevel = if (mlScore >= 70f) RiskLevel.RED else null
                    ))
                }

                val finalResult = heuristicResult.copy(
                    riskScore = finalRiskScore,
                    riskLevel = finalRiskLevel,
                    reasons = reasons,
                    decision = finalRiskLevel.toProtectionDecision(),
                    headline = finalRiskLevel.toProtectionDecision().defaultHeadline(),
                    recommendedAction = finalRiskLevel.toProtectionDecision().defaultAction()
                )

                // 1. Direct durable Room persistence (awaiting completion)
                threatJournal.recordScanResult(finalResult)

                // 2. Optional transient event bus emission for reactive UI consumers
                threatEventBus.emit(ThreatEvent.LinkThreatDetected(finalResult))

                finalResult
            }
            is FilePayload -> {
                val heuristicResult = fileScanner.scan(payload.uri)

                // 1. Direct durable Room persistence (awaiting completion)
                threatJournal.recordScanResult(heuristicResult)

                // 2. Optional transient event bus emission for reactive UI consumers
                threatEventBus.emit(ThreatEvent.FileThreatDetected(heuristicResult))

                heuristicResult
            }
        }
    }
}
