package com.sentinel.ai.protection.intent

import android.net.Uri
import com.sentinel.ai.core.data.ScanRepository
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.protection.intent.model.FilePayload
import com.sentinel.ai.protection.intent.model.UrlPayload
import javax.inject.Inject
import javax.inject.Singleton

/** Adapts the existing intent analyzer for UI-initiated scans. */
@Singleton
class IntentScanRepository @Inject constructor(
    private val analyzer: IntentThreatAnalyzer
) : ScanRepository {
    override suspend fun scanLink(link: String): ScanResult = analyzer.analyze(UrlPayload(link))

    override suspend fun scanFile(uri: Uri): ScanResult = analyzer.analyze(FilePayload(uri))
}
