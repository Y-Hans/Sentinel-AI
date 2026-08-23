package com.sentinel.ai.listeners

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelNotificationListenerTest {

    @Test
    fun `normalizes Indian country code spacing and punctuation`() {
        assertEquals("9876543210", SentinelNotificationListener.normalizeNumber("+91 98765-43210"))
        assertEquals("9876543210", SentinelNotificationListener.normalizeNumber("98765 43210"))
        assertEquals("9876543210", SentinelNotificationListener.normalizeNumber("(+91) 98765.43210"))
        assertEquals("9876543210", SentinelNotificationListener.normalizeNumber("+919876543210"))
    }

    @Test
    fun `matches WhatsApp sender title to contact display name`() {
        assertTrue(SentinelNotificationListener.isNameMatch("Laksh", "laksh"))
        assertTrue(SentinelNotificationListener.isNameMatch("  Laksh  ", "Laksh"))
        assertTrue(SentinelNotificationListener.isNameMatch("Laksh", "Lakshmi"))
        assertFalse(SentinelNotificationListener.isNameMatch("", "Laksh"))
    }

    @Test
    fun `identifies phone number patterns correctly`() {
        assertTrue(SentinelNotificationListener.isPhoneNumber("+91 98765 43210"))
        assertTrue(SentinelNotificationListener.isPhoneNumber("9876543210"))
        assertTrue(SentinelNotificationListener.isPhoneNumber("+1 (555) 234-5678"))
        assertFalse(SentinelNotificationListener.isPhoneNumber("John Doe"))
        assertFalse(SentinelNotificationListener.isPhoneNumber("Alice Bank Alert"))
    }
}
