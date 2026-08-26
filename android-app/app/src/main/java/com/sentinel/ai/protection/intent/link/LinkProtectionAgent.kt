package com.sentinel.ai.protection.intent.link

import com.sentinel.ai.core.model.LocalEvidence
import com.sentinel.ai.core.model.LocalFinding
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.core.evidence.ThreatEvidence
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRiskEngine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * URL protection entry point for incoming link payloads.
 *
 * This class normalizes URLs, evaluates threat metrics, and returns a scan result.
 */
@Singleton
class LinkProtectionAgent @Inject constructor(
    private val riskEngine: LinkHeuristicRiskEngine
) : LinkScanner {

    private val ruleMetadata = LinkHeuristicRiskEngine.defaultRules().map { rule ->
        rule.id to rule.name
    }

    override suspend fun scan(url: String): List<ThreatEvidence> {
        val analysis = riskEngine.analyze(url)

        return listOf(
            ThreatEvidence(
                category = com.sentinel.ai.core.evidence.EvidenceCategory.URL_HEURISTIC,
                type = com.sentinel.ai.core.evidence.EvidenceType.SUSPICIOUS_LINK,
                severity = when (analysis.riskLevel) {
                    com.sentinel.ai.core.model.RiskLevel.CRITICAL -> com.sentinel.ai.core.evidence.EvidenceSeverity.CRITICAL
                    com.sentinel.ai.core.model.RiskLevel.RED -> com.sentinel.ai.core.evidence.EvidenceSeverity.HIGH
                    com.sentinel.ai.core.model.RiskLevel.YELLOW -> com.sentinel.ai.core.evidence.EvidenceSeverity.MEDIUM
                    com.sentinel.ai.core.model.RiskLevel.GREEN -> com.sentinel.ai.core.evidence.EvidenceSeverity.LOW
                },
                sourceName = "LinkHeuristicRiskEngine",
                confidence = 0.9f,
                indicatorText = "Link Heuristics",
                explanation = analysis.explanation,
                metadata = mapOf("score" to analysis.score.toString())
            )
        )
    }
}
