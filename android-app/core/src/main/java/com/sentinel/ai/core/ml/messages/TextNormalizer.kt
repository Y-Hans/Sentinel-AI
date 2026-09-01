package com.sentinel.ai.core.ml.messages

import java.text.Normalizer
import java.util.regex.Pattern

data class TextNormalizationResult(
    val rawText: String,
    val cleanedText: String,
    val normalizedLowercase: String,
    val totalChars: Int,
    val zeroWidthCount: Int,
    val homoglyphCount: Int,
    val latinCharCount: Int,
    val devanagariCharCount: Int,
    val digitCount: Int,
    val uppercaseCount: Int,
    val lowercaseCount: Int,
    val punctuationCount: Int,
    val whitespaceCount: Int,
    val newlineCount: Int,
    val otherUnicodeCount: Int,
    val detectedScript: String,
    val isMixedScript: Boolean,
    val hasHomoglyphs: Boolean
)

object TextNormalizer {

    val ZERO_WIDTH_CHARS = setOf(
        '\u200b', '\u200c', '\u200d', '\ufeff', '\u00ad',
        '\u200e', '\u200f', '\u202a', '\u202b', '\u202c',
        '\u202d', '\u202e'
    )

    val HOMOGLYPH_MAP = mapOf(
        '\u0430' to 'a', '\u0410' to 'A', '\u0435' to 'e', '\u0415' to 'E',
        '\u043e' to 'o', '\u041e' to 'O', '\u0440' to 'p', '\u0420' to 'P',
        '\u0441' to 'c', '\u0421' to 'C', '\u0443' to 'y', '\u0445' to 'x',
        '\u0425' to 'X', '\u0456' to 'i', '\u0406' to 'I', '\u0458' to 'j',
        '\u0408' to 'J',
        '\u03b1' to 'a', '\u03bf' to 'o', '\u039f' to 'O', '\u03bd' to 'v'
    )

    private val RE_COLLAPSE_SPACES = Pattern.compile("[ \t]+")

