package com.sentinel.ai.protection.intent.heuristic.rules.link

import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicConfig
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRule
import com.sentinel.ai.protection.intent.heuristic.RuleCategory
import com.sentinel.ai.protection.intent.heuristic.RuleResult
import java.net.URI

class ExcessiveQueryParametersRule : LinkHeuristicRule {
    override val id: String = "excessive_query"
    override val name: String = "Excessive Query Parameters"

    override fun evaluate(url: String, uri: URI?, config: LinkHeuristicConfig): RuleResult {
        val count = uri?.rawQuery.orEmpty().split('&').count { it.isNotBlank() }
        val triggered = count > config.queryParamsThreshold
        return RuleResult(
            triggered = triggered,
            scoreContribution = if (triggered) config.weights[id] ?: 0f else 0f,
            explanation = if (triggered) "URL has excessive query parameters" else null,
            category = RuleCategory.URL_STRUCTURE
        )
    }
}
