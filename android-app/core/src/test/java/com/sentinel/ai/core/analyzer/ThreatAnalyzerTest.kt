package com.sentinel.ai.core.analyzer

import com.sentinel.ai.core.evidence.EvidenceCategory
import com.sentinel.ai.core.evidence.EvidenceSeverity
import com.sentinel.ai.core.evidence.EvidenceType
import com.sentinel.ai.core.evidence.ThreatEvidence
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreatAnalyzerTest {

    @Test
    fun `AnalysisResult defaults to empty evidence and hasEvidence false`() {
        val result = AnalysisResult()

        assertTrue(result.evidence.isEmpty())
        assertTrue(result.metadata.isEmpty())
        assertFalse(result.hasEvidence)
    }

    @Test
    fun `AnalysisResult with evidence correctly reports hasEvidence true`() {
        val evidenceItem = ThreatEvidence(
            category = EvidenceCategory.URL_HEURISTIC,
            type = EvidenceType.SUSPICIOUS_LINK,
            severity = EvidenceSeverity.HIGH,
            sourceName = "LinkHeuristicEngine",
            indicatorText = "Suspicious Link",
            explanation = "URL matched suspicious heuristics"
        )
        val result = AnalysisResult(
            evidence = listOf(evidenceItem),
            metadata = mapOf("inputUrl" to "https://malicious.example")
        )

        assertEquals(1, result.evidence.size)
        assertTrue(result.hasEvidence)
        assertEquals("https://malicious.example", result.metadata["inputUrl"])
    }

    @Test
    fun `ThreatAnalyzer implementation executes analysis asynchronously`() = runTest {
        val sampleAnalyzer = object : ThreatAnalyzer<String> {
            override val analyzerName: String = "TestStringAnalyzer"

            override suspend fun analyze(input: String): AnalysisResult {
                if (input.isBlank()) {
                    return AnalysisResult()
                }
                return AnalysisResult(
                    evidence = listOf(
                        ThreatEvidence(
                            category = EvidenceCategory.MESSAGE_CONTENT,
                            type = EvidenceType.GENERIC_SUSPICIOUS_PATTERN,
                            severity = EvidenceSeverity.LOW,
                            sourceName = analyzerName,
                            indicatorText = "Text Pattern",
                            explanation = "Analyzed text: $input"
                        )
                    )
                )
            }
        }

        assertEquals("TestStringAnalyzer", sampleAnalyzer.analyzerName)

        val emptyResult = sampleAnalyzer.analyze("")
        assertFalse(emptyResult.hasEvidence)

        val contentResult = sampleAnalyzer.analyze("sample input text")
        assertTrue(contentResult.hasEvidence)
        assertEquals(1, contentResult.evidence.size)
        assertEquals("Analyzed text: sample input text", contentResult.evidence.first().explanation)
    }
}
