package com.sentinel.ai.contacts

import android.content.Context
import com.sentinel.ai.core.sender.ContactLookupStatus
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidContactResolverTest {

    @Test
    fun `exact and case-insensitive contact name matching with whitespace normalization`() {
        assertTrue(AndroidContactResolver.isNameMatch("John Doe", "john doe"))
        assertTrue(AndroidContactResolver.isNameMatch("  Laksh  ", "Laksh"))
        assertTrue(AndroidContactResolver.isNameMatch("Alice Smith", "ALICE SMITH"))
        assertTrue(AndroidContactResolver.isNameMatch("Bob   Jones", "Bob Jones"))
        assertTrue(AndroidContactResolver.isNameMatch("Dr. Jane Watson", "Dr Jane Watson"))
    }

    @Test
    fun `rejects short-name and substring false positives`() {
        assertFalse(AndroidContactResolver.isNameMatch("Delivery Alert", "Al"))
        assertFalse(AndroidContactResolver.isNameMatch("Al", "Delivery Alert"))
        assertFalse(AndroidContactResolver.isNameMatch("Bank Admin", "Dan"))
        assertFalse(AndroidContactResolver.isNameMatch("Dan", "Bank Admin"))
        assertFalse(AndroidContactResolver.isNameMatch("Laksh", "Lakshmi"))
        assertFalse(AndroidContactResolver.isNameMatch("Lakshmi", "Laksh"))
        assertFalse(AndroidContactResolver.isNameMatch("Customer Service", "Tom"))
        assertFalse(AndroidContactResolver.isNameMatch("", "Laksh"))
        assertFalse(AndroidContactResolver.isNameMatch("Laksh", ""))
        assertFalse(AndroidContactResolver.isNameMatch("   ", "   "))
    }

    @Test
    fun `normalizes international phone numbers across countries`() {
        assertEquals("+15552345678", AndroidContactResolver.normalizeNumber("+1 (555) 234-5678"))
        assertEquals("+447911123456", AndroidContactResolver.normalizeNumber("+44 7911 123456"))
        assertEquals("+919876543210", AndroidContactResolver.normalizeNumber("+91 98765-43210"))
        assertEquals("+919876543210", AndroidContactResolver.normalizeNumber("(+91) 98765.43210"))
        assertEquals("+971501234567", AndroidContactResolver.normalizeNumber("+971 50 123 4567"))
        assertEquals("9876543210", AndroidContactResolver.normalizeNumber("98765 43210"))
        assertEquals("5552345678", AndroidContactResolver.normalizeNumber("(555) 234-5678"))
    }

    @Test
    fun `matches phone numbers across national and international formats`() {
        assertTrue(AndroidContactResolver.phoneNumbersMatch("+91 98765-43210", "9876543210"))
        assertTrue(AndroidContactResolver.phoneNumbersMatch("9876543210", "+91 98765-43210"))
        assertTrue(AndroidContactResolver.phoneNumbersMatch("+1 (555) 234-5678", "5552345678"))
        assertTrue(AndroidContactResolver.phoneNumbersMatch("+44 7911 123456", "+44 7911 123456"))
        assertTrue(AndroidContactResolver.phoneNumbersMatch("+971 50 123 4567", "+971 50 123 4567"))

        // Rejection of mismatching numbers and short numbers
        assertFalse(AndroidContactResolver.phoneNumbersMatch("+1 555 111 2222", "+1 555 333 4444"))
        assertFalse(AndroidContactResolver.phoneNumbersMatch("12345", "9876512345"))
        assertFalse(AndroidContactResolver.phoneNumbersMatch("", "+1 555 123 4567"))
    }

    @Test
    fun `identifies phone number patterns correctly`() {
        assertTrue(AndroidContactResolver.isPhoneNumber("+91 98765 43210"))
        assertTrue(AndroidContactResolver.isPhoneNumber("9876543210"))
        assertTrue(AndroidContactResolver.isPhoneNumber("+1 (555) 234-5678"))
        assertTrue(AndroidContactResolver.isPhoneNumber("+44 7911 123456"))
        assertTrue(AndroidContactResolver.isPhoneNumber("+971 50 123 4567"))
        assertTrue(AndroidContactResolver.isPhoneNumber("(555) 234-5678"))
        assertFalse(AndroidContactResolver.isPhoneNumber("John Doe"))
        assertFalse(AndroidContactResolver.isPhoneNumber("Alice Bank Alert"))
        assertFalse(AndroidContactResolver.isPhoneNumber("12345"))
        assertFalse(AndroidContactResolver.isPhoneNumber(""))
    }

    @Test
    fun `identifies email address patterns correctly`() {
        assertTrue(AndroidContactResolver.isEmail("user@example.com"))
        assertTrue(AndroidContactResolver.isEmail("john.doe+test@domain.co.in"))
        assertFalse(AndroidContactResolver.isEmail("not-an-email"))
        assertFalse(AndroidContactResolver.isEmail("+919876543210"))
        assertFalse(AndroidContactResolver.isEmail(""))
    }

    @Test
    fun `returns invalid identifier resolution for empty or blank input`() {
        val mockContext = mockk<Context>(relaxed = true)
        val resolver = AndroidContactResolver(mockContext)

        val resolution = resolver.resolve("   ")
        assertFalse(resolution.isKnownContact)
        assertNull(resolution.displayName)
        assertEquals("", resolution.normalizedIdentifier)
        assertEquals(ContactLookupStatus.INVALID_IDENTIFIER, resolution.lookupStatus)
    }

    @Test
    fun `returns permission denied resolution when READ_CONTACTS is not granted`() {
        val mockContext = mockk<Context>(relaxed = true)
        val resolver = AndroidContactResolver(mockContext).apply {
            permissionChecker = { _, _ -> false }
        }

        val resolution = resolver.resolve("+919876543210")
        assertFalse(resolution.isKnownContact)
        assertNull(resolution.displayName)
        assertEquals("+919876543210", resolution.normalizedIdentifier)
        assertEquals(ContactLookupStatus.PERMISSION_DENIED, resolution.lookupStatus)
    }
}
