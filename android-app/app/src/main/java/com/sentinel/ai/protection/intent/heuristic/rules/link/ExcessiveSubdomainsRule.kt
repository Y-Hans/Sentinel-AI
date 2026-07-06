package com.sentinel.ai.protection.intent.heuristic.rules.link

import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicConfig
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRule
import com.sentinel.ai.protection.intent.heuristic.RuleCategory
import com.sentinel.ai.protection.intent.heuristic.RuleResult
import java.net.URI

class ExcessiveSubdomainsRule : LinkHeuristicRule {
    override val id: String = "excessive_subdomains"
    override val name: String = "Excessive Subdomains"

    override fun evaluate(url: String, uri: URI?, config: LinkHeuristicConfig): RuleResult {
        val host = uri?.host ?: ""
        val weight = config.weights[id] ?: 0f
        
        val dotCount = host.count { it == '.' }
        val triggered = dotCount > config.subdomainThreshold
        
        return RuleResult(
            triggered = triggered,
            scoreContribution = if (triggered) weight else 0f,
            explanation = if (triggered) "Contains excessive subdomains" else null,
            category = RuleCategory.DOMAIN
        )
    }
}
