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
        val host = uri?.host?.lowercase() ?: ""
        val matchedKeyword = config.socialEngineeringKeywords.firstOrNull { keyword ->
            url.lowercase().replace('_', '-').contains(keyword)
        }

        if (matchedKeyword == null) {
            return RuleResult(
                triggered = false,
                scoreContribution = 0f,
                explanation = null,
                category = RuleCategory.SOCIAL_ENGINEERING
            )
        }

        // Check if the keyword is present in the host (domain/subdomain)
        val inHost = host.replace('_', '-').contains(matchedKeyword)
        
        val score = if (inHost) {
            config.weights[id] ?: 20f
        } else {
            // Match is in the path/query parameters, apply heavily discounted weight
            2f
        }

        val explanation = if (inHost) {
            "Uses social engineering keyword in domain: $matchedKeyword"
        } else {
            "Uses social engineering keyword in path/query: $matchedKeyword"
        }

        return RuleResult(
            triggered = true,
            scoreContribution = score,
            explanation = explanation,
            category = RuleCategory.SOCIAL_ENGINEERING
        )
    }
}
