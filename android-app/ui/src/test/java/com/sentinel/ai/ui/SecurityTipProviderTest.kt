package com.sentinel.ai.ui

import com.sentinel.ai.ui.components.DefaultSecurityTipProvider
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityTipProviderTest {
    @Test
    fun `returns a non-empty security tip`() {
        val provider = DefaultSecurityTipProvider()
        assertTrue(provider.getRandomTip().isNotBlank())
    }

    @Test
    fun `randomized provider returns stable safe content`() {
        val provider = DefaultSecurityTipProvider()
        repeat(20) {
            val tip = provider.getRandomTip()
            assertTrue(tip.contains("OTP") || tip.contains("domain") || tip.contains("phishing") || tip.contains("UPI") || tip.contains("APK") || tip.contains("payment") || tip.contains("sender"))
        }
    }
}
