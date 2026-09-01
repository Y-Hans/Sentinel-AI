package com.sentinel.ai.core.ml.messages

import kotlin.math.exp

/**
 * Multi-Class Tree Inference Engine for HistGradientBoosting models.
 * Evaluates 309 decision trees across 103 iterations for 3 classes with softmax.
 */
class MultiClassTreeEvaluator(
    val nClasses: Int,
    val nIterations: Int,
    val baselinePrediction: DoubleArray,
    val binThresholds: Array<DoubleArray>,
    val trees: Array<Array<Array<Node>>>
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

    fun binFeatures(features: DoubleArray, outBins: IntArray) {
        val n = features.size
        for (j in 0 until n) {
            val thresholds = binThresholds[j]
            val v = features[j]
            if (thresholds.isEmpty()) {
                outBins[j] = 0
                continue
            }
            var left = 0
            var right = thresholds.size
            while (left < right) {
                val mid = (left + right) / 2
                if (v <= thresholds[mid]) {
                    right = mid
                } else {
                    left = mid + 1
                }
            }
            outBins[j] = left
        }
    }

    fun predictRawMargins(features: DoubleArray): DoubleArray {
        val binned = IntArray(features.size)
        binFeatures(features, binned)

        val scores = DoubleArray(nClasses)
        for (k in 0 until nClasses) {
            scores[k] = baselinePrediction[k]
        }

        for (iterIdx in 0 until nIterations) {
            val iterTrees = trees[iterIdx]
            for (k in 0 until nClasses) {
                val treeNodes = iterTrees[k]
                var nodeIdx = 0
                while (true) {
                    val node = treeNodes[nodeIdx]
                    if (node.isLeaf) {
                        scores[k] += node.value
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
        }
        return scores
    }

    fun predictProba(features: DoubleArray): FloatArray {
        val margins = predictRawMargins(features)
        var maxM = margins[0]
        for (k in 1 until nClasses) {
            if (margins[k] > maxM) maxM = margins[k]
        }

        var sumExp = 0.0
        val expMargins = DoubleArray(nClasses)
        for (k in 0 until nClasses) {
            val e = exp(margins[k] - maxM)
            expMargins[k] = e
            sumExp += e
        }

        val probs = FloatArray(nClasses)
        for (k in 0 until nClasses) {
            probs[k] = (expMargins[k] / sumExp).toFloat()
        }
        return probs
    }
}
