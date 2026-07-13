package com.sentinel.ai.agents.whatsapp

import com.sentinel.ai.core.event.ThreatEvent
import com.sentinel.ai.core.event.ThreatEventBus
import com.sentinel.ai.core.event.schema.EventValidator
import com.sentinel.ai.core.event.schema.EventSchemaGson
import com.sentinel.ai.core.event.schema.ValidationResult
import com.sentinel.ai.core.event.schema.ScamRiskLevel
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationAgentCoordinator @Inject constructor(
    private val parser: NotificationParser,
    private val builder: NotificationEventBuilder,
    private val threatEventBus: ThreatEventBus
) {
    private val _lastStatus = MutableStateFlow("IDLE")
    val lastStatus: StateFlow<String> = _lastStatus
    private val duplicateLock = Any()
    private val recentNotificationFingerprints = object : LinkedHashMap<String, Long>(MAX_DUPLICATE_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > MAX_DUPLICATE_CACHE_SIZE
        }
    }

    suspend fun onWhatsAppNotification(
        snapshot: WhatsAppNotificationSnapshot,
        isKnownContact: Boolean = false
    ) {
        _lastStatus.value = "CAPTURED"
        val raw = parser.parse(snapshot) ?: run {
            logDebug("Notification ignored: package=${snapshot.packageName}, sender=${snapshot.title.orEmpty()}, message=${snapshot.bigText ?: snapshot.text.orEmpty()}")
            _lastStatus.value = "IGNORED"
            return
        }
        logDebug(
            "Notification accepted: package=${raw.normalized.packageName}, " +
                "sender=${raw.normalized.senderTitle}, message=${raw.normalized.messageText}, " +
                "timestamp=${raw.normalized.timestampMs}"
        )
        val event = builder.build(raw, isKnownContact = isKnownContact) ?: run {
            logDebug("Notification ignored: package=${raw.normalized.packageName}, sender=${raw.normalized.senderTitle}, message=${raw.normalized.messageText}, reason=event_not_built")
            _lastStatus.value = "IGNORED"
            return
        }
        val validation = EventValidator.validateMessage(event)
        if (validation is ValidationResult.Invalid) {
            logWarn("Notification ignored: package=${raw.normalized.packageName}, sender=${raw.normalized.senderTitle}, message=${raw.normalized.messageText}, reason=invalid_event, errors=${validation.errors.joinToString()}")
            _lastStatus.value = "FAILED"
            return
        }

        logDebug("WhatsAppAgent: ${EventSchemaGson.toJsonMessage(event)}")

        val fingerprint = raw.deduplicationFingerprint()
        val senderIdentifier = raw.bestEffortSenderIdentifier()
        val senderDisplayName = raw.senderDisplayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals(senderIdentifier, ignoreCase = true) }

        val result = ScanResult(
            id = stableScanResultId(fingerprint, raw.normalized.timestampMs),
            source = raw.normalized.packageName,
            senderDisplayName = senderDisplayName,
            senderIdentifier = senderIdentifier,
            riskLevel = when (event.event.scamRiskLevel) {
                ScamRiskLevel.LOW -> RiskLevel.GREEN
                ScamRiskLevel.MEDIUM -> RiskLevel.YELLOW
                ScamRiskLevel.HIGH -> RiskLevel.RED
                ScamRiskLevel.CRITICAL -> RiskLevel.CRITICAL
                null -> RiskLevel.GREEN
            },
            riskScore = (event.event.scamRiskScore ?: 0).toFloat(),
            explanation = event.event.scamExplanations?.joinToString("; ") ?: "WhatsApp message captured and normalized",
            timestamp = snapshot.timestampMs
        )
        if (result.decision != ProtectionDecision.BLOCK && isDuplicate(fingerprint)) {
            logDebug(
                "Notification ignored: package=${raw.normalized.packageName}, " +
                    "sender=${raw.normalized.senderTitle}, message=${raw.normalized.messageText}, " +
                    "reason=duplicate_notification"
            )
            _lastStatus.value = "IGNORED"
            return
        }
        threatEventBus.emit(ThreatEvent.WhatsAppThreatDetected(result))
        _lastStatus.value = "COMPLETED"
    }

    private companion object {
        const val TAG = "NotificationAgent"
        const val DUPLICATE_WINDOW_MS = 3_000L
        const val MAX_DUPLICATE_CACHE_SIZE = 256
    }

    private fun logDebug(message: String) {
        runCatching {
            android.util.Log.d(TAG, message)
        }
    }

    private fun logWarn(message: String) {
        runCatching {
            android.util.Log.w(TAG, message)
        }
    }

    private fun WhatsAppRawNotificationData.deduplicationFingerprint(): String {
        // Intentionally excludes notificationKey. The Android-assigned StatusBarNotification key
        // identifies a *post event*, not the logical message: the same message content can arrive
        // through more than one distinct key (e.g. an individual conversation notification and an
        // OEM/launcher-generated re-post), and a single key can also be reused for unrelated
        // content after a conversation notification is recycled. Keying the fingerprint on the
        // key made the dedup filter miss real duplicates whenever the key differed, which was the
        // true cause of duplicate ScanResults for a single WhatsApp message. Content identity
        // (package + sender + message text) within the dedup time window is the correct notion of
        // "the same logical message".
        return listOf(
            packageName,
            normalized.senderTitle.trim(),
            normalized.messageText.trim()
        ).joinToString(separator = "|")
    }

    private fun isDuplicate(fingerprint: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(duplicateLock) {
            evictExpiredFingerprints(now)
            val previous = recentNotificationFingerprints[fingerprint]
            if (previous != null && now - previous <= DUPLICATE_WINDOW_MS) {
                return true
            }
            recentNotificationFingerprints[fingerprint] = now
            return false
        }
    }

    private fun evictExpiredFingerprints(now: Long) {
        val iterator = recentNotificationFingerprints.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > DUPLICATE_WINDOW_MS) {
                iterator.remove()
            }
        }
    }

    private fun stableScanResultId(fingerprint: String, notificationTimestampMs: Long): String {
        // The in-memory dedup fingerprint (package + sender + text) is deliberately time-agnostic
        // so it can catch near-simultaneous re-posts of the same message (e.g. an individual
        // notification and its group-summary companion). Using that same fingerprint alone as the
        // permanent Room primary key went too far: two genuinely separate incidents - the same
        // sender sending the same scam text again on a later, different occasion - hashed to the
        // identical id, so Room's upsert-by-id silently overwrote the earlier record instead of
        // adding a new one, and the threat count never incremented for repeat senders.
        // sbn.postTime (carried through as normalized.timestampMs) uniquely identifies *this*
        // notification instance: it stays constant if the same still-active notification is
        // redelivered (e.g. on NotificationListenerService rebind), but differs for a message
        // posted at a different time. Folding it into the id keeps redelivery idempotent while
        // letting real repeat incidents persist as distinct threat records.
        val idSeed = "$fingerprint|$notificationTimestampMs"
        return UUID.nameUUIDFromBytes(idSeed.toByteArray(StandardCharsets.UTF_8)).toString()
    }

    private fun WhatsAppRawNotificationData.bestEffortSenderIdentifier(): String? {
        val exactCandidates = listOfNotNull(
            senderDisplayName,
            subText,
            conversationTitle
        )

        exactCandidates.forEach { candidate ->
            val identifier = candidate.trim().stableIdentifierOrNull()
            if (identifier != null) return identifier
        }

        val phoneOrEmail = listOfNotNull(senderDisplayName, subText)
            .firstNotNullOfOrNull { text -> text.extractEmbeddedIdentifierOrNull() }
        if (phoneOrEmail != null) return phoneOrEmail

        return null
    }

    private fun String.stableIdentifierOrNull(): String? {
        val value = trim()
        if (value.isEmpty()) return null
        if (EMAIL_REGEX.matches(value)) return value
        if (HANDLE_REGEX.matches(value)) return value
        if (WHATSAPP_JID_REGEX.matches(value)) return value
        if (looksLikePhoneNumber()) return value
        if (ALPHA_SENDER_ID_REGEX.matches(value)) return value
        return null
    }

    private fun String.extractEmbeddedIdentifierOrNull(): String? {
        EMAIL_FINDER.find(this)?.value?.let { return it }
        HANDLE_FINDER.find(this)?.value?.let { return it }
        WHATSAPP_JID_FINDER.find(this)?.value?.let { return it }
        PHONE_FINDER.find(this)?.value?.trim()?.let { candidate ->
            if (candidate.looksLikePhoneNumber()) return candidate
        }
        return null
    }

    private fun String.looksLikePhoneNumber(): Boolean {
        val normalizedDigits = filter(Char::isDigit)
        return normalizedDigits.length in 7..15 && PHONE_ALLOWED_REGEX.matches(this)
    }

    private val EMAIL_REGEX = Regex("""^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$""", RegexOption.IGNORE_CASE)
    private val HANDLE_REGEX = Regex("""^@[A-Za-z0-9._]{2,}$""")
    private val WHATSAPP_JID_REGEX = Regex("""^[A-Za-z0-9._-]+@[A-Za-z0-9.-]+$""")
    private val ALPHA_SENDER_ID_REGEX = Regex("""^[A-Z][A-Z0-9_-]{2,15}$""")
    private val PHONE_ALLOWED_REGEX = Regex("""^[+()\-.\s0-9]{7,}$""")
    private val EMAIL_FINDER = Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE)
    private val HANDLE_FINDER = Regex("""@[A-Za-z0-9._]{2,}""")
    private val WHATSAPP_JID_FINDER = Regex("""[A-Za-z0-9._-]+@[A-Za-z0-9.-]+""")
    private val PHONE_FINDER = Regex("""\+?[0-9][0-9()\-\s.]{6,}[0-9]""")
}

/** Compatibility name for the existing WhatsApp-only coordinator and tests. */
typealias WhatsAppAgentCoordinator = NotificationAgentCoordinator
