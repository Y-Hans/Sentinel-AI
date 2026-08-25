package com.sentinel.ai.ui

import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.ui.screens.history.historyTarget
import com.sentinel.ai.ui.util.SenderPresentation
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryPresentationTest {

    @Test
    fun `URL scan displays target URL over source label`() {
        val scan = scanResult(
            target = "https://example.com",
            source = "Intent (Link)",
            senderDisplayName = null,
            senderIdentifier = null
        )
        val senderPresentation = SenderPresentation(
            primaryText = "Unknown sender",
            secondaryText = null
        )

        val result = historyTarget(
            item = scan,
            senderPresentation = senderPresentation,
            appLabel = "Intent (Link)"
        )

        assertEquals("https://example.com", result)
    }

    @Test
    fun `file scan displays target filename over source label`() {
        val scan = scanResult(
            target = "attachment.apk",
            source = "File",
            senderDisplayName = null,
            senderIdentifier = null
        )
        val senderPresentation = SenderPresentation(
            primaryText = "Unknown sender",
            secondaryText = null
        )

        val result = historyTarget(
            item = scan,
            senderPresentation = senderPresentation,
            appLabel = "File"
        )

        assertEquals("attachment.apk", result)
    }

    @Test
    fun `WhatsApp notification with resolved contact displays sender display name`() {
        val scan = scanResult(
            target = null,
            source = "com.whatsapp",
            senderDisplayName = "John Doe",
            senderIdentifier = "+1234567890"
        )
        val senderPresentation = SenderPresentation(
            primaryText = "John Doe",
            secondaryText = "+1234567890"
        )

        val result = historyTarget(
            item = scan,
            senderPresentation = senderPresentation,
            appLabel = "WhatsApp"
        )

        assertEquals("John Doe", result)
    }

    @Test
    fun `anonymous notification with phone identifier displays senderIdentifier`() {
        val scan = scanResult(
            target = null,
            source = "com.whatsapp",
            senderDisplayName = null,
            senderIdentifier = "+1234567890"
        )
        val senderPresentation = SenderPresentation(
            primaryText = "+1234567890",
            secondaryText = null
        )

        val result = historyTarget(
            item = scan,
            senderPresentation = senderPresentation,
            appLabel = "WhatsApp"
        )

        assertEquals("+1234567890", result)
    }

    @Test
    fun `anonymous notification without identifier falls back to appLabel`() {
        val scan = scanResult(
            target = null,
            source = "com.whatsapp",
            senderDisplayName = null,
            senderIdentifier = null
        )
        val senderPresentation = SenderPresentation(
            primaryText = "Unknown sender",
            secondaryText = null
        )

        val result = historyTarget(
            item = scan,
            senderPresentation = senderPresentation,
            appLabel = "WhatsApp"
        )

        assertEquals("WhatsApp", result)
    }

    @Test
    fun `blank target falls back to sender display name`() {
        val scan = scanResult(
            target = "   ",
            source = "com.whatsapp",
            senderDisplayName = "Alice",
            senderIdentifier = "+1987654321"
        )
        val senderPresentation = SenderPresentation(
            primaryText = "Alice",
            secondaryText = "+1987654321"
        )

        val result = historyTarget(
            item = scan,
            senderPresentation = senderPresentation,
            appLabel = "WhatsApp"
        )

        assertEquals("Alice", result)
    }

    @Test
    fun `blank sender display name falls back to sender identifier`() {
        val scan = scanResult(
            target = null,
            source = "com.whatsapp",
            senderDisplayName = "  ",
            senderIdentifier = "+111222333"
        )
        val senderPresentation = SenderPresentation(
            primaryText = "Unknown sender",
            secondaryText = null
        )

        val result = historyTarget(
            item = scan,
            senderPresentation = senderPresentation,
            appLabel = "WhatsApp"
        )

        assertEquals("+111222333", result)
    }

    @Test
    fun `target takes priority over sender presentation`() {
        val scan = scanResult(
            target = "https://example.com/login",
            source = "Intent (Link)",
            senderDisplayName = "John",
            senderIdentifier = "+1234567890"
        )
        val senderPresentation = SenderPresentation(
            primaryText = "John",
            secondaryText = "+1234567890"
        )

        val result = historyTarget(
            item = scan,
            senderPresentation = senderPresentation,
            appLabel = "Chrome"
        )

        assertEquals("https://example.com/login", result)
    }

    @Test
    fun `target takes priority over appLabel`() {
        val scan = scanResult(
            target = "https://bank-secure.xyz",
            source = "Intent (Link)",
            senderDisplayName = null,
            senderIdentifier = null
        )
        val senderPresentation = SenderPresentation(
            primaryText = "Unknown sender",
            secondaryText = null
        )

        val result = historyTarget(
            item = scan,
            senderPresentation = senderPresentation,
            appLabel = "Chrome"
        )

        assertEquals("https://bank-secure.xyz", result)
    }

    @Test
    fun `resolveSenderPresentation resolves contact name when known`() {
        val fakeResolver = object : com.sentinel.ai.core.sender.ContactResolver {
            override fun resolve(identifier: String): com.sentinel.ai.core.sender.ContactResolution {
                return if (identifier == "+1234567890") {
                    com.sentinel.ai.core.sender.ContactResolution.matchFound("Alice", identifier)
                } else {
                    com.sentinel.ai.core.sender.ContactResolution.noMatch(identifier)
                }
            }
        }

        val presentation = com.sentinel.ai.ui.util.resolveSenderPresentation(
            senderDisplayName = null,
            senderIdentifier = "+1234567890",
            contactResolver = fakeResolver
        )

        assertEquals("Alice", presentation.primaryText)
        assertEquals("+1234567890", presentation.secondaryText)
    }

    @Test
    fun `resolveSenderPresentation falls back to Unknown Contact when identifier unknown`() {
        val fakeResolver = object : com.sentinel.ai.core.sender.ContactResolver {
            override fun resolve(identifier: String): com.sentinel.ai.core.sender.ContactResolution {
                return com.sentinel.ai.core.sender.ContactResolution.noMatch(identifier)
            }
        }

        val presentation = com.sentinel.ai.ui.util.resolveSenderPresentation(
            senderDisplayName = null,
            senderIdentifier = "+9999999999",
            contactResolver = fakeResolver
        )

        assertEquals("Unknown Contact", presentation.primaryText)
        assertEquals("+9999999999", presentation.secondaryText)
    }

    @Test
    fun `resolveSenderPresentation uses display name when identifier is null`() {
        val presentation = com.sentinel.ai.ui.util.resolveSenderPresentation(
            senderDisplayName = "HDFC Bank",
            senderIdentifier = null,
            contactResolver = null
        )

        assertEquals("HDFC Bank", presentation.primaryText)
        assertEquals(null, presentation.secondaryText)
    }

    private fun scanResult(
        target: String?,
        source: String,
        senderDisplayName: String?,
        senderIdentifier: String?
    ) = ScanResult(
        id = "test-id-${System.nanoTime()}",
        source = source,
        target = target,
        senderDisplayName = senderDisplayName,
        senderIdentifier = senderIdentifier,
        riskLevel = RiskLevel.GREEN,
        riskScore = 10f,
        explanation = "Test explanation",
        timestamp = 1_700_000_000_000L
    )
}
