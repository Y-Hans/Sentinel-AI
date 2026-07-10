package com.sentinel.ai.protection.intent.heuristic.rules.link

import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicConfig
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRule
import com.sentinel.ai.protection.intent.heuristic.RuleCategory
import com.sentinel.ai.protection.intent.heuristic.RuleResult
import com.sentinel.ai.protection.intent.link.ParsedUrl

class PunycodeRule : LinkHeuristicRule {
    override val id: String = "punycode"
    override val name: String = "Punycode Detection"

    override fun evaluate(url: ParsedUrl, config: LinkHeuristicConfig): RuleResult {
        val weight = config.weights[id] ?: 0f
        
        val triggered = url.isPunycode
        
        return RuleResult(
            triggered = triggered,
            scoreContribution = if (triggered) weight else 0f,
            explanation = if (triggered) "Punycode domain name detected" else null,
            category = RuleCategory.DOMAIN
        )
    }
}
