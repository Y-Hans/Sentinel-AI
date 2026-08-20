package com.sentinel.ai.protection.intent.reputation

import com.sentinel.ai.core.coroutines.DispatcherProvider
import com.sentinel.ai.core.data.local.UrlReputationDao
import com.sentinel.ai.core.data.local.UrlReputationEntity
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.core.model.EvidenceSourceStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Singleton
class ReputationManagerImpl @Inject constructor(
    private val dao: UrlReputationDao,
    private val combiner: EvidenceCombiner,
    private val dispatcherProvider: DispatcherProvider
) : ReputationManager {

    override suspend fun enrich(
        heuristicResult: ScanResult,
        mlScore: Float?,
        target: ReputationTarget?
    ): ScanResult {
        if (target !is ReputationTarget.Url) {
            // For files or other targets, just pass through EvidenceCombiner
            return combiner.combine(heuristicResult, mlScore, emptyList())
        }

        val url = target.url
        val domain = extractDomain(url)

        val evidenceList = mutableListOf<ReputationEvidence>()

        val exactMatch = dao.getReputation(url, "URL")
        if (exactMatch != null) {
            evidenceList.add(exactMatch.toEvidence("Local URL Reputation"))
        }

        if (domain != null) {
            val domainMatch = dao.getReputation(domain, "DOMAIN")
            if (domainMatch != null) {
                evidenceList.add(domainMatch.toEvidence("Local Domain Reputation"))
            }
        }

        val finalResult = combiner.combine(heuristicResult, mlScore, evidenceList)

        // Store updated reputation
        val newVerdict = when (finalResult.decision) {
            com.sentinel.ai.core.model.ProtectionDecision.ALLOW -> "CLEAN"
            com.sentinel.ai.core.model.ProtectionDecision.WARN -> "SUSPICIOUS"
            com.sentinel.ai.core.model.ProtectionDecision.BLOCK -> "MALICIOUS"
        }
        val now = System.currentTimeMillis()
        val firstSeen = exactMatch?.firstSeenTimestamp ?: now
        val scanCount = (exactMatch?.scanCount ?: 0) + 1

        val entity = UrlReputationEntity(
            target = url,
            type = "URL",
            verdict = newVerdict,
            confidence = finalResult.confidence,
            firstSeenTimestamp = firstSeen,
            lastSeenTimestamp = now,
            scanCount = scanCount,
            latestHeuristicScore = heuristicResult.riskScore,
            latestMlScore = mlScore ?: 0f,
            latestFinalScore = finalResult.riskScore,
            reasons = finalResult.explanation
        )
        dao.upsertReputation(entity)

        return finalResult
    }

    private fun extractDomain(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.host
        } catch (e: Exception) {
            null
        }
    }

    private fun UrlReputationEntity.toEvidence(providerName: String): ReputationEvidence {
        val verdict = try {
            ReputationVerdict.valueOf(this.verdict)
        } catch (e: Exception) {
            ReputationVerdict.UNKNOWN
        }

        val result = ReputationResult(
            providerName = providerName,
            confidence = this.confidence,
            reputation = verdict,
            reason = this.reasons,
            timestamp = this.lastSeenTimestamp
        )

        return ReputationEvidence.completed(result)
    }
}
