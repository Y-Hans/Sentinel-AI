package com.sentinel.ai.agents.whatsapp

import com.sentinel.ai.core.event.schema.ScamRiskLevel
import com.sentinel.ai.core.event.schema.UrlAnalysisItem

object ScamRuleEngine {

    fun evaluate(
        messageText: String,
        urls: List<UrlAnalysisItem>,
        isKnownContact: Boolean
    ): ScamRuleResult {
        val explanations = mutableListOf<String>()
        var score = 0

        urls.forEach { url ->
            if (url.isShortened) {
                score += 20
                explanations += "Shortened URL detected: ${url.domain}"
            }
            if (url.isIpAddressUrl) {
                score += 25
                explanations += "Raw IP address URL detected: ${url.rawUrl}"
            }
        }

        val lower = messageText.lowercase()
        score += addSignal(lower, "urgent", 20, "Urgency language detected", explanations)
        score += addSignal(lower, "verify", 15, "Sensitive request pattern detected", explanations)
        score += addSignal(lower, "account", 10, "Account-related language detected", explanations)
        score += addSignal(lower, "offer", 15, "Promotional offer language detected", explanations)
        score += addSignal(lower, "otp", 25, "Sensitive request pattern detected", explanations)
        score += addMatchedTerms(lower, URGENCY_TERMS, 12, explanations) { "Urgency language detected: $it" }
        score += addMatchedTerms(lower, FINANCIAL_TERMS, 10, explanations) { "Financial language detected: $it" }
        score += addMatchedTerms(lower, CREDENTIAL_TERMS, 15, explanations) { "Credential harvesting indicator detected: $it" }

        if (!isKnownContact) {
            score += 10
            explanations += "Sender is not a known contact"
        }

        val capped = score.coerceIn(0, 100)
        return ScamRuleResult(
            riskScore = capped,
            riskLevel = when (capped) {
                in 0..20 -> ScamRiskLevel.LOW
                in 21..50 -> ScamRiskLevel.MEDIUM
                in 51..69 -> ScamRiskLevel.HIGH
                else -> ScamRiskLevel.CRITICAL
            },
            explanations = explanations
        )
    }

    private fun addSignal(
        lower: String,
        term: String,
        points: Int,
        explanation: String,
        explanations: MutableList<String>
    ): Int {
        if (!lower.contains(term)) return 0
        explanations += explanation
        return points
    }

    private fun addMatchedTerms(
        lower: String,
        terms: List<String>,
        points: Int,
        explanations: MutableList<String>,
        explanationFactory: (String) -> String
    ): Int {
        terms.firstOrNull { lower.contains(it) }?.let {
            explanations += explanationFactory(it)
            return points
        }
        return 0
    }

    private val URGENCY_TERMS = listOf(
        "immediately",
        "act now",
        "account suspended",
        "account blocked"
    )

    private val FINANCIAL_TERMS = listOf(
        "bank",
        "payment",
        "refund",
        "wallet",
        "upi",
        "transaction"
    )

    private val CREDENTIAL_TERMS = listOf(
        "login",
        "password",
        "verification code",
        "security code"
    )
}
