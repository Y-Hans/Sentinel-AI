package com.sentinel.ai.core.analyzer

import com.sentinel.ai.core.evidence.ThreatEvidence

/**
 * Encapsulates the output of an analysis run, containing all extracted evidence
 * and optional diagnostic metadata.
 */
data class AnalysisResult(
    val evidence: List<ThreatEvidence> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
) {
    val hasEvidence: Boolean get() = evidence.isNotEmpty()
}

/**
 * Domain interface for independent signal analyzers.
 */
interface ThreatAnalyzer<in TInput> {
    val analyzerName: String
    suspend fun analyze(input: TInput): AnalysisResult
}
