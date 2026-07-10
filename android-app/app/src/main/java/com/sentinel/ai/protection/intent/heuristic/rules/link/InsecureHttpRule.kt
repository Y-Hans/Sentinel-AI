package com.sentinel.ai.protection.intent.heuristic.rules.link

import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicConfig
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRule
import com.sentinel.ai.protection.intent.heuristic.RuleCategory
import com.sentinel.ai.protection.intent.heuristic.RuleResult
import com.sentinel.ai.protection.intent.link.ParsedUrl

class InsecureHttpRule : LinkHeuristicRule {
    override val id: String = "insecure_http"
    override val name: String = "Insecure HTTP"

    override fun evaluate(url: ParsedUrl, config: LinkHeuristicConfig): RuleResult {
        val triggered = url.originalHost != null && url.scheme.equals("http", ignoreCase = true)
        return RuleResult(
            triggered = triggered,
            scoreContribution = if (triggered) config.weights[id] ?: 0f else 0f,
            explanation = if (triggered) "The URL uses unencrypted HTTP" else null,
            category = RuleCategory.URL_STRUCTURE
        )
    }
}
