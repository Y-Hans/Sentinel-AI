package com.sentinel.ai.protection.intent

import com.sentinel.ai.core.event.ThreatEvent
import com.sentinel.ai.core.event.ThreatEventBus
import com.sentinel.ai.core.event.ThreatJournal
import com.sentinel.ai.core.evidence.EvidenceCategory
import com.sentinel.ai.core.evidence.EvidenceSeverity
import com.sentinel.ai.core.evidence.EvidenceType
import com.sentinel.ai.core.evidence.ThreatEvidence
import com.sentinel.ai.core.fusion.FusionContext
import com.sentinel.ai.core.fusion.RiskFusionEngine
import com.sentinel.ai.core.ml.url.UrlScanner
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.protection.intent.file.FileScanner
import com.sentinel.ai.protection.intent.link.LinkScanner
import com.sentinel.ai.protection.intent.model.FilePayload
import com.sentinel.ai.protection.intent.model.IntentPayload
import com.sentinel.ai.protection.intent.model.UrlPayload
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentThreatAnalyzerImpl @Inject constructor(
    private val linkScanner: LinkScanner,
    private val fileScanner: FileScanner,
    private val threatEventBus: ThreatEventBus,
    private val urlScanner: UrlScanner,
    private val threatJournal: ThreatJournal,
    private val riskFusionEngine: RiskFusionEngine
) : IntentThreatAnalyzer {

    override suspend fun analyze(payload: IntentPayload): ScanResult {
        return when (payload) {
            is UrlPayload -> {
                val heuristicEvidence = linkScanner.scan(payload.url)
                var mlFailure: Exception? = null
                val mlResult = try {
                    urlScanner.scan(payload.url)
                } catch (e: Exception) {
                    Timber.e(e, "URL-ML inference failed for URL: ${com.sentinel.ai.core.utils.UrlLogger.redactUrl(payload.url)}")
                    mlFailure = e
                    null
                }

                val evidenceList = mutableListOf<ThreatEvidence>()
                evidenceList.addAll(heuristicEvidence)

                if (mlResult != null) {
                    val mlScore = (mlResult.probability * 100f).coerceIn(0f, 100f)
                    val severity = when {
                        mlScore >= 80f -> EvidenceSeverity.CRITICAL
                        mlScore >= 50f -> EvidenceSeverity.HIGH
                        mlScore >= 22.588723f -> EvidenceSeverity.MEDIUM
                        else -> EvidenceSeverity.LOW
                    }

                    evidenceList.add(
                        ThreatEvidence(
                            category = EvidenceCategory.URL_ML,
                            type = EvidenceType.URL_ML_SCORE,
                            severity = severity,
                            sourceName = "URL-ML Champion V7",
                            confidence = if (mlResult.isSafeBrandGated) 0.99f else 0.95f,
                            indicatorText = "${mlScore.toInt()}/100 (${mlResult.label})",
                            explanation = if (mlResult.isSafeBrandGated) {
                                "Verified safe brand domain"
                            } else {
                                "URL ML model predicted ${mlResult.label} (score: ${mlScore.toInt()}/100)"
                            },
                            metadata = mapOf(
                                "score" to mlScore.toString(),
                                "label" to mlResult.label,
                                "probability" to mlResult.probability.toString(),
                                "rawProbability" to mlResult.rawProbability.toString(),
                                "isSafeBrandGated" to mlResult.isSafeBrandGated.toString()
                            )
                        )
                    )
                }

                if (mlFailure != null) {
                    evidenceList.add(
                        ThreatEvidence(
                            category = EvidenceCategory.URL_ML,
                            type = EvidenceType.URL_ML_SCORE,
                            severity = EvidenceSeverity.CRITICAL,
                            sourceName = "URL-ML Champion V7",
                            confidence = 1.0f,
                            indicatorText = "URL ML unavailable",
                            explanation = "URL threat model could not be evaluated; blocking until local analysis is available",
                            metadata = mapOf("status" to "unavailable", "score" to "100.0")
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
                    id = UUID.randomUUID().toString(),
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
                    id = UUID.randomUUID().toString(),
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
