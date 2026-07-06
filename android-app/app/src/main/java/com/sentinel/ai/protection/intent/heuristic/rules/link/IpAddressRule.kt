package com.sentinel.ai.protection.intent.heuristic.rules.link

import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicConfig
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRule
import com.sentinel.ai.protection.intent.heuristic.RuleCategory
import com.sentinel.ai.protection.intent.heuristic.RuleResult
import java.net.URI

class IpAddressRule : LinkHeuristicRule {
    override val id: String = "ip_address"
    override val name: String = "IP Address Host"

    private val ipv4Regex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
    private val ipv6Regex = Regex("""^\[?[0-9a-fA-F:]+]?$""")

    override fun evaluate(url: String, uri: URI?, config: LinkHeuristicConfig): RuleResult {
        val host = uri?.host ?: ""
        val weight = config.weights[id] ?: 0f
        
        val triggered = ipv4Regex.matches(host) || (host.contains(":") && ipv6Regex.matches(host))
        
        return RuleResult(
            triggered = triggered,
            scoreContribution = if (triggered) weight else 0f,
            explanation = if (triggered) "Uses IP address instead of domain" else null,
            category = RuleCategory.DOMAIN
        )
    }
}
