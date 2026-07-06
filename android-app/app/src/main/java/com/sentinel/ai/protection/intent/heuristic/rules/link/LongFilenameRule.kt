package com.sentinel.ai.protection.intent.heuristic.rules.link

import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicConfig
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRule
import com.sentinel.ai.protection.intent.heuristic.RuleCategory
import com.sentinel.ai.protection.intent.heuristic.RuleResult
import java.net.URI

class LongFilenameRule : LinkHeuristicRule {
    override val id: String = "long_filename"
    override val name: String = "Long Filename"

    override fun evaluate(url: String, uri: URI?, config: LinkHeuristicConfig): RuleResult {
        val filename = uri?.path.orEmpty().substringAfterLast('/', "")
        val triggered = filename.length > config.filenameLengthThreshold
        return RuleResult(
            triggered = triggered,
            scoreContribution = if (triggered) config.weights[id] ?: 0f else 0f,
            explanation = if (triggered) "URL contains an unusually long filename" else null,
            category = RuleCategory.URL_STRUCTURE
        )
    }
}
