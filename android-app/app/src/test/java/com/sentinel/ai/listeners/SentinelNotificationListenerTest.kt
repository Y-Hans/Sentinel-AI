package com.sentinel.ai.listeners

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelNotificationListenerTest {

    @Test
    fun `exact and case-insensitive contact name matching with whitespace normalization`() {
        assertTrue(SentinelNotificationListener.isNameMatch("John Doe", "john doe"))
        assertTrue(SentinelNotificationListener.isNameMatch("  Laksh  ", "Laksh"))
        assertTrue(SentinelNotificationListener.isNameMatch("Alice Smith", "ALICE SMITH"))
        assertTrue(SentinelNotificationListener.isNameMatch("Bob   Jones", "Bob Jones"))
        assertTrue(SentinelNotificationListener.isNameMatch("Dr. Jane Watson", "Dr Jane Watson"))
    }

    @Test
    fun `rejects short-name and substring false positives`() {
        // F-01 regression prevention: Substrings must NOT match
        assertFalse(SentinelNotificationListener.isNameMatch("Delivery Alert", "Al"))
        assertFalse(SentinelNotificationListener.isNameMatch("Al", "Delivery Alert"))
        assertFalse(SentinelNotificationListener.isNameMatch("Bank Admin", "Dan"))
        assertFalse(SentinelNotificationListener.isNameMatch("Dan", "Bank Admin"))
        assertFalse(SentinelNotificationListener.isNameMatch("Laksh", "Lakshmi"))
        assertFalse(SentinelNotificationListener.isNameMatch("Lakshmi", "Laksh"))
        assertFalse(SentinelNotificationListener.isNameMatch("Customer Service", "Tom"))
        assertFalse(SentinelNotificationListener.isNameMatch("", "Laksh"))
        assertFalse(SentinelNotificationListener.isNameMatch("Laksh", ""))
        assertFalse(SentinelNotificationListener.isNameMatch("   ", "   "))
    }

    @Test
    fun `normalizes international phone numbers across countries`() {
        // F-03: Multi-country dialing code normalization
        assertEquals("+15552345678", SentinelNotificationListener.normalizeNumber("+1 (555) 234-5678"))
        assertEquals("+447911123456", SentinelNotificationListener.normalizeNumber("+44 7911 123456"))
        assertEquals("+919876543210", SentinelNotificationListener.normalizeNumber("+91 98765-43210"))
        assertEquals("+919876543210", SentinelNotificationListener.normalizeNumber("(+91) 98765.43210"))
        assertEquals("+971501234567", SentinelNotificationListener.normalizeNumber("+971 50 123 4567"))
        assertEquals("9876543210", SentinelNotificationListener.normalizeNumber("98765 43210"))
        assertEquals("5552345678", SentinelNotificationListener.normalizeNumber("(555) 234-5678"))
    }

    @Test
    fun `matches phone numbers across national and international formats`() {
        assertTrue(SentinelNotificationListener.phoneNumbersMatch("+91 98765-43210", "9876543210"))
        assertTrue(SentinelNotificationListener.phoneNumbersMatch("9876543210", "+91 98765-43210"))
        assertTrue(SentinelNotificationListener.phoneNumbersMatch("+1 (555) 234-5678", "5552345678"))
        assertTrue(SentinelNotificationListener.phoneNumbersMatch("+44 7911 123456", "+44 7911 123456"))
        assertTrue(SentinelNotificationListener.phoneNumbersMatch("+971 50 123 4567", "+971 50 123 4567"))

        // Rejection of mismatching numbers and short numbers
        assertFalse(SentinelNotificationListener.phoneNumbersMatch("+1 555 111 2222", "+1 555 333 4444"))
        assertFalse(SentinelNotificationListener.phoneNumbersMatch("12345", "9876512345"))
        assertFalse(SentinelNotificationListener.phoneNumbersMatch("", "+1 555 123 4567"))
    }

    @Test
    fun `identifies phone number patterns correctly`() {
        assertTrue(SentinelNotificationListener.isPhoneNumber("+91 98765 43210"))
        assertTrue(SentinelNotificationListener.isPhoneNumber("9876543210"))
        assertTrue(SentinelNotificationListener.isPhoneNumber("+1 (555) 234-5678"))
        assertTrue(SentinelNotificationListener.isPhoneNumber("+44 7911 123456"))
        assertTrue(SentinelNotificationListener.isPhoneNumber("+971 50 123 4567"))
        assertFalse(SentinelNotificationListener.isPhoneNumber("John Doe"))
        assertFalse(SentinelNotificationListener.isPhoneNumber("Alice Bank Alert"))
        assertFalse(SentinelNotificationListener.isPhoneNumber("12345"))
        assertFalse(SentinelNotificationListener.isPhoneNumber(""))
    }
}
