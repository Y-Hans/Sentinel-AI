package com.sentinel.ai.protection.intent

import android.content.Context
import android.net.Uri
import android.util.Log
import com.sentinel.ai.core.data.ScanRepository
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.ml.FeatureExtractor
import com.sentinel.ai.ml.MLInferenceManager
import com.sentinel.ai.protection.intent.link.UrlNormalizer
import com.sentinel.ai.protection.intent.model.FilePayload
import com.sentinel.ai.protection.intent.model.UrlPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Adapts the existing intent analyzer and applies URL-only ML scoring for every scan entry point. */
@Singleton
class IntentScanRepository @Inject constructor(
    private val analyzer: IntentThreatAnalyzer,
    @ApplicationContext private val context: Context
) : ScanRepository {
    private val mlInferenceManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MLInferenceManager(context)
    }

    override suspend fun scanLink(link: String): ScanResult {
        val payload = UrlPayload(link)
        val parsedUrl = UrlNormalizer.parse(link)
        Log.d(TAG, "payload.javaClass.name=${payload.javaClass.name}")
        Log.d(TAG, "payload.toString()=$payload")
        Log.d(
            TAG,
            "URL input=${payload.url}, scheme=${parsedUrl.scheme}, valid=${parsedUrl.isValid}"
        )

        val baseResult = analyzer.analyze(payload)
        return applyMlScore(baseResult, payload.url)
    }

    override suspend fun scanFile(uri: Uri): ScanResult = analyzer.analyze(FilePayload(uri))

    private fun applyMlScore(baseResult: ScanResult, url: String): ScanResult {
        return try {
            val features = FeatureExtractor.extract(url)
            if (features.size != FeatureExtractor.FEATURE_COUNT) {
                Log.e(TAG, "Invalid feature size: ${features.size}")
                return baseResult
            }

            val mlScore = (mlInferenceManager.predict(features) * 100f).coerceIn(0f, 100f)
            val boostedMlScore = (mlScore * 2f).coerceIn(0f, 100f)
            val combinedScore = (0.7f * baseResult.riskScore) +
                (0.3f * boostedMlScore)
            android.util.Log.d("ML_DEBUG", "Heuristic=${baseResult.riskScore}, ML=$mlScore, Combined=$combinedScore")
            baseResult.copy(riskScore = combinedScore)
        } catch (exception: Exception) {
            Log.e(TAG, "ML failed; using the rule-based result", exception)
            baseResult
        }
    }

    private companion object {
        const val TAG = "ML_DEBUG"
    }
}
