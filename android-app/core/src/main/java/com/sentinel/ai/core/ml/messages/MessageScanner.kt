package com.sentinel.ai.core.ml.messages

import java.io.InputStream
import java.nio.charset.StandardCharsets

open class MessageScanner(
    private val tfidfVectorizer: DualTfidfVectorizer,
    private val featureScaler: FeatureScaler,
    private val treeEvaluator: MultiClassTreeEvaluator,
    private val adjudicator: MessageAdjudicator
) {

    open fun scan(messageText: String, senderHeader: String? = null): MessageScanResult {
        // 1. Extract 70 tabular deterministic features
        val tabularFeatures = MessageFeatureExtractor.extractDeterministicFeatures(messageText, senderHeader)

        // 2. Extract 2,000 TF-IDF features (1,500 word + 500 char_wb)
        val wordTfidf = tfidfVectorizer.transformWord(messageText)
        val charTfidf = tfidfVectorizer.transformChar(messageText)

        // 3. Concatenate into full 2,070 feature vector
        val rawFeatures = DoubleArray(2070)
        for (i in 0 until 70) {
            rawFeatures[i] = tabularFeatures[i].toDouble()
        }
        for (i in 0 until 1500) {
            rawFeatures[70 + i] = wordTfidf[i]
        }
        for (i in 0 until 500) {
            rawFeatures[1570 + i] = charTfidf[i]
        }

        // 4. Scale all 2,070 features using standard scaler
        val scaledFeatures = featureScaler.transform(rawFeatures)

        // 5. Predict 3-class probabilities using HistGradientBoosting
        val probabilities = treeEvaluator.predictProba(scaledFeatures)

        // 6. Adjudicate decision based on calibrated non-benign threshold tau = 0.704
        return adjudicator.adjudicate(probabilities)
    }

    fun extractDeterministicFeatures(messageText: String, senderHeader: String? = null): DoubleArray {
        return MessageFeatureExtractor.extractDeterministicFeatures(messageText, senderHeader)
    }

    companion object {
        fun create(
            wordJson: String,
            charJson: String,
            scalerJson: String,
            treesJson: String,
            threshold: Float = 0.704f
        ): MessageScanner {
            val wordData = MessageAssetLoader.loadWordTfidf(wordJson)
            val charData = MessageAssetLoader.loadCharTfidf(charJson)
            val tfidf = DualTfidfVectorizer(
                wordVocab = wordData.vocabulary,
                wordIdf = wordData.idf,
                stopWords = wordData.stopWords,
                charVocab = charData.vocabulary,
                charIdf = charData.idf
            )
            val scalerData = MessageAssetLoader.loadScaler(scalerJson)
            val scaler = FeatureScaler(
                nFeatures = scalerData.mean.size,
                mean = scalerData.mean,
                scale = scalerData.scale
            )
            val treesData = MessageAssetLoader.loadTrees(treesJson)
            val evaluator = MultiClassTreeEvaluator(
                nClasses = treesData.nClasses,
                nIterations = treesData.nIterations,
                baselinePrediction = treesData.baselinePrediction,
                binThresholds = treesData.binThresholds,
                trees = treesData.trees
            )
            val adjudicator = MessageAdjudicator(threshold)

            return MessageScanner(tfidf, scaler, evaluator, adjudicator)
        }

        fun createFromStreams(
            wordStream: InputStream,
            charStream: InputStream,
            scalerStream: InputStream,
            treesStream: InputStream,
            threshold: Float = 0.704f
        ): MessageScanner {
            val wordJson = wordStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val charJson = charStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val scalerJson = scalerStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val treesJson = treesStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            return create(wordJson, charJson, scalerJson, treesJson, threshold)
        }

        fun createNoOp(): MessageScanner {
            val emptyTfidf = DualTfidfVectorizer(emptyMap(), DoubleArray(0), emptySet(), emptyMap(), DoubleArray(0))
            val emptyScaler = FeatureScaler(2070, DoubleArray(2070), DoubleArray(2070) { 1.0 })
            val emptyEvaluator = MultiClassTreeEvaluator(3, 0, doubleArrayOf(10.0, -10.0, -10.0), emptyArray(), emptyArray())
            val adjudicator = MessageAdjudicator(0.704f)
            return MessageScanner(emptyTfidf, emptyScaler, emptyEvaluator, adjudicator)
        }
    }
}
