package com.sentinel.ai.core.ml.messages

import java.util.regex.Pattern
import kotlin.math.sqrt

/**
 * Dual TF-IDF Vectorizer (Word 1-2 ngrams + Char_wb 3-5 ngrams).
 * Zero scikit-learn dependency, exact mathematical equivalence with L2 normalization.
 */
class DualTfidfVectorizer(
    val wordVocab: Map<String, Int>,
    val wordIdf: DoubleArray,
    val stopWords: Set<String>,
    val charVocab: Map<String, Int>,
    val charIdf: DoubleArray
) {
    // Unicode word token regex matching scikit-learn r"(?u)\b\w\w+\b"
    private val reWordToken = Pattern.compile("\\b\\w\\w+\\b", Pattern.UNICODE_CHARACTER_CLASS)

    fun transformWord(text: String): DoubleArray {
        val lower = text.lowercase()
        val matcher = reWordToken.matcher(lower)
        val tokens = mutableListOf<String>()
        while (matcher.find()) {
            tokens.add(matcher.group())
        }

        // Filter stop words for unigram components in scikit-learn
        val filtered = tokens.filter { it !in stopWords }

        // Counts of unigrams and bigrams
        val counts = mutableMapOf<String, Int>()
        val fSize = filtered.size

        // Unigrams (n=1)
        for (i in 0 until fSize) {
            val unigram = filtered[i]
            counts[unigram] = (counts[unigram] ?: 0) + 1
        }

        // Bigrams (n=2)
        for (i in 0 until fSize - 1) {
            val bigram = filtered[i] + " " + filtered[i + 1]
            counts[bigram] = (counts[bigram] ?: 0) + 1
        }

        val vec = DoubleArray(1500)
        var sumSq = 0.0
        for ((token, count) in counts) {
            val idx = wordVocab[token]
            if (idx != null && idx in 0 until 1500) {
                val weighted = count.toDouble() * wordIdf[idx]
                vec[idx] = weighted
                sumSq += weighted * weighted
            }
        }

        // L2 normalization
        val norm = sqrt(sumSq)
        if (norm > 1e-12) {
            val inv = 1.0 / norm
            for (i in 0 until 1500) {
                vec[i] *= inv
            }
        }
        return vec
    }

    fun transformChar(text: String): DoubleArray {
        val lower = text.lowercase()
        val words = lower.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val counts = mutableMapOf<String, Int>()

        for (w in words) {
            val padded = " $w "
            val pLen = padded.length
            for (n in 3..5) {
                if (pLen >= n) {
                    for (i in 0..pLen - n) {
                        val ngram = padded.substring(i, i + n)
                        counts[ngram] = (counts[ngram] ?: 0) + 1
                    }
                }
            }
        }

        val vec = DoubleArray(500)
        var sumSq = 0.0
        for ((ngram, count) in counts) {
            val idx = charVocab[ngram]
            if (idx != null && idx in 0 until 500) {
                val weighted = count.toDouble() * charIdf[idx]
                vec[idx] = weighted
                sumSq += weighted * weighted
            }
        }

        // L2 normalization
        val norm = sqrt(sumSq)
        if (norm > 1e-12) {
            val inv = 1.0 / norm
            for (i in 0 until 500) {
                vec[i] *= inv
            }
        }
        return vec
    }
}
