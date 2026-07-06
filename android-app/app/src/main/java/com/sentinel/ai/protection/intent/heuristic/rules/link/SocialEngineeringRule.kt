package com.sentinel.ai.protection.intent.heuristic.rules.link

import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicConfig
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRule
import com.sentinel.ai.protection.intent.heuristic.RuleCategory
import com.sentinel.ai.protection.intent.heuristic.RuleResult
import java.net.URI

class SocialEngineeringRule : LinkHeuristicRule {
    override val id: String = "social_engineering"
    override val name: String = "Social Engineering Keywords"

    override fun evaluate(url: String, uri: URI?, config: LinkHeuristicConfig): RuleResult {
        val normalized = url.lowercase().replace('_', '-')
        val matched = config.socialEngineeringKeywords.firstOrNull { keyword ->
            normalized.contains(keyword)
        }
        val triggered = matched != null
        return RuleResult(
            triggered = triggered,
            scoreContribution = if (triggered) config.weights[id] ?: 0f else 0f,
            explanation = if (triggered) "Uses social engineering keyword: $matched" else null,
            category = RuleCategory.SOCIAL_ENGINEERING
        )
    }
}
