package com.sentinel.ai.protection.intent.file

import android.net.Uri

/**
 * Minimal file type detector stub for future intent protection workflows.
 */
object FileTypeDetector {

    fun detect(uri: Uri): FileType = FileType.UNKNOWN
}

/**
 * File type categories reserved for future detection logic.
 */
enum class FileType {
    UNKNOWN,
}
