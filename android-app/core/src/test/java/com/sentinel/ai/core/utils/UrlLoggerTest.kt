package com.sentinel.ai.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UrlLoggerTest {
    @Test
    fun testNormalUrl() {
        assertEquals("https://example.com/path/to/resource", UrlLogger.redactUrl("https://example.com/path/to/resource"))
    }

    @Test
    fun testUrlWithQuery() {
        assertEquals("https://example.com/path?[REDACTED]", UrlLogger.redactUrl("https://example.com/path?token=SECRET&user=123"))
    }

    @Test
    fun testUrlWithFragment() {
        assertEquals("https://example.com/path?[REDACTED]", UrlLogger.redactUrl("https://example.com/path#section1"))
    }

    @Test
    fun testUrlWithBoth() {
        assertEquals("https://example.com/path?[REDACTED]", UrlLogger.redactUrl("https://example.com/path?token=SECRET#section1"))
    }

    @Test
    fun testTokenLikeQueryParameters() {
        assertEquals("https://example.com/reset?[REDACTED]", UrlLogger.redactUrl("https://example.com/reset?token=a1b2c3d4e5f6"))
    }

    @Test
    fun testMalformedUrl() {
        assertEquals("not_a_valid_url", UrlLogger.redactUrl("not_a_valid_url"))
    }
    
    @Test
    fun testEmptyUrl() {
        assertEquals("[EMPTY_URL]", UrlLogger.redactUrl(""))
        assertEquals("[EMPTY_URL]", UrlLogger.redactUrl(null))
    }

    @Test
    fun testCredentials() {
        assertEquals("https://example.com/path", UrlLogger.redactUrl("https://user:password@example.com/path"))
        assertEquals("https://example.com/path?[REDACTED]", UrlLogger.redactUrl("https://user:password@example.com/path?query=1"))
    }

    @Test
    fun testAtInPath() {
        assertEquals("https://example.com/@username", UrlLogger.redactUrl("https://example.com/@username"))
    }

    @Test
    fun testIPv6() {
        assertEquals("http://[2001:db8::1]:8080/path", UrlLogger.redactUrl("http://[2001:db8::1]:8080/path"))
    }
}
