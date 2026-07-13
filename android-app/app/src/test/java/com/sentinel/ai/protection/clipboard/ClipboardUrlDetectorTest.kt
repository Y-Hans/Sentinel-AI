package com.sentinel.ai.protection.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardUrlDetectorTest {
    @Test fun `accepts https URL`() {
        assertEquals("https://google.com", ClipboardUrlDetector.firstValidUrl("https://google.com"))
    }

    @Test fun `accepts http IP URL embedded in text`() {
        assertEquals(
            "http://192.168.1.1/login",
            ClipboardUrlDetector.firstValidUrl("Open http://192.168.1.1/login now")
        )
    }

    @Test fun `rejects non URL text and inferred schemes`() {
        assertNull(ClipboardUrlDetector.firstValidUrl("hello world"))
        assertEquals("https://google.com", ClipboardUrlDetector.firstValidUrl("google.com"))
    }
}
