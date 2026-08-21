package com.sentinel.ai.ml

interface MLInferenceEngine {
    fun predict(features: FloatArray): Float
}
