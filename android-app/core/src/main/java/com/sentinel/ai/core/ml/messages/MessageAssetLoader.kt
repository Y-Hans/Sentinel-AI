package com.sentinel.ai.core.ml.messages

data class WordTfidfData(
    val vocabulary: Map<String, Int>,
    val idf: DoubleArray,
    val stopWords: Set<String>
)

data class CharTfidfData(
    val vocabulary: Map<String, Int>,
    val idf: DoubleArray
)

data class ScalerData(
    val mean: DoubleArray,
    val scale: DoubleArray
)

data class TreesData(
    val baselinePrediction: DoubleArray,
    val binThresholds: Array<DoubleArray>,
    val trees: Array<Array<Array<MultiClassTreeEvaluator.Node>>>,
    val nClasses: Int,
    val nIterations: Int
)

/**
 * Fast streaming JSON parser for Messages-ML asset files.
 */
object MessageAssetLoader {

    fun loadWordTfidf(json: String): WordTfidfData {
        // Stop words
        val stopWords = mutableSetOf<String>()
        val stopMarker = "\"stop_words\":["
        val sIdx = json.indexOf(stopMarker)
        if (sIdx != -1) {
            val start = sIdx + stopMarker.length
            var end = start
            while (end < json.length && json[end] != ']') end++
            val content = json.substring(start, end)
            for (token in content.split(',')) {
                val clean = token.trim().trim('"')
                if (clean.isNotEmpty()) stopWords.add(clean)
            }
        }

        // IDF array
        val idf = parseFlatDoubleArray(json, "idf")

        // Vocabulary dictionary: "vocabulary":{"word":0,"word2":1}
        val vocab = mutableMapOf<String, Int>()
        val vMarker = "\"vocabulary\":{"
        val vIdx = json.indexOf(vMarker)
        if (vIdx != -1) {
            var pos = vIdx + vMarker.length
            while (pos < json.length && json[pos] != '}') {
                if (json[pos] == '"') {
                    pos++
                    val kStart = pos
                    while (pos < json.length && json[pos] != '"') pos++
                    val key = json.substring(kStart, pos)
                    pos++ // skip closing '"'
                    while (pos < json.length && json[pos] != ':') pos++
                    pos++ // skip ':'
                    val vStart = pos
                    while (pos < json.length && (json[pos].isDigit() || json[pos] == '-')) pos++
                    val value = json.substring(vStart, pos).trim().toInt()
                    vocab[key] = value
                } else {
                    pos++
                }
            }
        }

        return WordTfidfData(vocab, idf, stopWords)
    }

    fun loadCharTfidf(json: String): CharTfidfData {
        val idf = parseFlatDoubleArray(json, "idf")
        val vocab = mutableMapOf<String, Int>()
        val vMarker = "\"vocabulary\":{"
        val vIdx = json.indexOf(vMarker)
        if (vIdx != -1) {
            var pos = vIdx + vMarker.length
            while (pos < json.length && json[pos] != '}') {
                if (json[pos] == '"') {
                    pos++
                    val kStart = pos
                    while (pos < json.length && json[pos] != '"') {
                        if (json[pos] == '\\' && pos + 1 < json.length) {
                            pos += 2
                        } else {
                            pos++
                        }
                    }
                    var key = json.substring(kStart, pos)
                    key = key.replace("\\\\", "\\").replace("\\\"", "\"")
                    pos++ // skip closing '"'

                    while (pos < json.length && json[pos] != ':') pos++
                    pos++ // skip ':'
                    val vStart = pos
                    while (pos < json.length && (json[pos].isDigit() || json[pos] == '-')) pos++
                    val value = json.substring(vStart, pos).trim().toInt()
                    vocab[key] = value
                } else {
                    pos++
                }
            }
        }
        return CharTfidfData(vocab, idf)
    }

    fun loadScaler(json: String): ScalerData {
        val mean = parseFlatDoubleArray(json, "mean")
        val scale = parseFlatDoubleArray(json, "scale")
        return ScalerData(mean, scale)
    }

