package com.sentinel.ai.core.ml.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import kotlin.math.abs

class MessageParityTest {

    companion object {
        private lateinit var scanner: MessageScanner
        private lateinit var records: List<GoldenMessageRecord>

        @JvmStatic
        @BeforeClass
        fun setUp() {
            val wordStream = MessageParityTest::class.java.classLoader?.getResourceAsStream("champion_v2_word_vocab_idf.json")
                ?: throw IllegalStateException("champion_v2_word_vocab_idf.json not found")
            val charStream = MessageParityTest::class.java.classLoader?.getResourceAsStream("champion_v2_char_vocab_idf.json")
                ?: throw IllegalStateException("champion_v2_char_vocab_idf.json not found")
            val scalerStream = MessageParityTest::class.java.classLoader?.getResourceAsStream("champion_v2_scaler.json")
                ?: throw IllegalStateException("champion_v2_scaler.json not found")
            val treesStream = MessageParityTest::class.java.classLoader?.getResourceAsStream("champion_v2_trees.json")
                ?: throw IllegalStateException("champion_v2_trees.json not found")

            scanner = MessageScanner.createFromStreams(wordStream, charStream, scalerStream, treesStream)

            val goldenStream = MessageParityTest::class.java.classLoader?.getResourceAsStream("golden_messages.json")
                ?: throw IllegalStateException("golden_messages.json not found")
            val goldenJson = goldenStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            records = parseGoldenMessageRecords(goldenJson)
            assertEquals(116, records.size)
        }

        private fun parseGoldenMessageRecords(json: String): List<GoldenMessageRecord> {
            val records = mutableListOf<GoldenMessageRecord>()
            var pos = 0
            while (pos < json.length) {
                val idMarker = "\"id\""
                val idIdx = json.indexOf(idMarker, pos)
                if (idIdx == -1) break

                fun unescapeJson(s: String): String {
                    val sb = StringBuilder(s.length)
                    var i = 0
                    while (i < s.length) {
                        val c = s[i]
                        if (c == '\\' && i + 1 < s.length) {
                            val next = s[i + 1]
                            when (next) {
                                'n' -> { sb.append('\n'); i += 2 }
                                'r' -> { sb.append('\r'); i += 2 }
                                't' -> { sb.append('\t'); i += 2 }
                                'b' -> { sb.append('\b'); i += 2 }
                                'f' -> { sb.append('\u000c'); i += 2 }
                                '"' -> { sb.append('"'); i += 2 }
                                '\\' -> { sb.append('\\'); i += 2 }
                                '/' -> { sb.append('/'); i += 2 }
                                'u' -> {
                                    if (i + 5 < s.length) {
                                        val hex = s.substring(i + 2, i + 6)
                                        val code = hex.toIntOrNull(16)
                                        if (code != null) {
                                            sb.append(code.toChar())
                                            i += 6
                                        } else {
                                            sb.append(c)
                                            i++
                                        }
                                    } else {
                                        sb.append(c)
                                        i++
                                    }
                                }
                                else -> { sb.append(c); i++ }
                            }
                        } else {
                            sb.append(c)
                            i++
                        }
                    }
                    return sb.toString()
                }

                fun getStrField(key: String, from: Int): String {
                    val marker = "\"$key\""
                    val idx = json.indexOf(marker, from)
                    if (idx == -1) return ""
                    val colonIdx = json.indexOf(':', idx)
                    if (colonIdx == -1) return ""
                    var st = colonIdx + 1
                    while (st < json.length && json[st].isWhitespace()) st++
                    if (json.startsWith("null", st)) return ""
                    if (st < json.length && json[st] == '"') {
                        val s = st + 1
                        var e = s
                        while (e < json.length && json[e] != '"') {
                            if (json[e] == '\\') e += 2 else e++
                        }
                        val v = json.substring(s, e)
                        return unescapeJson(v)
                    }
                    return ""
                }

                fun getNumField(key: String, from: Int): String {
                    val marker = "\"$key\""
                    val idx = json.indexOf(marker, from)
                    if (idx == -1) return ""
                    val colonIdx = json.indexOf(':', idx)
                    if (colonIdx == -1) return ""
                    var st = colonIdx + 1
                    while (st < json.length && json[st].isWhitespace()) st++
                    var e = st
                    while (e < json.length && json[e] != ',' && json[e] != '}' && json[e] != '\n') e++
                    return json.substring(st, e).trim()
                }

                val id = getStrField("id", idIdx)
                val text = getStrField("text", idIdx)
                val senderRaw = getStrField("sender", idIdx)
                val sender = if (senderRaw.isEmpty()) null else senderRaw
                val cat = getStrField("category", idIdx)
                val label = getStrField("label", idIdx)
                val predClass = getNumField("predicted_class", idIdx).toIntOrNull() ?: 0

                val detMarker = "\"deterministic_features\""
                val detIdx = json.indexOf(detMarker, idIdx)
                val detStart = json.indexOf('[', detIdx) + 1
                val detEnd = json.indexOf(']', detStart)
                val detTokens = json.substring(detStart, detEnd).split(',')
                val detFeatures = FloatArray(detTokens.size)
                for (i in 0 until detTokens.size) {
                    detFeatures[i] = detTokens[i].trim().toFloat()
                }

                val pMarker = "\"probabilities\""
                val pIdx = json.indexOf(pMarker, idIdx)
                val pStart = json.indexOf('[', pIdx) + 1
                val pEnd = json.indexOf(']', pStart)
                val pTokens = json.substring(pStart, pEnd).split(',')
                val probs = FloatArray(3)
                for (i in 0 until 3) {
                    probs[i] = pTokens[i].trim().toFloat()
                }

                records.add(GoldenMessageRecord(id, text, sender, cat, detFeatures, probs, predClass, label))
                pos = pEnd
            }
            return records
        }
    }

