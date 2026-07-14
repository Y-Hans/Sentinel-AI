package com.sentinel.ai.ml

import android.content.Context
import org.json.JSONObject

class Scaler(context: Context) {

    private val means: FloatArray
    private val scales: FloatArray

    init {
        val json = context.assets.open("scaler.json")
            .bufferedReader()
            .use { it.readText() }

        val obj = JSONObject(json)

        val meanArray = obj.getJSONArray("mean")
        val scaleArray = obj.getJSONArray("scale")
        val featureNameArray = obj.getJSONArray("feature_names")

        means = FloatArray(meanArray.length()) { i ->
            meanArray.getDouble(i).toFloat()
        }

        scales = FloatArray(scaleArray.length()) { i ->
            scaleArray.getDouble(i).toFloat()
        }
        val featureNames = List(featureNameArray.length()) { i ->
            featureNameArray.getString(i)
        }

        require(featureNames == FeatureExtractor.FEATURE_NAMES) {
            "Scaler feature order does not match the model feature order"
        }
        require(means.size == FeatureExtractor.FEATURE_COUNT) {
            "Scaler mean count ${means.size} does not match ${FeatureExtractor.FEATURE_COUNT} model features"
        }
        require(scales.size == FeatureExtractor.FEATURE_COUNT) {
            "Scaler scale count ${scales.size} does not match ${FeatureExtractor.FEATURE_COUNT} model features"
        }
        require(means.all(Float::isFinite)) {
            "Scaler means must be finite"
        }
        require(scales.all { it.isFinite() && it != 0f }) {
            "Scaler values must be finite and non-zero"
        }
    }

    fun transform(input: FloatArray): FloatArray {
        require(input.size == means.size) {
            "Expected ${means.size} input features, received ${input.size}"
        }
        require(input.all(Float::isFinite)) { "Input features must be finite" }

        return FloatArray(input.size) { i ->
            (input[i] - means[i]) / scales[i]
        }.also { output ->
            require(output.all(Float::isFinite)) { "Scaled features must be finite" }
        }
    }
}
