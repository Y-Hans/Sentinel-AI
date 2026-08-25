package com.sentinel.ai.core.sender

/**
 * Top-level domain classification for message and notification senders.
 */
enum class SenderType {
    /** Service or transactional sender (e.g. verified financial or enterprise SMS headers). */
    SERVICE,

    /** Government authority or public entity sender header. */
    GOVERNMENT,

    /** Promotional or marketing message sender header. */
    PROMOTIONAL,

    /** Personal sender identified by a valid personal dialable phone number. */
    PERSONAL,

    /** Unclassified, unknown, malformed, or arbitrary sender identifier. */
    UNKNOWN
}
