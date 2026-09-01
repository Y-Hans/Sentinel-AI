package com.sentinel.ai.core.ml.url

import java.io.InputStream
import java.nio.charset.StandardCharsets

data class UrlScanResult(
    val label: String,
    val probability: Float,
    val isMalicious: Boolean,
    val rawProbability: Float,
    val isSafeBrandGated: Boolean,
    val features: FloatArray = FloatArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UrlScanResult
        return label == other.label &&
            probability == other.probability &&
            isMalicious == other.isMalicious &&
            rawProbability == other.rawProbability &&
            isSafeBrandGated == other.isSafeBrandGated &&
            features.contentEquals(other.features)
    }

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = 31 * result + probability.hashCode()
        result = 31 * result + isMalicious.hashCode()
        result = 31 * result + rawProbability.hashCode()
        result = 31 * result + isSafeBrandGated.hashCode()
        result = 31 * result + features.contentHashCode()
        return result
    }
}

/**
 * Production Android / JVM URL Scanner (Sentinel-ML V7 Champion).
 * Fully self-contained with zero external ML dependencies.
 */
open class UrlScanner(
    private val evaluator: HistGbmTreeEvaluator,
    private val adjudicator: SafeDomainAdjudicator
) {
    open fun scan(url: String): UrlScanResult {
        val features = UrlFeatureExtractor.extractFeatures(url)
        val rawProba = evaluator.predictProba(features)
        return adjudicator.adjudicate(features, rawProba)
    }

    fun extractFeatures(url: String): FloatArray {
        return UrlFeatureExtractor.extractFeatures(url)
    }

    companion object {
        fun fromJson(jsonStr: String): UrlScanner {
            val parser = SimpleJsonParser(jsonStr)
            val nTrees = parser.getInt("n_trees")
            val baseScore = parser.getDouble("base_score")
            val decisionThreshold = parser.getDouble("decision_threshold").toFloat()
            val confBound = parser.getDouble("conf_bound").toFloat()

            val binThreshArray = parser.getFloatArray2D("bin_thresholds")
            val rawTrees = parser.getTrees3D("trees")

            val evaluator = HistGbmTreeEvaluator(
                nTrees = nTrees,
                baseScore = baseScore,
                binThresholds = binThreshArray,
                trees = rawTrees
            )
            val adjudicator = SafeDomainAdjudicator(
                decisionThreshold = decisionThreshold,
                confBound = confBound
            )
            return UrlScanner(evaluator, adjudicator)
        }

        fun fromInputStream(stream: InputStream): UrlScanner {
            val text = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            return fromJson(text)
        }
    }
}
