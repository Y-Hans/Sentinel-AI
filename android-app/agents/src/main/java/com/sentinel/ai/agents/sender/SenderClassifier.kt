package com.sentinel.ai.agents.sender

import com.sentinel.ai.core.sender.SenderProfile
import com.sentinel.ai.core.sender.SenderType
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic classifier that maps raw sender identifiers to [SenderProfile]
 * and [SenderType] categorizations.
 *
 * Primary responsibility:
 * rawIdentifier -> normalize -> classify -> SenderProfile
 *
 * Classification is contextual evidence only, NOT a safety verdict.
 * This classifier is independent of contact resolution, threat detection, and risk scoring.
 */
@Singleton
class SenderClassifier @Inject constructor() {

    /**
     * Normalizes a raw sender identifier for consistent classification and comparison,
     * without overwriting or destroying the original raw identifier.
     */
    fun normalize(rawIdentifier: String?): String {
        if (rawIdentifier.isNullOrBlank()) return ""
        val trimmed = rawIdentifier.trim()

        return when {
            isPhoneNumber(trimmed) -> normalizePhoneNumber(trimmed)
            isEmail(trimmed) -> trimmed.lowercase(Locale.ROOT)
            isSmsHeader(trimmed) -> trimmed.uppercase(Locale.ROOT)
            else -> trimmed
        }
    }

    /**
     * Classifies a normalized sender identifier into a domain [SenderType].
     */
    fun classifyType(normalizedIdentifier: String): SenderType {
        val trimmed = normalizedIdentifier.trim()
        if (trimmed.isBlank()) return SenderType.UNKNOWN

        // Indian DLT-style SMS headers
        val headerType = classifySmsHeader(trimmed)
        if (headerType != null) {
            return headerType
        }

        // Personal dialable phone numbers
        if (isPhoneNumber(trimmed)) {
            return SenderType.PERSONAL
        }

        return SenderType.UNKNOWN
    }

    /**
     * Produces an immutable [SenderProfile] from a raw identifier and optional display name.
     */
    fun classify(rawIdentifier: String?, displayName: String? = null): SenderProfile {
        val raw = rawIdentifier.orEmpty()
        val cleanedDisplayName = displayName?.trim()?.takeIf { it.isNotEmpty() }
        if (raw.isBlank()) {
            return SenderProfile(
                rawIdentifier = raw,
                normalizedIdentifier = "",
                senderType = SenderType.UNKNOWN,
                isKnownContact = false,
                displayName = cleanedDisplayName,
                confidence = 0.0f
            )
        }

        val normalized = normalize(raw)
        val senderType = classifyType(normalized)

        return SenderProfile(
            rawIdentifier = raw,
            normalizedIdentifier = normalized,
            senderType = senderType,
            isKnownContact = false,
            displayName = cleanedDisplayName,
            confidence = 1.0f
        )
    }

    private fun classifySmsHeader(identifier: String): SenderType? {
        val upper = identifier.uppercase(Locale.ROOT)

        // 1. Prefixed Header format: 2-alpha prefix + hyphen + 3-8 char alphanumeric entity + hyphen + suffix
        val prefixedMatch = PREFIXED_HEADER_REGEX.matchEntire(upper)
        if (prefixedMatch != null) {
            val suffix = prefixedMatch.groupValues[1]
            return when (suffix) {
                "S" -> SenderType.SERVICE
                "G" -> SenderType.GOVERNMENT
                "P" -> SenderType.PROMOTIONAL
                else -> null
            }
        }

        // 2. Direct Header format: 3-8 char alphanumeric entity + hyphen + suffix
        val suffixedMatch = SUFFIXED_HEADER_REGEX.matchEntire(upper)
        if (suffixedMatch != null) {
            val suffix = suffixedMatch.groupValues[1]
            return when (suffix) {
                "S" -> SenderType.SERVICE
                "G" -> SenderType.GOVERNMENT
                "P" -> SenderType.PROMOTIONAL
                else -> null
            }
        }

        return null
    }

    private fun isSmsHeader(identifier: String): Boolean {
        val upper = identifier.uppercase(Locale.ROOT)
        return PREFIXED_HEADER_REGEX.matches(upper) || SUFFIXED_HEADER_REGEX.matches(upper)
    }

    private fun isPhoneNumber(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        // Must match allowed phone characters only
        if (!PHONE_CHARS_REGEX.matches(trimmed)) return false

        // Must start with +, (, or digit, and end with ) or digit
        val first = trimmed.first()
        val last = trimmed.last()
        if (!(first.isDigit() || first == '+' || first == '(')) return false
        if (!(last.isDigit() || last == ')')) return false

        val digits = trimmed.filter(Char::isDigit)
        // Valid international/national phone number digit length
        return digits.length in 7..15
    }

    private fun normalizePhoneNumber(phone: String): String {
        val trimmed = phone.trim()
        val hasPlus = trimmed.contains("+")
        val digits = trimmed.filter(Char::isDigit)
        return if (hasPlus && digits.isNotEmpty()) "+$digits" else digits
    }

    private fun isEmail(text: String): Boolean = EMAIL_REGEX.matches(text)

    companion object {
        // Indian DLT/TRAI SMS Header Patterns:
        // Prefixed: 2 alpha (gateway/operator circle) - 3..8 alphanumeric entity - 1 alpha suffix [S, G, P]
        // Examples: AD-HDFCBK-S, VM-GOOGLE-S, VK-UIDAI-G, BZ-FLIPKT-P, AD-MYGOV-G, AD-SWIGGY-P
        private val PREFIXED_HEADER_REGEX = Regex("^[A-Z]{2}-[A-Z0-9]{3,8}-([SGP])$")

        // Direct Suffixed: 3..8 alphanumeric entity (starting with letter) - 1 alpha suffix [S, G, P]
        // Examples: HDFCBK-S, GOOGLE-S, UIDAI-G, FLIPKT-P, SWIGGY-P
        private val SUFFIXED_HEADER_REGEX = Regex("^[A-Z][A-Z0-9]{2,7}-([SGP])$")

        // Dialable phone formatting characters
        private val PHONE_CHARS_REGEX = Regex("^[+0-9()\\-.\t ]+$")

        // Email address pattern
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
