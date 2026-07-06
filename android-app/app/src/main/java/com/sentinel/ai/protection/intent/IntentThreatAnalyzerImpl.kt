package com.sentinel.ai.protection.intent

import com.sentinel.ai.core.event.ThreatEvent
import com.sentinel.ai.core.event.ThreatEventBus
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.protection.intent.file.FileScanner
import com.sentinel.ai.protection.intent.link.LinkScanner
import com.sentinel.ai.protection.intent.model.FilePayload
import com.sentinel.ai.protection.intent.model.IntentPayload
import com.sentinel.ai.protection.intent.model.UrlPayload
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of IntentThreatAnalyzer that coordinates scanning of links and files,
 * and integrates with the existing Threat Engine.
 */
@Singleton
class IntentThreatAnalyzerImpl @Inject constructor(
    private val linkScanner: LinkScanner,
    private val fileScanner: FileScanner,
    private val threatEventBus: ThreatEventBus
) : IntentThreatAnalyzer {

    override suspend fun analyze(payload: IntentPayload): ScanResult {
        return when (payload) {
            is UrlPayload -> {
                val result = linkScanner.scan(payload.url)
                threatEventBus.emit(ThreatEvent.LinkThreatDetected(result))
                result
            }
            is FilePayload -> {
                val result = fileScanner.scan(payload.uri)
                threatEventBus.emit(ThreatEvent.FileThreatDetected(result))
                result
            }
        }
    }
}
