package com.sentinel.ai.protection.intent.heuristic.rules.link

import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicConfig
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRule
import com.sentinel.ai.protection.intent.heuristic.RuleCategory
import com.sentinel.ai.protection.intent.heuristic.RuleResult
import java.net.URI

class RedirectParameterRule : LinkHeuristicRule {
    override val id: String = "suspicious_redirect"
    override val name: String = "Redirect Parameter"

    override fun evaluate(url: String, uri: URI?, config: LinkHeuristicConfig): RuleResult {
        val query = uri?.rawQuery.orEmpty()
        val triggered = query.split('&').any { part ->
            val name = part.substringBefore('=', "").lowercase()
            val value = part.substringAfter('=', "")
            name in config.redirectParameters && (value.contains("http%3a", true) || value.contains("https%3a", true) || value.contains("http", true))
        }
        return RuleResult(
            triggered = triggered,
            scoreContribution = if (triggered) config.weights[id] ?: 0f else 0f,
            explanation = if (triggered) "URL contains a redirect parameter placeholder signal" else null,
            category = RuleCategory.URL_STRUCTURE
        )
    }
}
