package com.sentinel.ai.core.ml.messages

/**
 * Standard Scaler for 2,070-dimensional Messages vector.
 * Standardization: z_i = (x_i - mean_i) / scale_i in 64-bit IEEE double precision.
 */
class FeatureScaler(
    val nFeatures: Int,
    val mean: DoubleArray,
    val scale: DoubleArray
) {
    fun transform(features: DoubleArray, outScaled: DoubleArray) {
        val n = features.size
        for (i in 0 until n) {
            val s = scale[i]
            if (s != 0.0) {
                outScaled[i] = (features[i] - mean[i]) / s
            } else {
                outScaled[i] = features[i] - mean[i]
            }
        }
    }

    fun transform(features: DoubleArray): DoubleArray {
        val out = DoubleArray(features.size)
        transform(features, out)
        return out
    }
}
