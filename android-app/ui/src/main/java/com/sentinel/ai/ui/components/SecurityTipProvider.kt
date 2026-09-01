package com.sentinel.ai.ui.components

import javax.inject.Inject
import javax.inject.Singleton

interface SecurityTipProvider {
    fun getRandomTip(): String
}

@Singleton
class DefaultSecurityTipProvider @Inject constructor() : SecurityTipProvider {
    // We maintain an index to avoid repeating the same tip immediately, if possible, 
    // or we just return a random one. For simplicity, we just pick randomly.
    private val tips = listOf(
        "Never share OTPs with anyone.",
        "Check domains carefully before entering credentials.",
        "Urgency is commonly used in phishing attacks.",
        "Never share UPI PINs to receive money.",
        "Be careful with unexpected APK files.",
        "Verify payment or refund requests independently.",
        "A familiar sender account can still be compromised."
    )

    override fun getRandomTip(): String {
        return tips.random()
    }
}
