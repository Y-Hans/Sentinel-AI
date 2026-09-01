package com.sentinel.ai.agents.whatsapp

import com.sentinel.ai.core.event.ThreatEvent
import com.sentinel.ai.core.event.ThreatEventBus
import com.sentinel.ai.core.event.ThreatJournal
import com.sentinel.ai.core.event.schema.EventValidator
import com.sentinel.ai.core.event.schema.ValidationResult
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.agents.sender.SenderClassifier
import com.sentinel.ai.core.fusion.FusionContext
import com.sentinel.ai.core.fusion.RiskFusionEngine
import com.sentinel.ai.core.warning.WarningNotificationDispatcher
import com.sentinel.ai.core.warning.WarningSeverity
import com.sentinel.ai.core.warning.toWarningUiModel
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
    private val threatEventBus: ThreatEventBus,
    private val threatJournal: ThreatJournal,
    private val warningDispatcher: WarningNotificationDispatcher,
    private val threatAnalyzer: NotificationThreatAnalyzer,
    private val riskFusionEngine: RiskFusionEngine,
    private val senderClassifier: SenderClassifier
) {
    // Secondary constructor for 6-arg testing without DI
    constructor(
        parser: NotificationParser,
        builder: NotificationEventBuilder,
        threatEventBus: ThreatEventBus,
        threatJournal: ThreatJournal,
        warningDispatcher: WarningNotificationDispatcher,
        riskFusionEngine: RiskFusionEngine
    ) : this(
        parser = parser,
        builder = builder,
        threatEventBus = threatEventBus,
        threatJournal = threatJournal,
        warningDispatcher = warningDispatcher,
        threatAnalyzer = NotificationThreatAnalyzer(),
        riskFusionEngine = riskFusionEngine,
        senderClassifier = SenderClassifier()
    )
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
            _lastStatus.value = "IGNORED"
            return
        }
        val event = builder.build(raw, isKnownContact = isKnownContact) ?: run {
            _lastStatus.value = "IGNORED"
            return
        }
        val validation = EventValidator.validateMessage(event)
        if (validation is ValidationResult.Invalid) {
            _lastStatus.value = "FAILED"
            return
        }

        val fingerprint = raw.deduplicationFingerprint()
        val senderIdentifier = raw.bestEffortSenderIdentifier()
        val senderDisplayName = raw.senderDisplayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals(senderIdentifier, ignoreCase = true) }

        val senderProfile = senderClassifier.classify(
            rawIdentifier = senderIdentifier ?: senderDisplayName,
            displayName = senderDisplayName
        )

        val evidence = threatAnalyzer.extractEvidence(
            messageText = raw.messageText.orEmpty(),
            urls = event.event.urls.orEmpty(),
            isKnownContact = isKnownContact,
            senderHeader = senderIdentifier ?: senderDisplayName
        )

        val fusionContext = FusionContext(
            source = raw.normalized.packageName,
            target = raw.messageText,
            senderProfile = senderProfile,
            isKnownContact = isKnownContact,
            timestamp = snapshot.timestampMs
        )

        val fusionResult = riskFusionEngine.fuse(evidence, fusionContext)

        val result = fusionResult.toScanResult(
            id = stableScanResultId(fingerprint, raw.normalized.timestampMs),
            source = raw.normalized.packageName,
            target = raw.messageText,
            senderDisplayName = senderDisplayName,
            senderIdentifier = senderIdentifier,
            timestamp = snapshot.timestampMs
        )

        if (result.decision != ProtectionDecision.BLOCK && isDuplicate(fingerprint)) {
            _lastStatus.value = "IGNORED"
            return
        }

        // 1. Direct durable Room persistence (awaiting completion)
        threatJournal.recordScanResult(result)

        // 2. Direct warning notification dispatch for elevated threats
        dispatchWarningIfNeeded(result)

        // 3. Optional transient event bus emission for reactive UI consumers
        threatEventBus.emit(ThreatEvent.WhatsAppThreatDetected(result))

        _lastStatus.value = "COMPLETED"
    }

    private fun dispatchWarningIfNeeded(result: ScanResult) {
        if (result.decision == ProtectionDecision.BLOCK) {
            warningDispatcher.showWarning(result, highPriority = true)
            return
        }

        val warning = result.toWarningUiModel()
        when (warning.severity) {
            WarningSeverity.MEDIUM -> warningDispatcher.showWarning(result, highPriority = false)
            WarningSeverity.HIGH -> warningDispatcher.showWarning(result, highPriority = true)
            WarningSeverity.CRITICAL -> warningDispatcher.showWarning(result, highPriority = true)
            WarningSeverity.NONE -> Unit
        }
    }

    private companion object {
        const val TAG = "NotificationAgent"
        const val DUPLICATE_WINDOW_MS = 3_000L
        const val MAX_DUPLICATE_CACHE_SIZE = 256
    }

    private fun WhatsAppRawNotificationData.deduplicationFingerprint(): String {
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
