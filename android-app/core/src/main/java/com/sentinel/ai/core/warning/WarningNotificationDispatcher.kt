package com.sentinel.ai.core.warning

import com.sentinel.ai.core.model.ScanResult

/**
 * Dispatches system-level warning notifications for elevated threats.
 */
interface WarningNotificationDispatcher {
    fun showWarning(result: ScanResult, highPriority: Boolean = false)
}