    fun analyzeAndNormalize(rawText: String?): TextNormalizationResult {
        val raw = rawText ?: ""
        val totalChars = raw.codePointCount(0, raw.length)
        if (totalChars == 0) {
            return TextNormalizationResult(
                rawText = "",
                cleanedText = "",
                normalizedLowercase = "",
                totalChars = 0,
                zeroWidthCount = 0,
                homoglyphCount = 0,
                latinCharCount = 0,
                devanagariCharCount = 0,
                digitCount = 0,
                uppercaseCount = 0,
                lowercaseCount = 0,
                punctuationCount = 0,
                whitespaceCount = 0,
                newlineCount = 0,
                otherUnicodeCount = 0,
                detectedScript = "UNKNOWN",
                isMixedScript = false,
                hasHomoglyphs = false
            )
        }

        var zeroWidthCount = 0
        var homoglyphCount = 0
        var latinCount = 0
        var devanagariCount = 0
        var digitCount = 0
        var upperCount = 0
        var lowerCount = 0
        var punctCount = 0
        var whitespaceCount = 0
        var newlineCount = 0
        var otherUnicodeCount = 0

        var charIdx = 0
        while (charIdx < raw.length) {
            val cp = raw.codePointAt(charIdx)
            val charCount = Character.charCount(cp)
            charIdx += charCount

            val ch = if (charCount == 1) cp.toChar() else null
            if (ch != null && ch in ZERO_WIDTH_CHARS) {
                zeroWidthCount++
                continue
            }
            if (ch != null && ch in HOMOGLYPH_MAP) {
                homoglyphCount++
            }
            if (cp == 0x0A || cp == 0x0D) {
                newlineCount++
            }
            if (Character.isWhitespace(cp)) {
                whitespaceCount++
                continue
            }
            if (Character.isDigit(cp)) {
                digitCount++
                continue
            }

            val type = Character.getType(cp)
            val isPunctOrSymbol = (
                type == Character.DASH_PUNCTUATION.toInt() ||
                type == Character.START_PUNCTUATION.toInt() ||
                type == Character.END_PUNCTUATION.toInt() ||
                type == Character.CONNECTOR_PUNCTUATION.toInt() ||
                type == Character.OTHER_PUNCTUATION.toInt() ||
                type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
                type == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
                type == Character.MATH_SYMBOL.toInt() ||
                type == Character.CURRENCY_SYMBOL.toInt() ||
                type == Character.MODIFIER_SYMBOL.toInt() ||
                type == Character.OTHER_SYMBOL.toInt()
            )
            if (isPunctOrSymbol) {
                punctCount++
                continue
            }

            if (Character.isUpperCase(cp)) {
                upperCount++
            } else if (Character.isLowerCase(cp)) {
                lowerCount++
            }

            if ((cp in 0x0041..0x005A) || (cp in 0x0061..0x007A) || (cp in 0x00C0..0x024F)) {
                latinCount++
            } else if (cp in 0x0900..0x097F) {
                devanagariCount++
            } else {
                otherUnicodeCount++
            }
        }

        val totalLetters = latinCount + devanagariCount + otherUnicodeCount
        val detectedScript: String
        val isMixedScript: Boolean

        if (totalLetters == 0) {
            detectedScript = if (digitCount > 0) "LATIN" else "UNKNOWN"
            isMixedScript = false
        } else {
            val latinRatio = latinCount.toDouble() / totalLetters.toDouble()
            val devaRatio = devanagariCount.toDouble() / totalLetters.toDouble()

            if (latinCount > 0 && devanagariCount > 0) {
                detectedScript = "MIXED"
                isMixedScript = true
            } else if (latinRatio >= 0.85) {
                detectedScript = "LATIN"
                isMixedScript = otherUnicodeCount > 1
            } else if (devaRatio >= 0.85) {
                detectedScript = "DEVANAGARI"
                isMixedScript = otherUnicodeCount > 1
            } else {
                detectedScript = "OTHER"
                isMixedScript = (latinCount > 0 || devanagariCount > 0)
            }
        }

        // Clean text construction
        val stripped = StringBuilder()
        var sIdx = 0
        while (sIdx < raw.length) {
            val cp = raw.codePointAt(sIdx)
            val count = Character.charCount(cp)
            sIdx += count
            val ch = if (count == 1) cp.toChar() else null
            if (ch == null || ch !in ZERO_WIDTH_CHARS) {
                if (ch != null) stripped.append(ch) else stripped.append(Character.toChars(cp))
            }
        }
        val normalizedNfkd = Normalizer.normalize(stripped.toString(), Normalizer.Form.NFKD)
        val homoglyphReplaced = StringBuilder()
        for (i in 0 until normalizedNfkd.length) {
            val ch = normalizedNfkd[i]
            homoglyphReplaced.append(HOMOGLYPH_MAP[ch] ?: ch)
        }

        val lines = homoglyphReplaced.toString().split("\r?\n".toRegex())
        val cleanedLines = mutableListOf<String>()
        for (line in lines) {
            val collapsed = RE_COLLAPSE_SPACES.matcher(line).replaceAll(" ").trim()
            if (collapsed.isNotEmpty()) {
                cleanedLines.add(collapsed)
            }
        }
        val cleanedText = cleanedLines.joinToString("\n")
        val normalizedLowercase = cleanedText.lowercase()

        return TextNormalizationResult(
            rawText = raw,
            cleanedText = cleanedText,
            normalizedLowercase = normalizedLowercase,
            totalChars = totalChars,
            zeroWidthCount = zeroWidthCount,
            homoglyphCount = homoglyphCount,
            latinCharCount = latinCount,
            devanagariCharCount = devanagariCount,
            digitCount = digitCount,
            uppercaseCount = upperCount,
            lowercaseCount = lowerCount,
            punctuationCount = punctCount,
            whitespaceCount = whitespaceCount,
            newlineCount = newlineCount,
            otherUnicodeCount = otherUnicodeCount,
            detectedScript = detectedScript,
            isMixedScript = isMixedScript,
            hasHomoglyphs = homoglyphCount > 0
        )
    }
}
