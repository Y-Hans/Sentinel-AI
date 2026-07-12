package com.sentinel.ai.protection.intent.heuristic.rules.link

import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicConfig
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRule
import com.sentinel.ai.protection.intent.heuristic.RuleCategory
import com.sentinel.ai.protection.intent.heuristic.RuleResult
import com.sentinel.ai.protection.intent.link.ParsedUrl

class EmbeddedUrlRule : LinkHeuristicRule {
    override val id: String = "embedded_url"
    override val name: String = "Embedded URL"

    override fun evaluate(url: ParsedUrl, config: LinkHeuristicConfig): RuleResult {
        val pathContainsUrl = url.pathContainsEmbeddedHttpUrl
        val embeddedParameters = url.queryParameters.filter { it.containsEmbeddedHttpUrl }
        val triggered = pathContainsUrl || embeddedParameters.isNotEmpty()
        val onlyRedirectParameterEvidence = !pathContainsUrl && embeddedParameters.isNotEmpty() &&
            embeddedParameters.all {
                it.decodedName.lowercase() in config.redirectParameters &&
                    it.isPlausibleHttpDestination
            }

        return RuleResult(
            triggered = triggered,
            scoreContribution = if (triggered && !onlyRedirectParameterEvidence) config.weights[id] ?: 0f else 0f,
            explanation = if (triggered) "The URL embeds another destination URL" else null,
            category = RuleCategory.URL_STRUCTURE
        )
    }
}
