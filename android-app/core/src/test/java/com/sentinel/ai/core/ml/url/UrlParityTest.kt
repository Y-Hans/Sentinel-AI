package com.sentinel.ai.core.ml.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.InputStream
import kotlin.math.abs

class UrlParityTest {

    companion object {
        private lateinit var scanner: UrlScanner
        private lateinit var records: List<GoldenUrlRecord>

        @JvmStatic
        @BeforeClass
        fun setUp() {
            val modelStream = UrlParityTest::class.java.classLoader?.getResourceAsStream("v7_champion_portable.json")
                ?: throw IllegalStateException("v7_champion_portable.json not found in test resources")
            val goldenStream = UrlParityTest::class.java.classLoader?.getResourceAsStream("golden_urls.json")
                ?: throw IllegalStateException("golden_urls.json not found in test resources")

            scanner = UrlScanner.fromInputStream(modelStream)
            val goldenJson = goldenStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            records = parseGoldenUrlRecords(goldenJson)
            assertEquals(151, records.size)
        }

        private fun parseGoldenUrlRecords(json: String): List<GoldenUrlRecord> {
            val records = mutableListOf<GoldenUrlRecord>()
            var pos = 0
            while (pos < json.length) {
                val idMarker = "\"id\""
                val idIdx = json.indexOf(idMarker, pos)
                if (idIdx == -1) break

                fun getField(key: String, from: Int): String {
                    val marker = "\"$key\""
                    val idx = json.indexOf(marker, from)
                    if (idx == -1) return ""
                    val colonIdx = json.indexOf(':', idx)
                    if (colonIdx == -1) return ""
                    var start = colonIdx + 1
                    while (start < json.length && json[start].isWhitespace()) start++
                    if (start < json.length && json[start] == '"') {
                        val s = start + 1
                        var e = s
                        while (e < json.length && json[e] != '"') {
                            if (json[e] == '\\') e += 2 else e++
                        }
                        return json.substring(s, e)
                    } else {
                        var e = start
                        while (e < json.length && json[e] != ',' && json[e] != '}' && json[e] != '\n') e++
                        return json.substring(start, e).trim()
                    }
                }

                val id = getField("id", idIdx)
                val url = getField("url", idIdx)
                val cat = getField("category", idIdx)
                val rawP = getField("raw_probability", idIdx).toFloat()
                val finalP = getField("final_probability", idIdx).toFloat()
                val label = getField("label", idIdx)
                val isMal = label == "MALICIOUS"

                val fMarker = "\"features\""
                val fIdx = json.indexOf(fMarker, idIdx)
                val fStart = json.indexOf('[', fIdx) + 1
                val fEnd = json.indexOf(']', fStart)
                val fTokens = json.substring(fStart, fEnd).split(',')
                val features = FloatArray(fTokens.size)
                for (i in fTokens.indices) {
                    features[i] = fTokens[i].trim().toFloat()
                }

                records.add(GoldenUrlRecord(id, url, cat, features, rawP, finalP, isMal, label))
                pos = fEnd
            }
            return records
        }
    }

    data class GoldenUrlRecord(
        val id: String,
        val url: String,
        val category: String,
        val features: FloatArray,
        val rawProbability: Float,
        val finalProbability: Float,
        val isMalicious: Boolean,
        val label: String
    )

    @Test
    fun testAll151GoldenRecordsLabelParity() {
        var labelMatches = 0
        var maxFeatDiff = 0.0
        var maxRawPDiff = 0.0
        var maxFinalPDiff = 0.0
        val mismatches = mutableListOf<String>()

        for (rec in records) {
            val ktFeatures = scanner.extractFeatures(rec.url)
            val result = scanner.scan(rec.url)

            for (i in 0 until 67) {
                val pyVal = rec.features[i]
                val ktVal = ktFeatures[i]
                val diff = abs(pyVal.toDouble() - ktVal.toDouble())
                if (diff > maxFeatDiff) maxFeatDiff = diff
            }

            val rawDiff = abs(rec.rawProbability.toDouble() - result.rawProbability.toDouble())
            if (rawDiff > maxRawPDiff) maxRawPDiff = rawDiff

            val finalDiff = abs(rec.finalProbability.toDouble() - result.probability.toDouble())
            if (finalDiff > maxFinalPDiff) maxFinalPDiff = finalDiff

            if (rec.label == result.label && rec.isMalicious == result.isMalicious) {
                labelMatches++
            } else {
                mismatches.add("${rec.id}: expected ${rec.label} (${rec.finalProbability}), got ${result.label} (${result.probability})")
            }
        }

        assertTrue("Max feature difference should be within single float precision: $maxFeatDiff", maxFeatDiff < 1e-4)
        assertTrue("Max raw probability difference should be within tolerance: $maxRawPDiff", maxRawPDiff < 1e-4)
        assertTrue("Max final probability difference should be within tolerance: $maxFinalPDiff", maxFinalPDiff < 1e-4)
        assertTrue("Label mismatches found: $mismatches", mismatches.isEmpty())
        assertEquals("Label parity must be exactly 151/151", 151, labelMatches)
    }

    @Test
    fun testSafeBrandDomainGating() {
        val googleResult = scanner.scan("https://www.google.com")
        assertEquals("BENIGN", googleResult.label)
        assertTrue(googleResult.isSafeBrandGated)
        assertEquals(0.001f, googleResult.probability, 1e-5f)

        val wikipediaResult = scanner.scan("https://en.wikipedia.org/wiki/Computer_security")
        assertEquals("BENIGN", wikipediaResult.label)
        assertTrue(wikipediaResult.probability < 0.22588723f)
    }

    @Test
    fun testMaliciousPhishingUrl() {
        val result = scanner.scan("http://secure-login-google.com.account-update.xyz/login.php")
        assertEquals("MALICIOUS", result.label)
        assertTrue(result.isMalicious)
        assertTrue(result.probability >= 0.22588723f)
    }

    @Test
    fun testMalformedAndEmptyUrls() {
        val emptyResult = scanner.scan("")
        // Empty URL produces length 0 features which HistGBM classifies as non-standard/malicious
        assertEquals("MALICIOUS", emptyResult.label)
        assertTrue(emptyResult.probability >= 0.22588723f)
    }
}
