package com.sentinel.ai.core.data

import android.net.Uri
import com.sentinel.ai.core.model.ScanResult

/** Entry point used by UI clients to run the app's protection scan pipeline. */
interface ScanRepository {
    suspend fun scanLink(link: String): ScanResult
    suspend fun scanFile(uri: Uri): ScanResult
}
