package com.sentinel.ai.protection.intent.heuristic.rules.link

import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicConfig
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRule
import com.sentinel.ai.protection.intent.heuristic.RuleCategory
import com.sentinel.ai.protection.intent.heuristic.RuleResult
import java.net.URI

class BrandImpersonationRule : LinkHeuristicRule {
    override val id: String = "brand_impersonation"
    override val name: String = "Brand Impersonation"

    override fun evaluate(url: String, uri: URI?, config: LinkHeuristicConfig): RuleResult {
        val host = uri?.host?.removePrefix("www.")?.lowercase().orEmpty()
        if (host.isBlank()) {
            return clean()
        }

        config.brandOfficialDomains.forEach { (brand, officialDomains) ->
            val official = officialDomains.any { host == it || host.endsWith(".$it") }
            if (!official && host.contains(brand)) {
                val weight = if (hasExtraWord(host, brand, config)) {
                    config.weights["brand_impersonation"] ?: 0f
                } else {
                    config.weights["brand_lookalike"] ?: 0f
                }
                return RuleResult(
                    triggered = true,
                    scoreContribution = weight,
                    explanation = "Possible $brand brand impersonation",
                    category = RuleCategory.BRAND_IMPERSONATION
                )
            }

            if (!official && looksLikeBrand(host, brand, config)) {
                return RuleResult(
                    triggered = true,
                    scoreContribution = config.weights["brand_lookalike"] ?: 0f,
                    explanation = "Possible $brand look-alike domain",
                    category = RuleCategory.BRAND_IMPERSONATION
                )
            }
        }

        return clean()
    }

    private fun hasExtraWord(host: String, brand: String, config: LinkHeuristicConfig): Boolean {
        return config.brandExtraWords.any { word -> host.contains("$brand-$word") || host.contains("$word-$brand") || host.contains("$brand$word") }
    }

    private fun looksLikeBrand(host: String, brand: String, config: LinkHeuristicConfig): Boolean {
        val label = host.substringBefore('.')
        if (kotlin.math.abs(label.length - brand.length) > 1) return false

        var differences = 0
        label.zip(brand).forEach { (actual, expected) ->
            if (actual != expected && actual !in config.lookAlikeReplacements[expected].orEmpty()) {
                differences++
            }
        }
        differences += kotlin.math.abs(label.length - brand.length)
        return differences in 1..2
    }

    private fun clean(): RuleResult = RuleResult(
        triggered = false,
        scoreContribution = 0f,
        explanation = null,
        category = RuleCategory.BRAND_IMPERSONATION
    )
}
