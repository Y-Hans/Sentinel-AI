package com.sentinel.ai.protection.accessibility

import android.view.accessibility.AccessibilityEvent

/**
 * Lightweight helpers for extracting URL-bearing text from accessibility events.
 *
 * Only the event payload and the event source itself are read. The source's descendants are
 * deliberately not traversed so frequent content-change events remain inexpensive.
 */
internal object AccessibilityUtils {

    const val DEDUPLICATION_WINDOW_MS = 5_000L
    const val MAX_RECENT_URLS = 50

    private val urlPattern = Regex(
        pattern = """https?://[\w.-]+\.[a-z]{2,}(/[^\s]*)?""",
        option = RegexOption.IGNORE_CASE
    )

    fun extractText(event: AccessibilityEvent): String {
        val textParts = linkedSetOf<String>()

        event.text
            ?.asSequence()
            ?.map(CharSequence::toString)
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.forEach(textParts::add)

        event.source?.let { source ->
            source.text
                ?.toString()
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let(textParts::add)

            source.contentDescription
                ?.toString()
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let(textParts::add)
        }

        return textParts.joinToString(separator = " ")
    }

    fun extractUrls(text: String): List<String> =
        urlPattern.findAll(text)
            .map(MatchResult::value)
            .distinct()
            .toList()

    /**
     * Records an observation and returns true only when the URL has not been seen during the
     * deduplication window. Removing and reinserting preserves least-recently-seen ordering.
     */
    fun shouldScanUrl(
        recentUrls: LinkedHashMap<String, Long>,
        url: String,
        observedAtMs: Long
    ): Boolean {
        val lastSeenAtMs = recentUrls.remove(url)
        recentUrls[url] = observedAtMs

        while (recentUrls.size > MAX_RECENT_URLS) {
            val oldestUrl = recentUrls.entries.firstOrNull()?.key ?: break
            recentUrls.remove(oldestUrl)
        }

        return lastSeenAtMs == null ||
            observedAtMs - lastSeenAtMs >= DEDUPLICATION_WINDOW_MS
    }
}
