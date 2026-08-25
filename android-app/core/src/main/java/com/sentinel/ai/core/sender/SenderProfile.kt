package com.sentinel.ai.core.sender

/**
 * Immutable domain model representing the classified profile of a message sender.
 *
 * Senders are classified for contextual intelligence, not as an ultimate safety verdict.
 */
data class SenderProfile(
    val rawIdentifier: String,
    val normalizedIdentifier: String,
    val senderType: SenderType,
    val isKnownContact: Boolean = false,
    val displayName: String? = null,
    val confidence: Float = 1.0f
) {
    init {
        require(confidence in 0f..1f) {
            "Confidence must be between 0.0 and 1.0, found: $confidence"
        }
    }
}
