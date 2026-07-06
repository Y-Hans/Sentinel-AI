package com.sentinel.ai.protection.intent.heuristic.rules.link

import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicConfig
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRule
import com.sentinel.ai.protection.intent.heuristic.RuleCategory
import com.sentinel.ai.protection.intent.heuristic.RuleResult
import java.net.URI

class SuspiciousTldRule : LinkHeuristicRule {
    override val id: String = "suspicious_tld"
    override val name: String = "Suspicious TLD"

    override fun evaluate(url: String, uri: URI?, config: LinkHeuristicConfig): RuleResult {
        val host = uri?.host?.lowercase() ?: ""
        val weight = config.weights[id] ?: 0f
        
        val matchedTld = config.suspiciousTlds.firstOrNull { tld ->
            host.endsWith(".$tld")
        }
        val triggered = matchedTld != null
        
        return RuleResult(
            triggered = triggered,
            scoreContribution = if (triggered) weight else 0f,
            explanation = if (triggered) "Uses .$matchedTld domain" else null,
            category = RuleCategory.DOMAIN
        )
    }
}
