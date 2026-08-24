package com.sentinel.ai.core.utils

import android.net.Uri

object UrlLogger {
    fun redactUrl(url: String?): String {
        if (url.isNullOrBlank()) return "[EMPTY_URL]"
        
        val withoutQuery = url.substringBefore('?').substringBefore('#')
        val hasRedaction = withoutQuery.length < url.length
        
        // Strip credentials from authority (e.g. user:pass@)
        val redactedAuth = withoutQuery.replace(Regex("://[^@/]+@"), "://")
        
        return if (hasRedaction) "$redactedAuth?[REDACTED]" else redactedAuth
    }
}
