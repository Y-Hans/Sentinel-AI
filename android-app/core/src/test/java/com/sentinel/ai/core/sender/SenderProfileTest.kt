package com.sentinel.ai.core.sender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderProfileTest {

    @Test
    fun `creates valid SenderProfile with default arguments`() {
        val profile = SenderProfile(
            rawIdentifier = "AD-HDFCBK-S",
            normalizedIdentifier = "AD-HDFCBK-S",
            senderType = SenderType.SERVICE
        )

        assertEquals("AD-HDFCBK-S", profile.rawIdentifier)
        assertEquals("AD-HDFCBK-S", profile.normalizedIdentifier)
        assertEquals(SenderType.SERVICE, profile.senderType)
        assertFalse(profile.isKnownContact)
        assertNull(profile.displayName)
        assertEquals(1.0f, profile.confidence, 0.001f)
    }

    @Test
    fun `creates SenderProfile with explicit properties`() {
        val profile = SenderProfile(
            rawIdentifier = " +91 98765 43210 ",
            normalizedIdentifier = "+919876543210",
            senderType = SenderType.PERSONAL,
            isKnownContact = true,
            displayName = "Alice Smith",
            confidence = 0.85f
        )

        assertEquals(" +91 98765 43210 ", profile.rawIdentifier)
        assertEquals("+919876543210", profile.normalizedIdentifier)
        assertEquals(SenderType.PERSONAL, profile.senderType)
        assertTrue(profile.isKnownContact)
        assertEquals("Alice Smith", profile.displayName)
        assertEquals(0.85f, profile.confidence, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects confidence greater than 1`() {
        SenderProfile(
            rawIdentifier = "test",
            normalizedIdentifier = "test",
            senderType = SenderType.UNKNOWN,
            confidence = 1.1f
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects confidence less than 0`() {
        SenderProfile(
            rawIdentifier = "test",
            normalizedIdentifier = "test",
            senderType = SenderType.UNKNOWN,
            confidence = -0.1f
        )
    }

    @Test
    fun `ContactResolution matchFound builds expected state`() {
        val resolution = ContactResolution.matchFound("Bob Jones", "+15551234567")
        assertTrue(resolution.isKnownContact)
        assertEquals("Bob Jones", resolution.displayName)
        assertEquals("+15551234567", resolution.normalizedIdentifier)
        assertEquals(ContactLookupStatus.MATCH_FOUND, resolution.lookupStatus)
    }

    @Test
    fun `ContactResolution noMatch builds expected state`() {
        val resolution = ContactResolution.noMatch("+15551234567")
        assertFalse(resolution.isKnownContact)
        assertNull(resolution.displayName)
        assertEquals("+15551234567", resolution.normalizedIdentifier)
        assertEquals(ContactLookupStatus.NO_MATCH, resolution.lookupStatus)
    }

    @Test
    fun `ContactResolution permissionDenied builds expected state`() {
        val resolution = ContactResolution.permissionDenied("+15551234567")
        assertFalse(resolution.isKnownContact)
        assertNull(resolution.displayName)
        assertEquals("+15551234567", resolution.normalizedIdentifier)
        assertEquals(ContactLookupStatus.PERMISSION_DENIED, resolution.lookupStatus)
    }

    @Test
    fun `ContactResolution invalidIdentifier builds expected state`() {
        val resolution = ContactResolution.invalidIdentifier("   ")
        assertFalse(resolution.isKnownContact)
        assertNull(resolution.displayName)
        assertEquals("", resolution.normalizedIdentifier)
        assertEquals(ContactLookupStatus.INVALID_IDENTIFIER, resolution.lookupStatus)
    }

    @Test
    fun `ContactResolution lookupError builds expected state`() {
        val resolution = ContactResolution.lookupError("+15551234567")
        assertFalse(resolution.isKnownContact)
        assertNull(resolution.displayName)
        assertEquals("+15551234567", resolution.normalizedIdentifier)
        assertEquals(ContactLookupStatus.LOOKUP_ERROR, resolution.lookupStatus)
    }

    @Test
    fun `ContactResolver default isKnownContact reflects resolution`() {
        val resolver = object : ContactResolver {
            override fun resolve(identifier: String): ContactResolution {
                return if (identifier == "known") {
                    ContactResolution.matchFound("Known User", identifier)
                } else {
                    ContactResolution.noMatch(identifier)
                }
            }
        }

        assertTrue(resolver.isKnownContact("known"))
        assertFalse(resolver.isKnownContact("unknown"))
    }
}