    fun loadTrees(json: String): TreesData {
        val nClasses = 3

        // Baseline prediction
        val bpMarker = "\"baseline_prediction\":"
        val bpIdx = json.indexOf(bpMarker)
        val bpStart = json.indexOf('[', bpIdx)
        val bpInnerStart = json.indexOf('[', bpStart + 1)
        val bpInnerEnd = json.indexOf(']', bpInnerStart)
        val bpTokens = json.substring(bpInnerStart + 1, bpInnerEnd).split(',')
        val baselinePrediction = DoubleArray(3)
        for (k in 0 until 3) {
            baselinePrediction[k] = bpTokens[k].trim().toDouble()
        }

        // Bin thresholds 2D
        val binThresholds = parse2DDoubleArray(json, "bin_thresholds")

        // Trees 4D in JSON: [ [ [node], [node] ], ... ]
        val allIters = mutableListOf<Array<Array<MultiClassTreeEvaluator.Node>>>()
        val tMarker = "\"trees\":["
        val tIdx = json.indexOf(tMarker)
        var pos = tIdx + tMarker.length

        while (pos < json.length && json[pos] != ']') {
            if (json[pos] == '[') {
                // Start of an iteration
                pos++
                val iterClasses = mutableListOf<Array<MultiClassTreeEvaluator.Node>>()
                while (pos < json.length && json[pos] != ']') {
                    if (json[pos] == '[') {
                        // Start of a class tree
                        pos++
                        val classNodes = mutableListOf<MultiClassTreeEvaluator.Node>()
                        while (pos < json.length && json[pos] != ']') {
                            if (json[pos] == '[') {
                                // Start of node: [feature_idx, bin_threshold, left, right, value, is_leaf, missing_go_to_left]
                                pos++
                                val nStart = pos
                                while (pos < json.length && json[pos] != ']') pos++
                                val nodeStr = json.substring(nStart, pos)
                                val p = nodeStr.split(',')
                                classNodes.add(
                                    MultiClassTreeEvaluator.Node(
                                        featureIdx = p[0].trim().toInt(),
                                        binThreshold = p[1].trim().toInt(),
                                        left = p[2].trim().toInt(),
                                        right = p[3].trim().toInt(),
                                        value = p[4].trim().toDouble(),
                                        isLeaf = p[5].trim().toInt() == 1,
                                        missingGoToLeft = p[6].trim().toInt() == 1
                                    )
                                )
                                pos++ // skip ']' of node
                            } else {
                                pos++
                            }
                        }
                        iterClasses.add(classNodes.toTypedArray())
                        pos++ // skip ']' of class tree
                    } else {
                        pos++
                    }
                }
                allIters.add(iterClasses.toTypedArray())
                pos++ // skip ']' of iteration
            } else {
                pos++
            }
        }

        return TreesData(
            baselinePrediction = baselinePrediction,
            binThresholds = binThresholds,
            trees = allIters.toTypedArray(),
            nClasses = nClasses,
            nIterations = allIters.size
        )
    }

    private fun parseFlatDoubleArray(json: String, key: String): DoubleArray {
        val marker = "\"${key}\":["
        val idx = json.indexOf(marker)
        if (idx == -1) throw IllegalArgumentException("Key not found: $key")
        val start = idx + marker.length
        var end = start
        while (end < json.length && json[end] != ']') end++
        val tokens = json.substring(start, end).split(',')
        val arr = DoubleArray(tokens.size)
        for (i in 0 until tokens.size) {
            arr[i] = tokens[i].trim().toDouble()
        }
        return arr
    }

    private fun parse2DDoubleArray(json: String, key: String): Array<DoubleArray> {
        val marker = "\"${key}\":["
        val idx = json.indexOf(marker)
        if (idx == -1) throw IllegalArgumentException("Key not found: $key")
        var pos = idx + marker.length

        val result = mutableListOf<DoubleArray>()
        while (pos < json.length && json[pos] != ']') {
            if (json[pos] == '[') {
                pos++
                val innerStart = pos
                while (pos < json.length && json[pos] != ']') pos++
                val innerStr = json.substring(innerStart, pos).trim()
                if (innerStr.isEmpty()) {
                    result.add(DoubleArray(0))
                } else {
                    val tokens = innerStr.split(',')
                    val arr = DoubleArray(tokens.size)
                    for (t in 0 until tokens.size) {
                        arr[t] = tokens[t].trim().toDouble()
                    }
                    result.add(arr)
                }
                pos++
            } else {
                pos++
            }
        }
        return result.toTypedArray()
    }
}
