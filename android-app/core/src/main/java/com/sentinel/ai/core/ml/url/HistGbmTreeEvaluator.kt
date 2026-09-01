package com.sentinel.ai.core.ml.url

import java.util.Arrays
import kotlin.math.exp

/**
 * Portable Native Tree Traversal Engine for HistGradientBoosting models (URL-ML V7).
 * Evaluates 350 binary decision trees with left-sided quantile binning.
 */
class HistGbmTreeEvaluator(
    val nTrees: Int,
    val baseScore: Double,
    val binThresholds: Array<FloatArray>,
    val trees: Array<Array<Node>>
) {
    data class Node(
        val featureIdx: Int,
        val binThreshold: Int,
        val left: Int,
        val right: Int,
        val value: Double,
        val isLeaf: Boolean,
        val missingGoToLeft: Boolean
    )

    /**
     * Map continuous features to discrete uint8 bins via searchsorted (side='left').
     */
    fun binFeatures(features: FloatArray, outBins: IntArray) {
        val n = features.size
        for (j in 0 until n) {
            val thresholds = binThresholds[j]
            val v = features[j]
            if (thresholds.isEmpty()) {
                outBins[j] = 0
                continue
            }
            var idx = Arrays.binarySearch(thresholds, v)
            if (idx >= 0) {
                while (idx > 0 && thresholds[idx - 1] == v) {
                    idx--
                }
                outBins[j] = idx
            } else {
                outBins[j] = -idx - 1
            }
        }
    }

    /**
     * Predict raw margin score across all trees.
     */
    fun predictRawMargin(features: FloatArray): Double {
        val binned = IntArray(features.size)
        binFeatures(features, binned)

        var margin = baseScore
        for (treeIdx in 0 until nTrees) {
            val treeNodes = trees[treeIdx]
            var nodeIdx = 0
            while (true) {
                val node = treeNodes[nodeIdx]
                if (node.isLeaf) {
                    margin += node.value
                    break
                }
                val binVal = binned[node.featureIdx]
                if (binVal <= node.binThreshold) {
                    nodeIdx = node.left
                } else {
                    nodeIdx = node.right
                }
            }
        }
        return margin
    }

    /**
     * Predict calibrated probability via logistic sigmoid.
     */
    fun predictProba(features: FloatArray): Float {
        val margin = predictRawMargin(features)
        val p = 1.0 / (1.0 + exp(-margin))
        return p.toFloat()
    }
}
