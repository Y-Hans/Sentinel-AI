package com.sentinel.ai.core.ml.url

/**
 * Safe-Domain Gated Adjudication for URL-ML V7.
 * Calibrated threshold: tau = 0.22588723.
 */
class SafeDomainAdjudicator(
    val decisionThreshold: Float = 0.22588723f,
    val confBound: Float = 0.80f
) {
    // Feature indices in the 67-feature vector
    val idxSafeBrand = 60
    val idxBrandImp = 0
    val idxSuspTld = 63
    val idxIsIp = 14
    val idxSuspExt = 28
    val idxRedirect = 38
    val idxPathWords = 29
    val idxQueryWords = 39
    val idxDoubleEnc = 23
    val idxAt = 2
    val idxSubdomain = 18
    val idxPuny = 11
    val idxPort = 5
    val idxSlashes = 20
    val idxNonAscii = 4
    val idxNestedUrl = 3

    fun adjudicate(features: FloatArray, rawProba: Float): UrlScanResult {
        val hasThreat = (
            features[idxBrandImp] > 0.0f ||
            features[idxSuspTld] > 0.0f ||
            features[idxIsIp] > 0.0f ||
            features[idxSuspExt] > 0.0f ||
            features[idxRedirect] > 0.0f ||
            features[idxPathWords] > 0.0f ||
            features[idxQueryWords] > 0.0f ||
            features[idxDoubleEnc] > 0.0f ||
            features[idxAt] > 0.0f ||
            features[idxPuny] > 0.0f ||
            features[idxPort] > 0.0f ||
            features[idxSlashes] > 0.0f ||
            features[idxNonAscii] > 0.0f ||
            features[idxNestedUrl] > 0.0f ||
            features[idxSubdomain] >= 2.0f
        )

        val isSafeBrand = (features[idxSafeBrand] == 1.0f) && (!hasThreat)
        var pFinal = rawProba
        val isGated = isSafeBrand && (rawProba < confBound)
        if (isGated) {
            pFinal = 0.001f
        }

        val isMalicious = pFinal >= decisionThreshold
        val label = if (isMalicious) "MALICIOUS" else "BENIGN"

        return UrlScanResult(
            label = label,
            probability = pFinal,
            isMalicious = isMalicious,
            rawProbability = rawProba,
            isSafeBrandGated = isGated,
            features = features
        )
    }
}
