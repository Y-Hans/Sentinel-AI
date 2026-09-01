package com.sentinel.ai.core.ml.messages

import java.util.regex.Pattern
import kotlin.math.max

/**
 * Deterministic 70-feature extraction engine for Messages-ML Champion V2.
 * Strictly reproduces Python feature_extraction.py.
 */
object MessageFeatureExtractor {

    private val RE_URGENCY_WORDS = Pattern.compile("\\b(immediately|urgent|urgently|asap|hurry|rush|act\\s+now|quick|instantly|fast|promptly)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_DEADLINE_WORDS = Pattern.compile("\\b(today|tonight|within\\s+\\d+\\s*(?:hours?|hrs?|mins?|minutes?)|before\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|expires?|deadline|last\\s+date|last\\s+day)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_TIME_LIMIT = Pattern.compile("\\b(?:within\\s+\\d+\\s*(?:hours?|hrs?|minutes?|mins?)|in\\s+\\d+\\s*(?:hours?|minutes?)|by\\s+\\d{1,2}\\s*(?:pm|am))\\b", Pattern.CASE_INSENSITIVE)

    private val RE_ACCOUNT_BLOCKED = Pattern.compile("\\b(blocked|deactivated|suspended|locked|restricted|freez(?:ed|e|ing)|disabled|inoperative)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_PENALTY = Pattern.compile("\\b(penalty|fine|fined|charges|late\\s+fee|dues|recovery|legal\\s+notice)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_LEGAL_ACTION = Pattern.compile("\\b(police|fir|court|arrest|warrant|advocate|legal\\s+action|cbi|cyber\\s+crime|trai\\s+complaint)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_DISCONNECTION = Pattern.compile("\\b(disconnect(?:ed|ion)?|power\\s+cut|light\\s+cut|bijli\\s+cut|service\\s+terminat(?:ed|ion))\\b", Pattern.CASE_INSENSITIVE)
    private val RE_UNAUTHORIZED_ACTIVITY = Pattern.compile("\\b(unauthorized|suspicious\\s+login|fraudulent\\s+transaction|security\\s+alert|unrecognized\\s+device)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_KYC_PAN = Pattern.compile("\\b(kyc|pan\\s*card|aadhaar|document\\s+verification|yono|update\\s+pan|pan\\s+update|kyc\\s+update)\\b", Pattern.CASE_INSENSITIVE)

    private val RE_OTP_GENERIC = Pattern.compile("\\b(otp|one\\s*time\\s*password|verification\\s*code|auth\\s*code|security\\s*code|login\\s*pin|2fa)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_PIN = Pattern.compile("\\b(upi\\s*pin|atm\\s*pin|mpin|secret\\s*pin|passcode)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_PASSWORD = Pattern.compile("\\b(password|netbanking\\s*password|login\\s*password)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_NUMERIC_CODE = Pattern.compile("\\b\\d{4,8}\\b")

    private val RE_OTP_NEGATION_WARNING = Pattern.compile("\\b(never\\s+share|do\\s+not\\s+(?:share|disclose|give|tell|forward)|bank\\s+never\\s+asks|keep\\s+(?:it\\s+)?confidential|strictly\\s+confidential|don'?t\\s+share)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_OTP_DISCLOSURE_REQUEST = Pattern.compile("\\b(?:share|send|tell|forward|provide|give|reply\\s+with|message)\\s+(?:me\\s+|us\\s+|your\\s+|the\\s+)?(?:secret\\s+)?(?:otp|code|pin|password|one\\s*time\\s*password)\\b|\\b(?:otp|code|pin)\\s+(?:to\\s+(?:cancel|stop|verify|customer\\s*care|support|executive|officer|manager|number))\\b", Pattern.CASE_INSENSITIVE)
    private val RE_DELIVERY_CONTEXT = Pattern.compile("\\b(delivery\\s+agent|delivery\\s+associate|delivery\\s+partner|delivery\\s+boy|courier\\s+person|at\\s+your\\s+door|package\\s+arrival|order\\s+delivery)\\b", Pattern.CASE_INSENSITIVE)

    private val RE_DEBIT_CREDIT = Pattern.compile("\\b(debited\\s+by|credited\\s+with|withdrawn|transferred|txn|transaction|payment\\s+of|refund\\s+of)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_CURRENCY_AMOUNT = Pattern.compile("(?:₹|Rs\\.?|INR)\\s*[\\d,]+(?:\\.\\d+)?", Pattern.CASE_INSENSITIVE)
    private val RE_MASKED_ACCOUNT = Pattern.compile("(?:[xX*]{2,}\\d{3,4}|a/c\\s*(?:no\\.?)?\\s*[xX*]*\\d{3,4})", Pattern.CASE_INSENSITIVE)
    private val RE_BALANCE = Pattern.compile("\\b(bal|balance|avl\\s*bal|available\\s*balance)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_UPI_COLLECT = Pattern.compile("\\b(collect\\s+request|upi\\s+collect|approve\\s+collect|enter\\s+pin\\s+to\\s+receive)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_LURE_PRIZE = Pattern.compile("\\b(lottery|won|winner|cashback|reward|bonus|lucky\\s+draw|free\\s+gift|claim\\s+reward|earned\\s+rs)\\b", Pattern.CASE_INSENSITIVE)

    private val RE_URL = Pattern.compile("https?://\\S+|www\\.\\S+", Pattern.CASE_INSENSITIVE)
    private val RE_SHORTENER = Pattern.compile("\\b(bit\\.ly|tinyurl\\.com|is\\.gd|cutt\\.ly|t\\.co|rb\\.gy|shorturl\\.at|wa\\.me)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_RAW_IP = Pattern.compile("https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}", Pattern.CASE_INSENSITIVE)
    private val RE_APK = Pattern.compile("(\\.apk\\b|download\\s+(?:support|bank|update)\\s+app)", Pattern.CASE_INSENSITIVE)
    private val RE_PHONE = Pattern.compile("(?:\\+91|0)?[6-9]\\d{9}")
    private val RE_UPI_VPA = Pattern.compile("\\b[a-zA-Z0-9.\\-_]{2,256}@(okhdfcbank|okaxis|okicici|oksbi|paytm|ybl|apl|upi)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_WHATSAPP_CTA = Pattern.compile("\\b(whatsapp|wa\\.me|contact\\s+on\\s+whatsapp|telegram)\\b", Pattern.CASE_INSENSITIVE)

    private val RE_LEGIT_INSTITUTION = Pattern.compile("\\b(income\\s+tax|incometax|uidai|aadhaar|msedcl|mahavitaran|gov\\.in|bank\\s+of|hdfc|icici|sbi|axis\\s+bank|kotak|pnb)\\b", Pattern.CASE_INSENSITIVE)
    private val RE_LEGIT_NOTICE = Pattern.compile("\\b(official\\s+(?:portal|app|website)|never\\s+ask|do\\s+not\\s+share|consumer\\s+no|ay\\s+\\d{4}|section\\s+\\d+|dear\\s+customer)\\b", Pattern.CASE_INSENSITIVE)

    private fun countMatches(p: Pattern, s: String): Int {
        val m = p.matcher(s)
        var count = 0
        while (m.find()) count++
        return count
    }

    private fun hasMatch(p: Pattern, s: String): Boolean {
        return p.matcher(s).find()
    }

    /**
     * Extracts exactly 70 deterministic features matching canonical alphabetical order.
     */
    fun extractDeterministicFeatures(rawText: String, senderHeader: String?): DoubleArray {

        val norm = TextNormalizer.analyzeAndNormalize(rawText)
        val sender = SenderParser.parseSenderHeader(senderHeader)

        val totalC = max(1, norm.totalChars)
        val words = if (norm.cleanedText.isNotEmpty()) norm.cleanedText.split("\\s+".toRegex()) else emptyList()
        val wordCount = words.size
        var totalWordLen = 0
        for (w in words) totalWordLen += w.codePointCount(0, w.length)
        val avgWordLen = totalWordLen.toDouble() / max(1, wordCount).toDouble()

        val text = norm.normalizedLowercase
        val raw = norm.rawText

        // 1. Structural
        val messageLength = norm.totalChars.toFloat()
        val wordCountF = wordCount.toFloat()
        val avgWordLength = avgWordLen.toFloat()
        val digitCount = norm.digitCount.toFloat()
        val digitRatio = norm.digitCount.toFloat() / totalC.toFloat()
        val uppercaseCount = norm.uppercaseCount.toFloat()
        val uppercaseRatio = norm.uppercaseCount.toFloat() / totalC.toFloat()
        val specialCharCount = norm.punctuationCount.toFloat()
        val specialCharRatio = norm.punctuationCount.toFloat() / totalC.toFloat()
        val newlineCount = norm.newlineCount.toFloat()
        val whitespaceCount = norm.whitespaceCount.toFloat()
        val zeroWidthCount = norm.zeroWidthCount.toFloat()
        val hasHomoglyphs = if (norm.hasHomoglyphs) 1.0f else 0.0f
        val isMixedScript = if (norm.isMixedScript) 1.0f else 0.0f

        // 2. Urgency
        val urgMatches = countMatches(RE_URGENCY_WORDS, text)
        val dlMatches = countMatches(RE_DEADLINE_WORDS, text)
        val timeLim = if (hasMatch(RE_TIME_LIMIT, text)) 1.0f else 0.0f
        val urgencyWordCount = urgMatches.toFloat()
        val deadlineWordCount = dlMatches.toFloat()
        val hasTimeLimitPattern = timeLim
        val highPressureScore = (urgMatches * 1.5f + dlMatches * 1.0f + timeLim * 2.0f)

        // 3. Fear / Threat
        val blk = if (hasMatch(RE_ACCOUNT_BLOCKED, text)) 1.0f else 0.0f
        val pen = if (hasMatch(RE_PENALTY, text)) 1.0f else 0.0f
        val leg = if (hasMatch(RE_LEGAL_ACTION, text)) 1.0f else 0.0f
        val disc = if (hasMatch(RE_DISCONNECTION, text)) 1.0f else 0.0f
        val unauth = if (hasMatch(RE_UNAUTHORIZED_ACTIVITY, text)) 1.0f else 0.0f
        val kyc = if (hasMatch(RE_KYC_PAN, text)) 1.0f else 0.0f
        val totalFearSignals = blk + pen + leg + disc + unauth + kyc

        // 4. Auth
        val otpGen = if (hasMatch(RE_OTP_GENERIC, text)) 1.0f else 0.0f
        val pinPres = if (hasMatch(RE_PIN, text)) 1.0f else 0.0f
        val passPres = if (hasMatch(RE_PASSWORD, text)) 1.0f else 0.0f
        val codeM = RE_NUMERIC_CODE.matcher(text)
        var hasCode = 0.0f
        var maxCodeLen = 0.0f
        while (codeM.find()) {
            hasCode = 1.0f
            val len = codeM.group().length.toFloat()
            if (len > maxCodeLen) maxCodeLen = len
        }

        // 5. OTP Intent
        val otpPres = if (hasMatch(RE_OTP_GENERIC, text) || hasCode > 0.0f) 1.0f else 0.0f
        val negWarn = if (hasMatch(RE_OTP_NEGATION_WARNING, text)) 1.0f else 0.0f
        val discReq = if (hasMatch(RE_OTP_DISCLOSURE_REQUEST, text)) 1.0f else 0.0f
        val delivCtx = if (hasMatch(RE_DELIVERY_CONTEXT, text)) 1.0f else 0.0f
        val otpIntentScore = (discReq * 3.0f) - (negWarn * 2.0f) - (delivCtx * 1.5f)

        // 6. Financial
        val dcMatches = countMatches(RE_DEBIT_CREDIT, text).toFloat()
        val amt = if (hasMatch(RE_CURRENCY_AMOUNT, text)) 1.0f else 0.0f
        val acct = if (hasMatch(RE_MASKED_ACCOUNT, text)) 1.0f else 0.0f
        val bal = if (hasMatch(RE_BALANCE, text)) 1.0f else 0.0f
        val upiColl = if (hasMatch(RE_UPI_COLLECT, text)) 1.0f else 0.0f
        val lures = countMatches(RE_LURE_PRIZE, text).toFloat()

        // 7. CTA
        val urlCount = countMatches(RE_URL, raw).toFloat()
        val hasShort = if (hasMatch(RE_SHORTENER, raw)) 1.0f else 0.0f
        val hasIp = if (hasMatch(RE_RAW_IP, raw)) 1.0f else 0.0f
        val hasApk = if (hasMatch(RE_APK, raw)) 1.0f else 0.0f
        val phoneCount = countMatches(RE_PHONE, raw).toFloat()
        val vpaCount = countMatches(RE_UPI_VPA, raw).toFloat()
        val hasWa = if (hasMatch(RE_WHATSAPP_CTA, raw)) 1.0f else 0.0f

        // 8. Sender
        val isAlpha = if (sender.senderType == "ALPHANUMERIC_HEADER") 1.0f else 0.0f
        val isDlt = if (sender.hasDltStructuralShape) 1.0f else 0.0f
        val isPhone = if (sender.senderType == "PHONE_NUMBER") 1.0f else 0.0f
        val isShort = if (sender.isShortcode) 1.0f else 0.0f
        val hasBank = if (sender.knownEntityCategory == "BANK") 1.0f else 0.0f

        val bodyHasBank = if (hasMatch(RE_MASKED_ACCOUNT, text) || hasMatch(RE_KYC_PAN, text)) 1.0f else 0.0f
        val bodyHasDisc = if (hasMatch(RE_DISCONNECTION, text)) 1.0f else 0.0f
        val phoneBankMismatch = if (isPhone > 0.0f && (bodyHasBank > 0.0f || bodyHasDisc > 0.0f)) 1.0f else 0.0f

        // 9. Legit Intent
        val inst = countMatches(RE_LEGIT_INSTITUTION, text).toFloat()
        val notice = countMatches(RE_LEGIT_NOTICE, text).toFloat()
        val hasInst = if (inst > 0.0f) 1.0f else 0.0f
        val hasNotice = if (notice > 0.0f) 1.0f else 0.0f
        val legitContextScore = inst * 1.0f + notice * 1.5f

        // 10. Interactions (mirroring exact python dictionary lookups)
        val urgencyXLegitIntent = urgencyWordCount * legitContextScore
        val kycXSender = kyc * isAlpha
        val otpXProtective = 0.0f
        val financialXCredential = 0.0f
        val urlXCta = 0.0f
        val urlXSender = 0.0f
        val institutionalXUrl = 0.0f
        val suspensionXProtective = 0.0f
        val bankingXDisclosure = 0.0f
        val urgencyXCredential = 0.0f
        val fearXCta = 0.0f

        // Return 70 features in exact canonical alphabetical order:
        return doubleArrayOf(
            blk.toDouble(),                     //  0: account_blocked_signal
            avgWordLength.toDouble(),           //  1: avg_word_length
            bal.toDouble(),                     //  2: balance_keyword_present
            bankingXDisclosure.toDouble(),      //  3: banking_x_disclosure
            amt.toDouble(),                     //  4: currency_amount_present
            deadlineWordCount.toDouble(),       //  5: deadline_word_count
            dcMatches.toDouble(),               //  6: debit_credit_count
            delivCtx.toDouble(),                //  7: delivery_otp_context
            digitCount.toDouble(),              //  8: digit_count
            digitRatio.toDouble(),              //  9: digit_ratio
            disc.toDouble(),                    // 10: disconnection_signal
            fearXCta.toDouble(),                // 11: fear_x_cta
            financialXCredential.toDouble(),    // 12: financial_x_credential
            hasApk.toDouble(),                  // 13: has_apk_reference
            hasHomoglyphs.toDouble(),           // 14: has_homoglyphs
            hasInst.toDouble(),                 // 15: has_legit_institution
            hasNotice.toDouble(),               // 16: has_legit_notice
            hasIp.toDouble(),                   // 17: has_raw_ip_url
            hasShort.toDouble(),                // 18: has_shortener_url
            hasTimeLimitPattern.toDouble(),     // 19: has_time_limit_pattern
            highPressureScore.toDouble(),       // 20: high_pressure_score
            institutionalXUrl.toDouble(),       // 21: institutional_x_url
            isMixedScript.toDouble(),           // 22: is_mixed_script
            kyc.toDouble(),                     // 23: kyc_pan_signal
            kycXSender.toDouble(),              // 24: kyc_x_sender
            leg.toDouble(),                     // 25: legal_action_signal
            legitContextScore.toDouble(),       // 26: legit_context_score
            inst.toDouble(),                    // 27: legit_institution_keyword_count
            notice.toDouble(),                  // 28: legit_notice_keyword_count
            acct.toDouble(),                    // 29: masked_account_present
            messageLength.toDouble(),           // 30: message_length
            newlineCount.toDouble(),            // 31: newline_count
            maxCodeLen.toDouble(),              // 32: numeric_code_length
            hasCode.toDouble(),                 // 33: numeric_code_present
            discReq.toDouble(),                 // 34: otp_disclosure_request
            otpGen.toDouble(),                  // 35: otp_generic_present
            otpIntentScore.toDouble(),          // 36: otp_intent_risk_score
            negWarn.toDouble(),                 // 37: otp_negation_warning
            otpPres.toDouble(),                 // 38: otp_present
            otpXProtective.toDouble(),          // 39: otp_x_protective
            passPres.toDouble(),                // 40: password_present
            pen.toDouble(),                     // 41: penalty_signal
            phoneCount.toDouble(),              // 42: phone_number_count
            pinPres.toDouble(),                 // 43: pin_present
            lures.toDouble(),                   // 44: prize_lure_count
            hasBank.toDouble(),                 // 45: sender_has_bank_match
            isAlpha.toDouble(),                 // 46: sender_is_alphanumeric
            isDlt.toDouble(),                   // 47: sender_is_dlt_shape
            isPhone.toDouble(),                 // 48: sender_is_phone_number
            isShort.toDouble(),                 // 49: sender_is_shortcode
            phoneBankMismatch.toDouble(),       // 50: sender_phone_with_banking_body
            specialCharCount.toDouble(),        // 51: special_char_count
            specialCharRatio.toDouble(),        // 52: special_char_ratio
            suspensionXProtective.toDouble(),   // 53: suspension_x_protective
            totalFearSignals.toDouble(),        // 54: total_fear_signals_count
            unauth.toDouble(),                  // 55: unauthorized_activity_signal
            upiColl.toDouble(),                 // 56: upi_collect_keyword_present
            vpaCount.toDouble(),                // 57: upi_vpa_count
            uppercaseCount.toDouble(),          // 58: uppercase_count
            uppercaseRatio.toDouble(),          // 59: uppercase_ratio
            urgencyWordCount.toDouble(),        // 60: urgency_word_count
            urgencyXCredential.toDouble(),      // 61: urgency_x_credential
            urgencyXLegitIntent.toDouble(),     // 62: urgency_x_legit_intent
            urlCount.toDouble(),                // 63: url_count
            urlXCta.toDouble(),                 // 64: url_x_cta
            urlXSender.toDouble(),              // 65: url_x_sender
            hasWa.toDouble(),                   // 66: whatsapp_cta_present
            whitespaceCount.toDouble(),         // 67: whitespace_count
            wordCountF.toDouble(),              // 68: word_count
            zeroWidthCount.toDouble()           // 69: zero_width_count
        )
    }
}
