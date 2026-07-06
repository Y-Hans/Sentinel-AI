package com.sentinel.ai.protection.intent.heuristic

import java.net.URI

interface LinkHeuristicRule {
    val id: String
    val name: String
    fun evaluate(url: String, uri: URI?, config: LinkHeuristicConfig): RuleResult
}
