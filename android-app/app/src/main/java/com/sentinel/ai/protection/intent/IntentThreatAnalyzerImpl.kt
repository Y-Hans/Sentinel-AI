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
import com.sentinel.ai.core.evidence.EvidenceCategory
import com.sentinel.ai.core.evidence.EvidenceSeverity
import com.sentinel.ai.core.evidence.EvidenceType
import com.sentinel.ai.core.evidence.ThreatEvidence
import com.sentinel.ai.core.fusion.DefaultRiskFusionEngine
import com.sentinel.ai.core.fusion.FusionContext
import com.sentinel.ai.core.fusion.RiskFusionEngine
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
    private val threatJournal: ThreatJournal,
    private val riskFusionEngine: RiskFusionEngine = DefaultRiskFusionEngine()
) : IntentThreatAnalyzer {

    override suspend fun analyze(payload: IntentPayload): ScanResult {
        return when (payload) {
            is UrlPayload -> {
                val heuristicEvidence = linkScanner.scan(payload.url)
                val mlScore = try {
                    when (val result = FeatureExtractor.extract(payload.url)) {
                        is com.sentinel.ai.ml.FeatureExtractionResult.Success -> {
                            if (result.features.size == FeatureExtractor.FEATURE_COUNT) {
                                (mlInferenceEngine.predict(result.features) * 100f).coerceIn(0f, 100f)
                            } else null
                        }
                        is com.sentinel.ai.ml.FeatureExtractionResult.Failure -> null
                    }
                } catch (e: Exception) {
                    null
                }

                val evidenceList = mutableListOf<ThreatEvidence>()
                evidenceList.addAll(heuristicEvidence)

                if (mlScore != null) {
                    evidenceList.add(
                        ThreatEvidence(
                            category = EvidenceCategory.URL_ML,
                            type = EvidenceType.URL_ML_SCORE,
                            severity = when {
                                mlScore >= 90f -> EvidenceSeverity.CRITICAL
                                mlScore >= 70f -> EvidenceSeverity.HIGH
                                mlScore >= 40f -> EvidenceSeverity.MEDIUM
                                else -> EvidenceSeverity.LOW
                            },
                            sourceName = "ML Classifier",
                            confidence = 0.9f,
                            indicatorText = "${mlScore.toInt()}/100",
                            explanation = "Machine learning model score: ${mlScore.toInt()}/100",
                            metadata = mapOf("mlScore" to mlScore.toString())
                        )
                    )
                }

                val timestamp = System.currentTimeMillis()
                val fusionContext = FusionContext(
                    source = "Intent (Link)",
                    target = payload.url,
                    timestamp = timestamp
                )

                val fusionResult = riskFusionEngine.fuse(evidenceList, fusionContext)

                val finalResult = fusionResult.toScanResult(
                    id = java.util.UUID.randomUUID().toString(),
                    source = "Intent (Link)",
                    target = payload.url,
                    timestamp = timestamp,
                    senderDisplayName = null,
                    senderIdentifier = null
                )

                // 1. Direct durable Room persistence (awaiting completion)
                threatJournal.recordScanResult(finalResult)

                // 2. Optional transient event bus emission for reactive UI consumers
                threatEventBus.emit(ThreatEvent.LinkThreatDetected(finalResult))

                finalResult
            }
            is FilePayload -> {
                val heuristicEvidence = fileScanner.scan(payload.uri)

                val timestamp = System.currentTimeMillis()
                val fusionContext = FusionContext(
                    source = "Intent (File)",
                    target = payload.uri.toString(),
                    timestamp = timestamp
                )

                val fusionResult = riskFusionEngine.fuse(heuristicEvidence, fusionContext)

                val finalResult = fusionResult.toScanResult(
                    id = java.util.UUID.randomUUID().toString(),
                    source = "Intent (File)",
                    target = payload.uri.toString(),
                    timestamp = timestamp,
                    senderDisplayName = null,
                    senderIdentifier = null
                )

                // 1. Direct durable Room persistence (awaiting completion)
                threatJournal.recordScanResult(finalResult)

                // 2. Optional transient event bus emission for reactive UI consumers
                threatEventBus.emit(ThreatEvent.FileThreatDetected(finalResult))

                finalResult
            }
        }
    }
}
