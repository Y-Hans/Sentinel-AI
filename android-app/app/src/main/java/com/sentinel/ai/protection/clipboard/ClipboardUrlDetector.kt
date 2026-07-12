package com.sentinel.ai.protection.clipboard

import com.sentinel.ai.protection.intent.link.UrlNormalizer

internal object ClipboardUrlDetector {
    private val webUrl = Regex("https?://[^\\s<>\\\"]+", RegexOption.IGNORE_CASE)

    fun firstValidUrl(text: String): String? = webUrl.find(text)?.value
        ?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
        ?.takeIf { candidate ->
            val parsed = UrlNormalizer.parse(candidate)
            parsed.isValid && (parsed.scheme == "http" || parsed.scheme == "https")
        }
        ?.let(UrlNormalizer::normalize)
}
