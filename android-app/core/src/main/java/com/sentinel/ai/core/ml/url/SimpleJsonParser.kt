package com.sentinel.ai.core.ml.url

/**
 * Lightweight, zero-dependency streaming JSON parser tailored for URL-ML model bundles.
 * Zero external ML or parsing runtime dependencies.
 */
class SimpleJsonParser(val json: String) {

    fun getInt(key: String): Int {
        val marker = "\"$key\":"
        val idx = json.indexOf(marker)
        if (idx == -1) throw IllegalArgumentException("Key not found: $key")
        val start = idx + marker.length
        var end = start
        while (end < json.length && (json[end].isDigit() || json[end] == '-')) end++
        return json.substring(start, end).trim().toInt()
    }

    fun getDouble(key: String): Double {
        val marker = "\"$key\":"
        val idx = json.indexOf(marker)
        if (idx == -1) throw IllegalArgumentException("Key not found: $key")
        val start = idx + marker.length
        var end = start
        while (end < json.length && (json[end].isDigit() || json[end] == '-' || json[end] == '.' || json[end] == 'e' || json[end] == 'E' || json[end] == '+')) end++
        return json.substring(start, end).trim().toDouble()
    }

    fun getFloatArray2D(key: String): Array<FloatArray> {
        val marker = "\"$key\":["
        val idx = json.indexOf(marker)
        if (idx == -1) throw IllegalArgumentException("Key not found: $key")
        var pos = idx + marker.length

        val result = mutableListOf<FloatArray>()
        while (pos < json.length && json[pos] != ']') {
            if (json[pos] == '[') {
                pos++
                val innerStart = pos
                while (pos < json.length && json[pos] != ']') pos++
                val innerStr = json.substring(innerStart, pos).trim()
                if (innerStr.isEmpty()) {
                    result.add(FloatArray(0))
                } else {
                    val tokens = innerStr.split(',')
                    val arr = FloatArray(tokens.size)
                    for (t in 0 until tokens.size) {
                        arr[t] = tokens[t].trim().toFloat()
                    }
                    result.add(arr)
                }
                pos++ // skip ']'
            } else {
                pos++
            }
        }
        return result.toTypedArray()
    }

    fun getTrees3D(key: String): Array<Array<HistGbmTreeEvaluator.Node>> {
        val marker = "\"$key\":["
        val idx = json.indexOf(marker)
        if (idx == -1) throw IllegalArgumentException("Key not found: $key")
        var pos = idx + marker.length

        val allTrees = mutableListOf<Array<HistGbmTreeEvaluator.Node>>()

        while (pos < json.length && json[pos] != ']') {
            if (json[pos] == '[') {
                // Start of a tree
                pos++
                val treeNodes = mutableListOf<HistGbmTreeEvaluator.Node>()
                while (pos < json.length && json[pos] != ']') {
                    if (json[pos] == '[') {
                        // Start of a node: [feature_idx, bin_threshold, left, right, value, is_leaf, missing_go_to_left]
                        pos++
                        val nodeStart = pos
                        while (pos < json.length && json[pos] != ']') pos++
                        val nodeStr = json.substring(nodeStart, pos)
                        val parts = nodeStr.split(',')
                        val f = parts[0].trim().toInt()
                        val th = parts[1].trim().toInt()
                        val l = parts[2].trim().toInt()
                        val r = parts[3].trim().toInt()
                        val v = parts[4].trim().toDouble()
                        val leaf = parts[5].trim().toInt() == 1
                        val ml = parts[6].trim().toInt() == 1

                        treeNodes.add(
                            HistGbmTreeEvaluator.Node(
                                featureIdx = f,
                                binThreshold = th,
                                left = l,
                                right = r,
                                value = v,
                                isLeaf = leaf,
                                missingGoToLeft = ml
                            )
                        )
                        pos++ // skip ']'
                    } else {
                        pos++
                    }
                }
                allTrees.add(treeNodes.toTypedArray())
                pos++ // skip ']' of tree
            } else {
                pos++
            }
        }
        return allTrees.toTypedArray()
    }
}
