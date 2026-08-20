package com.sentinel.ai.protection.intent

import android.content.Context
import com.sentinel.ai.core.event.ThreatEvent
import com.sentinel.ai.core.event.ThreatEventBus
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.ml.FeatureExtractor
import com.sentinel.ai.ml.MLInferenceManager
import com.sentinel.ai.protection.intent.file.FileScanner
import com.sentinel.ai.protection.intent.link.LinkScanner
import com.sentinel.ai.protection.intent.model.FilePayload
import com.sentinel.ai.protection.intent.model.IntentPayload
import com.sentinel.ai.protection.intent.model.UrlPayload
import com.sentinel.ai.protection.intent.reputation.ReputationManager
import com.sentinel.ai.protection.intent.reputation.ReputationTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentThreatAnalyzerImpl @Inject constructor(
    private val linkScanner: LinkScanner,
    private val fileScanner: FileScanner,
    private val threatEventBus: ThreatEventBus,
    private val reputationManager: ReputationManager,
    @ApplicationContext private val context: Context
) : IntentThreatAnalyzer {

    private val mlInferenceManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MLInferenceManager(context)
    }

    override suspend fun analyze(payload: IntentPayload): ScanResult {
        return when (payload) {
            is UrlPayload -> {
                val heuristicResult = linkScanner.scan(payload.url)
                val mlScore = try {
                    val features = FeatureExtractor.extract(payload.url)
                    if (features.size == FeatureExtractor.FEATURE_COUNT) {
                        (mlInferenceManager.predict(features) * 100f).coerceIn(0f, 100f)
                    } else null
                } catch (e: Exception) {
                    null
                }

                val finalResult = reputationManager.enrich(
                    heuristicResult = heuristicResult,
                    mlScore = mlScore,
                    target = ReputationTarget.Url(payload.url)
                )
                threatEventBus.emit(ThreatEvent.LinkThreatDetected(finalResult))
                finalResult
            }
            is FilePayload -> {
                val heuristicResult = fileScanner.scan(payload.uri)
                val finalResult = reputationManager.enrich(
                    heuristicResult = heuristicResult,
                    mlScore = null,
                    target = null
                )
                threatEventBus.emit(ThreatEvent.FileThreatDetected(finalResult))
                finalResult
            }
        }
    }
}
