package com.sentinel.ai.protection.intent.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.protection.intent.heuristic.FileHeuristicRiskEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File protection entry point for incoming file payloads.
 *
 * This class detects the file type using the ContentResolver, extracts the actual
 * file name from the content provider, evaluates threat metrics, and returns a scan result.
 */
@Singleton
class FileProtectionAgent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val riskEngine: FileHeuristicRiskEngine
) : FileScanner {

    override suspend fun scan(uri: Uri): ScanResult {
        val contentResolver = context.contentResolver
        val fileType = FileTypeDetector.detect(uri, contentResolver)
        
        val filename = getDisplayName(uri) 
            ?: uri.lastPathSegment 
            ?: uri.path 
            ?: uri.toString()
            
        return riskEngine.toScanResult(filename, fileType.name)
    }

    private fun getDisplayName(uri: Uri): String? {
        if (uri.scheme?.lowercase() != "content") return null
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        }.getOrNull()
    }
}
