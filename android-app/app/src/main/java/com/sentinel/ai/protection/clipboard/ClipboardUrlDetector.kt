package com.sentinel.ai.protection.clipboard

import com.sentinel.ai.protection.intent.link.UrlNormalizer

internal object ClipboardUrlDetector {
    private val webUrl = Regex("https?://[^\\s<>\\\"]+", RegexOption.IGNORE_CASE)
    private val bareWebUrl = Regex(
        "^(?:www\\.)?(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}" +
            "(?::\\d{1,5})?(?:[/\\?#][^\\s<>\\\"]*)?$",
        RegexOption.IGNORE_CASE
    )

    fun firstValidUrl(text: String): String? {
        val trimmed = text.trim()
        val candidate = webUrl.find(trimmed)?.value
            ?: trimmed.takeIf(bareWebUrl::matches)?.let(::normalizeClipboard)
            ?: return null

        return candidate
        ?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
        ?.takeIf { candidate ->
            val parsed = UrlNormalizer.parse(candidate)
            parsed.isValid && (parsed.scheme == "http" || parsed.scheme == "https")
        }
        ?.let(UrlNormalizer::normalize)
    }

    private fun normalizeClipboard(text: String): String =
        if (text.startsWith("http://", ignoreCase = true) ||
            text.startsWith("https://", ignoreCase = true)
        ) {
            text
        } else {
            "https://$text"
        }
}
