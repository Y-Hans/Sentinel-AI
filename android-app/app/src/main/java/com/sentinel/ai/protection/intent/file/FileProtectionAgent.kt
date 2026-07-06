package com.sentinel.ai.protection.intent.file

import android.net.Uri
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.protection.intent.heuristic.FileHeuristicRiskEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File protection entry point for incoming file payloads.
 *
 * This class detects the file type, evaluates threat metrics, and returns a scan result.
 */
@Singleton
class FileProtectionAgent @Inject constructor(
    private val riskEngine: FileHeuristicRiskEngine
) : FileScanner {

    override suspend fun scan(uri: Uri): ScanResult {
        val fileType = FileTypeDetector.detect(uri)
        val filename = uri.lastPathSegment ?: uri.path ?: uri.toString()
        return riskEngine.toScanResult(filename, fileType.name)
    }
}
