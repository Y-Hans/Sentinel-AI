package com.sentinel.ai.protection.intent.link

import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.protection.intent.heuristic.LinkHeuristicRiskEngine
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

    override suspend fun scan(url: String): ScanResult {
        return riskEngine.toScanResult(url)
    }
}
