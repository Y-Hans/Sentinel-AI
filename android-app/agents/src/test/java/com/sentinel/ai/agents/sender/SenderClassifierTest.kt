package com.sentinel.ai.agents.sender

import com.sentinel.ai.core.sender.SenderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SenderClassifierTest {

    private val classifier = SenderClassifier()

    // ==========================================
    // 1. NORMALIZATION TESTS
    // ==========================================

    @Test
    fun `normalizes null, empty, and whitespace strings safely`() {
        assertEquals("", classifier.normalize(null))
        assertEquals("", classifier.normalize(""))
        assertEquals("", classifier.normalize("   "))
        assertEquals("", classifier.normalize("\t\n "))
    }

    @Test
    fun `normalizes SMS headers to uppercase`() {
        assertEquals("AD-HDFCBK-S", classifier.normalize("ad-hdfcbk-s"))
        assertEquals("AD-HDFCBK-S", classifier.normalize("  AD-Hdfcbk-S  "))
        assertEquals("VK-UIDAI-G", classifier.normalize("vk-uidai-g"))
        assertEquals("BZ-FLIPKT-P", classifier.normalize("bz-flipkt-p"))
        assertEquals("HDFCBK-S", classifier.normalize("hdfcbk-s"))
        assertEquals("FLIPKT-P", classifier.normalize("  flipkt-p  "))
    }

    @Test
    fun `normalizes phone numbers removing formatting and preserving plus`() {
        assertEquals("+919876543210", classifier.normalize("+91 98765-43210"))
        assertEquals("+919876543210", classifier.normalize("(+91) 98765.43210"))
        assertEquals("+15552345678", classifier.normalize("+1 (555) 234-5678"))
        assertEquals("+447911123456", classifier.normalize("+44 7911 123456"))
        assertEquals("+971501234567", classifier.normalize("+971 50 123 4567"))
        assertEquals("9876543210", classifier.normalize("98765 43210"))
        assertEquals("5552345678", classifier.normalize("(555) 234-5678"))
    }

    @Test
    fun `normalizes email addresses to lowercase`() {
        assertEquals("user@example.com", classifier.normalize("User@Example.COM"))
        assertEquals("john.doe@domain.co.in", classifier.normalize("  John.Doe@Domain.CO.IN  "))
    }

    @Test
    fun `preserves arbitrary alphanumeric text without mutation`() {
        assertEquals("UnknownSender123", classifier.normalize("  UnknownSender123  "))
    }

    // ==========================================
    // 2. SERVICE SMS HEADER TESTS (-S)
    // ==========================================

    @Test
    fun `classifies valid Indian Service headers with suffix -S`() {
        assertEquals(SenderType.SERVICE, classifier.classifyType("AD-HDFCBK-S"))
        assertEquals(SenderType.SERVICE, classifier.classifyType("VM-GOOGLE-S"))
        assertEquals(SenderType.SERVICE, classifier.classifyType("AD-SBIINB-S"))
        assertEquals(SenderType.SERVICE, classifier.classifyType("VM-AMAZON-S"))
        assertEquals(SenderType.SERVICE, classifier.classifyType("HDFCBK-S"))
        assertEquals(SenderType.SERVICE, classifier.classifyType("GOOGLE-S"))
        assertEquals(SenderType.SERVICE, classifier.classifyType("ad-hdfcbk-s"))
        assertEquals(SenderType.SERVICE, classifier.classifyType("  AD-HDFCBK-S  "))
    }

    // ==========================================
    // 3. GOVERNMENT SMS HEADER TESTS (-G)
    // ==========================================

    @Test
    fun `classifies valid Indian Government headers with suffix -G`() {
        assertEquals(SenderType.GOVERNMENT, classifier.classifyType("VK-UIDAI-G"))
        assertEquals(SenderType.GOVERNMENT, classifier.classifyType("AD-MYGOV-G"))
        assertEquals(SenderType.GOVERNMENT, classifier.classifyType("UIDAI-G"))
        assertEquals(SenderType.GOVERNMENT, classifier.classifyType("VM-ITDEPT-G"))
        assertEquals(SenderType.GOVERNMENT, classifier.classifyType("AD-EPFOHO-G"))
        assertEquals(SenderType.GOVERNMENT, classifier.classifyType("vk-uidai-g"))
        assertEquals(SenderType.GOVERNMENT, classifier.classifyType("  AD-MYGOV-G  "))
    }

    // ==========================================
    // 4. PROMOTIONAL SMS HEADER TESTS (-P)
    // ==========================================

    @Test
    fun `classifies valid Indian Promotional headers with suffix -P`() {
        assertEquals(SenderType.PROMOTIONAL, classifier.classifyType("BZ-FLIPKT-P"))
        assertEquals(SenderType.PROMOTIONAL, classifier.classifyType("AD-SWIGGY-P"))
        assertEquals(SenderType.PROMOTIONAL, classifier.classifyType("FLIPKT-P"))
        assertEquals(SenderType.PROMOTIONAL, classifier.classifyType("VM-ZOMATO-P"))
        assertEquals(SenderType.PROMOTIONAL, classifier.classifyType("bz-flipkt-p"))
        assertEquals(SenderType.PROMOTIONAL, classifier.classifyType("  AD-SWIGGY-P  "))
    }

    // ==========================================
    // 5. PERSONAL PHONE NUMBER TESTS
    // ==========================================

    @Test
    fun `classifies valid personal phone numbers across international and national formats`() {
        assertEquals(SenderType.PERSONAL, classifier.classifyType("+91 98765 43210"))
        assertEquals(SenderType.PERSONAL, classifier.classifyType("+919876543210"))
        assertEquals(SenderType.PERSONAL, classifier.classifyType("9876543210"))
        assertEquals(SenderType.PERSONAL, classifier.classifyType("+1 (555) 234-5678"))
        assertEquals(SenderType.PERSONAL, classifier.classifyType("+44 7911 123456"))
        assertEquals(SenderType.PERSONAL, classifier.classifyType("+971 50 123 4567"))
        assertEquals(SenderType.PERSONAL, classifier.classifyType("(555) 234-5678"))
        assertEquals(SenderType.PERSONAL, classifier.classifyType("  +91 98765-43210  "))
        assertEquals(SenderType.PERSONAL, classifier.classifyType("98765.43210"))
    }

    // ==========================================
    // 6. UNKNOWN / SECURITY BOUNDARY TESTS
    // ==========================================

    @Test
    fun `rejects false positive and malformed identifiers as UNKNOWN`() {
        // Empty and whitespace
        assertEquals(SenderType.UNKNOWN, classifier.classifyType(""))
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("   "))

        // Emails, URLs, handles
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("user@example.com"))
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("@username"))
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("example.com"))
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("https://example.com"))

        // Strings with -S / -G / -P but invalid structure
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("some-user-S"))
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("foo-Something"))
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("arbitrary strings containing -S"))
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("malformed-S-header-123"))
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("AD-HDFCBK-S-EXTRA"))
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("12-HDFCBK-S"))
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("-HDFCBK-S"))
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("AD-H-S"))
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("AD-HDFCBKTOOLONGNAME-S"))

        // Numeric non-phone tokens (OTPs, shortcodes, transaction IDs, timestamps)
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("482913")) // 6-digit OTP
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("12345"))  // 5-digit shortcode
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("17000000000000000")) // timestamp > 15 digits
        assertEquals(SenderType.UNKNOWN, classifier.classifyType("TXN123456789"))
    }

    // ==========================================
    // 7. SENDER PROFILE CREATION TESTS
    // ==========================================

    @Test
    fun `classifies raw string into complete SenderProfile without contact resolver dependency`() {
        val profile = classifier.classify("  ad-hdfcbk-s  ", displayName = "HDFC Alert")
        assertEquals("  ad-hdfcbk-s  ", profile.rawIdentifier)
        assertEquals("AD-HDFCBK-S", profile.normalizedIdentifier)
        assertEquals(SenderType.SERVICE, profile.senderType)
        assertFalse(profile.isKnownContact)
        assertEquals("HDFC Alert", profile.displayName)
        assertEquals(1.0f, profile.confidence, 0.001f)
    }

    @Test
    fun `classifies phone number into SenderProfile with default contact fields`() {
        val profile = classifier.classify("+91 98765 43210")
        assertEquals("+91 98765 43210", profile.rawIdentifier)
        assertEquals("+919876543210", profile.normalizedIdentifier)
        assertEquals(SenderType.PERSONAL, profile.senderType)
        assertFalse(profile.isKnownContact)
        assertNull(profile.displayName)
        assertEquals(1.0f, profile.confidence, 0.001f)
    }

    @Test
    fun `handles blank raw identifier safely in classify`() {
        val profile = classifier.classify("   ", displayName = "Some Name")
        assertEquals("   ", profile.rawIdentifier)
        assertEquals("", profile.normalizedIdentifier)
        assertEquals(SenderType.UNKNOWN, profile.senderType)
        assertFalse(profile.isKnownContact)
        assertEquals("Some Name", profile.displayName)
        assertEquals(0.0f, profile.confidence, 0.001f)
    }

    @Test
    fun `handles null raw identifier safely in classify`() {
        val profile = classifier.classify(null)
        assertEquals("", profile.rawIdentifier)
        assertEquals("", profile.normalizedIdentifier)
        assertEquals(SenderType.UNKNOWN, profile.senderType)
        assertFalse(profile.isKnownContact)
        assertNull(profile.displayName)
        assertEquals(0.0f, profile.confidence, 0.001f)
    }
}
