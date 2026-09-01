package com.sentinel.ai.core.ml.messages

data class MessageScanResult(
    val label: String,
    val classIndex: Int,
    val probabilities: FloatArray,
    val isNonBenign: Boolean,
    val pNonBenign: Float
) {
    val isThreat: Boolean get() = isNonBenign
    val predictedClass: Int get() = classIndex
    val nonBenignProbability: Double get() = pNonBenign.toDouble()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MessageScanResult
        return label == other.label &&
            classIndex == other.classIndex &&
            isNonBenign == other.isNonBenign &&
            pNonBenign == other.pNonBenign &&
            probabilities.contentEquals(other.probabilities)
    }

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = 31 * result + classIndex
        result = 31 * result + isNonBenign.hashCode()
        result = 31 * result + pNonBenign.hashCode()
        result = 31 * result + probabilities.contentHashCode()
        return result
    }
}

/**
 * Adjudication logic for Messages-ML Champion V2.
 * Applies threshold tau = 0.704 on P(SUSPICIOUS_SPAM) + P(MALICIOUS).
 */
class MessageAdjudicator(
    val threshold: Float = 0.704f
) {
    fun adjudicate(probabilities: FloatArray): MessageScanResult {
        val pSusp = probabilities[1]
        val pMal = probabilities[2]
        val pNonBenign = pSusp + pMal

        val label: String
        val classIndex: Int
        val isNonBenign: Boolean

        if (pNonBenign >= threshold) {
            isNonBenign = true
            if (pSusp > pMal) {
                label = "SUSPICIOUS_SPAM"
                classIndex = 1
            } else {
                label = "MALICIOUS"
                classIndex = 2
            }
        } else {
            isNonBenign = false
            label = "BENIGN"
            classIndex = 0
        }

        return MessageScanResult(
            label = label,
            classIndex = classIndex,
            probabilities = probabilities,
            isNonBenign = isNonBenign,
            pNonBenign = pNonBenign
        )
    }
}
