package com.sentinel.ai.protection.intent.heuristic.rules.link

import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicConfig
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRule
import com.sentinel.ai.protection.intent.heuristic.RuleCategory
import com.sentinel.ai.protection.intent.heuristic.RuleResult
import java.net.URI

class TrackingParameterRule : LinkHeuristicRule {
    override val id: String = "tracking_parameters"
    override val name: String = "Tracking Parameters"

    override fun evaluate(url: String, uri: URI?, config: LinkHeuristicConfig): RuleResult {
        val names = queryParameterNames(uri)
        val triggered = names.any { name ->
            name in config.trackingParameters || config.trackingParameterPrefixes.any { name.startsWith(it) }
        }
        return RuleResult(
            triggered = triggered,
            scoreContribution = if (triggered) config.weights[id] ?: 0f else 0f,
            explanation = if (triggered) "URL contains tracking parameters" else null,
            category = RuleCategory.URL_STRUCTURE
        )
    }

    private fun queryParameterNames(uri: URI?): List<String> {
        return uri?.rawQuery.orEmpty()
            .split('&')
            .mapNotNull { it.substringBefore('=', "").lowercase().takeIf(String::isNotBlank) }
    }
}
