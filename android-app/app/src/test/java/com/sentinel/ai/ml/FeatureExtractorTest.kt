package com.sentinel.ai.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureExtractorTest {

    @Test
    fun `extracts the fifteen model features in training order`() {
        val url = "https://login.example.com/a1"

        val result = FeatureExtractor.extract(url)
        assertTrue(result is FeatureExtractionResult.Success)
        val features = (result as FeatureExtractionResult.Success).features

        assertEquals(FeatureExtractor.FEATURE_COUNT, features.size)
        assertEquals(url.length.toFloat(), features[0])
        assertEquals("login.example.com".length.toFloat(), features[1])
        assertEquals(0f, features[2])
        assertEquals(1f, features[3])
        assertEquals(1f, features[4])
        assertEquals(1f, features[5])
        assertEquals(0f, features[8])
        assertEquals(0f, features[9])
        assertEquals(0.25f, features[10])
        assertEquals(0f, features[11])
        assertEquals(3f, features[12])
        assertEquals(0f, features[13])
        assertEquals(6f / 17f, features[14])
        assertTrue(features.all(Float::isFinite))
    }

    @Test
    fun `malformed and empty URLs always return fifteen finite features`() {
        val inputs = listOf(
            "",
            " ",
            "https://",
            "https://example.com:99999",
            "https://example.com/?redirect=%ZZ",
            "mailto:user@example.com"
        )

        inputs.forEach { input ->
            val result = FeatureExtractor.extract(input)
            assertTrue(result is FeatureExtractionResult.Success)
            val features = (result as FeatureExtractionResult.Success).features

            assertEquals("Unexpected feature count for $input", 15, features.size)
            assertTrue("Non-finite feature for $input", features.all(Float::isFinite))
        }
    }

    @Test
    fun `detects IPv4 without throwing on URI parsing`() {
        val result = FeatureExtractor.extract("http://192.168.1.1/login")
        assertTrue(result is FeatureExtractionResult.Success)
        val features = (result as FeatureExtractionResult.Success).features

        assertEquals(1f, features[2])
        assertEquals(0f, features[4])
        assertEquals(1f, features[5])
    }

    @Test
    fun `scheme-less URLs use the same normalized representation as Python`() {
        val result = FeatureExtractor.extract("google.com")
        assertTrue(result is FeatureExtractionResult.Success)
        val features = (result as FeatureExtractionResult.Success).features

        assertEquals(18f, features[0])
        assertEquals(10f, features[1])
        assertEquals(0f, features[3])
        assertEquals(1f, features[4])
        assertEquals(1f, features[13])
        assertEquals(4f / 10f, features[14])
    }
}
