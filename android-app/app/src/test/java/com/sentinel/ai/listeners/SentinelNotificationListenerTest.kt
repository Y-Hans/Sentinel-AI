package com.sentinel.ai.listeners

import com.sentinel.ai.agents.whatsapp.WhatsAppNotificationSnapshot
import com.sentinel.ai.core.sender.ContactResolution
import com.sentinel.ai.core.sender.ContactResolver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelNotificationListenerTest {

    @Test
    fun `isKnownContact delegates correctly to injected ContactResolver`() {
        val fakeResolver = object : ContactResolver {
            override fun resolve(identifier: String): ContactResolution {
                return if (identifier == "Alice" || identifier == "+919876543210") {
                    ContactResolution.matchFound(displayName = "Alice", normalizedIdentifier = identifier)
                } else {
                    ContactResolution.noMatch(identifier)
                }
            }
        }

        val listener = SentinelNotificationListener().apply {
            contactResolver = fakeResolver
        }

        val knownSnapshot = WhatsAppNotificationSnapshot(
            packageName = "com.whatsapp",
            notificationKey = "key1",
            timestampMs = 1000L,
            title = "Alice",
            text = "Hello there"
        )
        assertTrue(listener.isKnownContact(knownSnapshot))

        val unknownSnapshot = WhatsAppNotificationSnapshot(
            packageName = "com.whatsapp",
            notificationKey = "key2",
            timestampMs = 1000L,
            title = "+911112223334",
            text = "Hello there"
        )
        assertFalse(listener.isKnownContact(unknownSnapshot))

        val emptySnapshot = WhatsAppNotificationSnapshot(
            packageName = "com.whatsapp",
            notificationKey = "key3",
            timestampMs = 1000L,
            title = "   ",
            text = "Hello there"
        )
        assertFalse(listener.isKnownContact(emptySnapshot))
    }
}
