package com.sentinel.ai.protection.intent

import android.net.Uri
import android.util.Log
import com.sentinel.ai.core.data.ScanRepository
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.protection.intent.link.UrlNormalizer
import com.sentinel.ai.protection.intent.model.FilePayload
import com.sentinel.ai.protection.intent.model.UrlPayload
import javax.inject.Inject
import javax.inject.Singleton

/** Adapts the existing intent analyzer for every scan entry point. */
@Singleton
class IntentScanRepository @Inject constructor(
    private val analyzer: IntentThreatAnalyzer
) : ScanRepository {

    override suspend fun scanLink(link: String): ScanResult {
        val payload = UrlPayload(link)
        val parsedUrl = UrlNormalizer.parse(link)
        Log.d(TAG, "payload.javaClass.name=${payload.javaClass.name}")
        Log.d(TAG, "payload.toString()=$payload")
        Log.d(
            TAG,
            "URL input=${payload.url}, scheme=${parsedUrl.scheme}, valid=${parsedUrl.isValid}"
        )

        return analyzer.analyze(payload)
    }

    override suspend fun scanFile(uri: Uri): ScanResult = analyzer.analyze(FilePayload(uri))

    private companion object {
        const val TAG = "ML_DEBUG"
    }
}
