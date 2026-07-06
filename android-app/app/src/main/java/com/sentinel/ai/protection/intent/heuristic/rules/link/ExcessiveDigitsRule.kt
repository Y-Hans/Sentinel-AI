package com.sentinel.ai.protection.intent.heuristic.rules.link

import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicConfig
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRule
import com.sentinel.ai.protection.intent.heuristic.RuleCategory
import com.sentinel.ai.protection.intent.heuristic.RuleResult
import java.net.URI

class ExcessiveDigitsRule : LinkHeuristicRule {
    override val id: String = "excessive_digits"
    override val name: String = "Excessive Digits"

    override fun evaluate(url: String, uri: URI?, config: LinkHeuristicConfig): RuleResult {
        val host = uri?.host ?: ""
        val weight = config.weights[id] ?: 0f
        
        val digitCount = host.count { it.isDigit() }
        val letterCount = host.count { it.isLetter() }
        val ratio = if (letterCount > 0) digitCount.toDouble() / letterCount else 0.0
        
        val triggered = digitCount > 5 || (digitCount > 0 && ratio > config.digitRatioThreshold)
        
        return RuleResult(
            triggered = triggered,
            scoreContribution = if (triggered) weight else 0f,
            explanation = if (triggered) "Contains excessive digits in the domain" else null,
            category = RuleCategory.DOMAIN
        )
    }
}
