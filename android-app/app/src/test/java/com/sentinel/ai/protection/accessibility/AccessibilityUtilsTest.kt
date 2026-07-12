package com.sentinel.ai.protection.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityUtilsTest {

    @Test
    fun `extractUrls returns every http and https URL in encounter order`() {
        val text = "Open https://example.com/path and http://sub.example.org/next?q=1"

        assertEquals(
            listOf("https://example.com/path", "http://sub.example.org/next?q=1"),
            AccessibilityUtils.extractUrls(text)
        )
    }

    @Test
    fun `extractUrls is case insensitive and removes duplicates within one event`() {
        val text = "HTTPS://Example.COM/path HTTPS://Example.COM/path"

        assertEquals(
            listOf("HTTPS://Example.COM/path"),
            AccessibilityUtils.extractUrls(text)
        )
    }

    @Test
    fun `shouldScanUrl suppresses observations inside five seconds`() {
        val recentUrls = linkedMapOf<String, Long>()

        assertTrue(AccessibilityUtils.shouldScanUrl(recentUrls, URL, observedAtMs = 1_000L))
        assertFalse(AccessibilityUtils.shouldScanUrl(recentUrls, URL, observedAtMs = 5_999L))
        assertTrue(AccessibilityUtils.shouldScanUrl(recentUrls, URL, observedAtMs = 10_999L))
    }

    @Test
    fun `shouldScanUrl keeps only the fifty most recently observed URLs`() {
        val recentUrls = linkedMapOf<String, Long>()

        repeat(AccessibilityUtils.MAX_RECENT_URLS + 1) { index ->
            AccessibilityUtils.shouldScanUrl(
                recentUrls = recentUrls,
                url = "https://example$index.com",
                observedAtMs = index.toLong()
            )
        }

        assertEquals(AccessibilityUtils.MAX_RECENT_URLS, recentUrls.size)
        assertFalse(recentUrls.containsKey("https://example0.com"))
        assertTrue(recentUrls.containsKey("https://example50.com"))
    }

    private companion object {
        const val URL = "https://example.com/path"
    }
}
