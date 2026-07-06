package com.sentinel.ai.protection.intent.heuristic.rules.link

import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicConfig
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRule
import com.sentinel.ai.protection.intent.heuristic.RuleCategory
import com.sentinel.ai.protection.intent.heuristic.RuleResult
import java.net.URI

class PunycodeRule : LinkHeuristicRule {
    override val id: String = "punycode"
    override val name: String = "Punycode Detection"

    override fun evaluate(url: String, uri: URI?, config: LinkHeuristicConfig): RuleResult {
        val host = uri?.host?.lowercase() ?: ""
        val weight = config.weights[id] ?: 0f
        
        val triggered = host.startsWith("xn--") || host.contains(".xn--")
        
        return RuleResult(
            triggered = triggered,
            scoreContribution = if (triggered) weight else 0f,
            explanation = if (triggered) "Punycode domain name detected" else null,
            category = RuleCategory.DOMAIN
        )
    }
}
