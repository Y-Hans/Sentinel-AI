package com.sentinel.ai.core.ml.messages

import java.util.regex.Pattern

data class SenderParseResult(
    val rawHeader: String?,
    val senderType: String,
    val hasDltStructuralShape: Boolean,
    val dltPrefix: String?,
    val dltEntityCode: String?,
    val dltSuffixCategory: String?,
    val isDomesticPhone: Boolean,
    val isInternationalPhone: Boolean,
    val isShortcode: Boolean,
    val knownEntityMatch: String?,
    val knownEntityCategory: String?,
    val headerLength: Int
)

object SenderParser {

    private val DLT_PATTERN = Pattern.compile("^([A-Za-z]{2})[-_]([A-Za-z0-9]{6})(?:[-_]([A-Za-z0-9]+))?$")
    private val STANDALONE_ALPHA_PATTERN = Pattern.compile("^[A-Za-z0-9]{3,9}$")
    private val INDIAN_PHONE_PATTERN = Pattern.compile("^(?:\\+91|0)?([6-9]\\d{9})$")
    private val INTL_PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{6,14}$")
    private val SHORTCODE_PATTERN = Pattern.compile("^\\d{3,6}$")

    val KNOWN_ENTITIES = mapOf(
        "HDFCBK" to "BANK", "HDFC" to "BANK",
        "SBIINB" to "BANK", "SBIPAY" to "BANK", "SBICRD" to "BANK", "SBI" to "BANK",
        "ICICIB" to "BANK", "ICICI" to "BANK",
        "AXISBK" to "BANK", "AXIS" to "BANK",
        "KOTAKB" to "BANK", "KOTAK" to "BANK",
        "PNBSMS" to "BANK", "PNB" to "BANK",
        "BOBTXN" to "BANK", "BOB" to "BANK",
        "CANBNK" to "BANK", "CANARA" to "BANK",
        "AIRTEL" to "TELECOM", "JIOINF" to "TELECOM", "JIO" to "TELECOM",
        "VIALRT" to "TELECOM", "VIL" to "TELECOM", "BSNL" to "TELECOM",
        "ITDPRC" to "GOVT", "UIDAI" to "GOVT", "EPFOHO" to "GOVT",
        "GSTIND" to "GOVT", "VAAHAN" to "GOVT",
        "AMAZON" to "ECOMMERCE", "FLPKRT" to "ECOMMERCE",
        "SWIGGY" to "DELIVERY", "ZOMATO" to "DELIVERY"
    )

    fun parseSenderHeader(header: String?): SenderParseResult {
        if (header == null || header.trim().isEmpty()) {
            return SenderParseResult(
                rawHeader = null,
                senderType = "UNKNOWN",
                hasDltStructuralShape = false,
                dltPrefix = null,
                dltEntityCode = null,
                dltSuffixCategory = null,
                isDomesticPhone = false,
                isInternationalPhone = false,
                isShortcode = false,
                knownEntityMatch = null,
                knownEntityCategory = null,
                headerLength = 0
            )
        }

        val cleanHeader = header.trim()
        val hLen = cleanHeader.length

        // 1. DLT Match
        val dltM = DLT_PATTERN.matcher(cleanHeader)
        if (dltM.matches()) {
            val prefix = dltM.group(1)!!.uppercase()
            val entity = dltM.group(2)!!.uppercase()
            val suffix = if (dltM.groupCount() >= 3 && dltM.group(3) != null) dltM.group(3)!!.uppercase() else null

            var matchedEntity: String? = null
            var matchedCategory: String? = null
            for ((known, cat) in KNOWN_ENTITIES) {
                if (entity.contains(known)) {
                    matchedEntity = known
                    matchedCategory = cat
                    break
                }
            }

            return SenderParseResult(
                rawHeader = cleanHeader,
                senderType = "ALPHANUMERIC_HEADER",
                hasDltStructuralShape = true,
                dltPrefix = prefix,
                dltEntityCode = entity,
                dltSuffixCategory = suffix,
                isDomesticPhone = false,
                isInternationalPhone = false,
                isShortcode = false,
                knownEntityMatch = matchedEntity,
                knownEntityCategory = matchedCategory,
                headerLength = hLen
            )
        }

        // 2. Shortcode
        if (SHORTCODE_PATTERN.matcher(cleanHeader).matches()) {
            return SenderParseResult(
                rawHeader = cleanHeader,
                senderType = "SHORTCODE",
                hasDltStructuralShape = false,
                dltPrefix = null,
                dltEntityCode = null,
                dltSuffixCategory = null,
                isDomesticPhone = false,
                isInternationalPhone = false,
                isShortcode = true,
                knownEntityMatch = null,
                knownEntityCategory = null,
                headerLength = hLen
            )
        }

        // 3. Domestic Indian Phone
        if (INDIAN_PHONE_PATTERN.matcher(cleanHeader).matches()) {
            return SenderParseResult(
                rawHeader = cleanHeader,
                senderType = "PHONE_NUMBER",
                hasDltStructuralShape = false,
                dltPrefix = null,
                dltEntityCode = null,
                dltSuffixCategory = null,
                isDomesticPhone = true,
                isInternationalPhone = false,
                isShortcode = false,
                knownEntityMatch = null,
                knownEntityCategory = null,
                headerLength = hLen
            )
        }

        // 4. Standalone Alphanumeric Header
        if (STANDALONE_ALPHA_PATTERN.matcher(cleanHeader).matches() && !cleanHeader.all { it.isDigit() }) {
            val upperHeader = cleanHeader.uppercase()
            var matchedEntity: String? = null
            var matchedCategory: String? = null
            for ((known, cat) in KNOWN_ENTITIES) {
                if (upperHeader.contains(known)) {
                    matchedEntity = known
                    matchedCategory = cat
                    break
                }
            }

            return SenderParseResult(
                rawHeader = cleanHeader,
                senderType = "ALPHANUMERIC_HEADER",
                hasDltStructuralShape = false,
                dltPrefix = null,
                dltEntityCode = upperHeader,
                dltSuffixCategory = null,
                isDomesticPhone = false,
                isInternationalPhone = false,
                isShortcode = false,
                knownEntityMatch = matchedEntity,
                knownEntityCategory = matchedCategory,
                headerLength = hLen
            )
        }

        // 5. International Phone
        if (INTL_PHONE_PATTERN.matcher(cleanHeader).matches()) {
            return SenderParseResult(
                rawHeader = cleanHeader,
                senderType = "PHONE_NUMBER",
                hasDltStructuralShape = false,
                dltPrefix = null,
                dltEntityCode = null,
                dltSuffixCategory = null,
                isDomesticPhone = false,
                isInternationalPhone = true,
                isShortcode = false,
                knownEntityMatch = null,
                knownEntityCategory = null,
                headerLength = hLen
            )
        }

        // 6. Unknown fallback
        return SenderParseResult(
            rawHeader = cleanHeader,
            senderType = "UNKNOWN",
            hasDltStructuralShape = false,
            dltPrefix = null,
            dltEntityCode = null,
            dltSuffixCategory = null,
            isDomesticPhone = false,
            isInternationalPhone = false,
            isShortcode = false,
            knownEntityMatch = null,
            knownEntityCategory = null,
            headerLength = hLen
        )
    }
}