    data class GoldenMessageRecord(
        val id: String,
        val text: String,
        val sender: String?,
        val category: String,
        val detFeatures: FloatArray,
        val probabilities: FloatArray,
        val predictedClass: Int,
        val label: String
    )

    @Test
    fun testAll116GoldenRecordsLabelParity() {
        var labelMatches = 0
        var maxDetDiff = 0.0
        var maxProbDiff = 0.0
        val mismatches = mutableListOf<String>()

        for (rec in records) {
            val result = scanner.scan(rec.text, rec.sender)
            val ktDet = scanner.extractDeterministicFeatures(rec.text, rec.sender)

            for (i in 0 until 70) {
                val diff = abs(rec.detFeatures[i].toDouble() - ktDet[i])
                if (diff > maxDetDiff) maxDetDiff = diff
            }

            for (k in 0 until 3) {
                val diff = abs(rec.probabilities[k].toDouble() - result.probabilities[k].toDouble())
                if (diff > maxProbDiff) maxProbDiff = diff
            }

            if (rec.label == result.label && rec.predictedClass == result.classIndex) {
                labelMatches++
            } else {
                mismatches.add(
                    "${rec.id}: expected ${rec.label} (${rec.predictedClass}), got ${result.label} (${result.classIndex}), probs=${result.probabilities.contentToString()}"
                )
            }
        }

        assertTrue("Max deterministic feature diff should be within tolerance ($maxDetDiff)", maxDetDiff <= 0.045)
        assertTrue("Max probability diff should be within tolerance ($maxProbDiff)", maxProbDiff <= 0.03)
        assertTrue("Label mismatches found: $mismatches", mismatches.isEmpty())
        assertEquals("Label parity must be exactly 116/116", 116, labelMatches)
    }

    @Test
    fun testGoldenSample001BankingMessage() {
        val rec001 = records[0]
        val result = scanner.scan(rec001.text, rec001.sender)
        assertEquals("BENIGN", result.label)
        assertEquals(0, result.classIndex)
        assertTrue(!result.isNonBenign)
    }

    @Test
    fun testSuspiciousPowerCutScam() {
        val text = "Dear customer your electricity power will be disconnected tonight at 9:30 PM because your previous bill was not updated. Please immediately contact our electricity officer at 9876543210."
        val result = scanner.scan(text, "+919876543210")
        assertTrue(result.isNonBenign)
        assertTrue(result.label == "MALICIOUS" || result.label == "SUSPICIOUS_SPAM")
        assertTrue(result.pNonBenign >= 0.704f)
    }

    @Test
    fun testKycPanPhishingMessage() {
        val text = "Dear user, your SBI YONO account has been suspended due to pending KYC. Click http://sbi-kyc-update.xyz to update PAN immediately."
        val result = scanner.scan(text, "SBI-UPDATE")
        assertEquals("MALICIOUS", result.label)
        assertEquals(2, result.classIndex)
        assertTrue(result.isNonBenign)
    }
}
