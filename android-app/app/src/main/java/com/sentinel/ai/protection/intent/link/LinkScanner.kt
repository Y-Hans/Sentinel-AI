package com.sentinel.ai.protection.intent.link

import com.sentinel.ai.core.evidence.ThreatEvidence

/**
 * Reusable interface for scanning web URLs.
 */
interface LinkScanner {
    suspend fun scan(url: String): List<ThreatEvidence>
}
