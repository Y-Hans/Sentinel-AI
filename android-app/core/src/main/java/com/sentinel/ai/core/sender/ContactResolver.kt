package com.sentinel.ai.core.sender

/**
 * Status indicating the outcome of a contact lookup operation.
 */
enum class ContactLookupStatus {
    /** Contact was successfully found in local address book. */
    MATCH_FOUND,

    /** Lookup was performed successfully, but no matching contact exists. */
    NO_MATCH,

    /** Contact read permission (e.g. READ_CONTACTS) was not granted. */
    PERMISSION_DENIED,

    /** The provided identifier is empty, malformed, or unsuitable for lookup. */
    INVALID_IDENTIFIER,

    /** Provider error, query failure, or contacts subsystem unavailable. */
    LOOKUP_ERROR
}

/**
 * Domain model representing the result of resolving contact identity.
 *
 * NOTE: [isKnownContact] indicates whether the contact exists in the address book.
 * It is distinct from [displayName], which may be null or different from the query identifier.
 */
data class ContactResolution(
    val isKnownContact: Boolean,
    val displayName: String? = null,
    val normalizedIdentifier: String = "",
    val lookupStatus: ContactLookupStatus = if (isKnownContact) ContactLookupStatus.MATCH_FOUND else ContactLookupStatus.NO_MATCH
) {
    companion object {
        fun matchFound(displayName: String?, normalizedIdentifier: String): ContactResolution =
            ContactResolution(
                isKnownContact = true,
                displayName = displayName,
                normalizedIdentifier = normalizedIdentifier,
                lookupStatus = ContactLookupStatus.MATCH_FOUND
            )

        fun noMatch(normalizedIdentifier: String = ""): ContactResolution =
            ContactResolution(
                isKnownContact = false,
                displayName = null,
                normalizedIdentifier = normalizedIdentifier,
                lookupStatus = ContactLookupStatus.NO_MATCH
            )

        fun permissionDenied(normalizedIdentifier: String = ""): ContactResolution =
            ContactResolution(
                isKnownContact = false,
                displayName = null,
                normalizedIdentifier = normalizedIdentifier,
                lookupStatus = ContactLookupStatus.PERMISSION_DENIED
            )

        fun invalidIdentifier(rawIdentifier: String = ""): ContactResolution =
            ContactResolution(
                isKnownContact = false,
                displayName = null,
                normalizedIdentifier = rawIdentifier.trim(),
                lookupStatus = ContactLookupStatus.INVALID_IDENTIFIER
            )

        fun lookupError(normalizedIdentifier: String = ""): ContactResolution =
            ContactResolution(
                isKnownContact = false,
                displayName = null,
                normalizedIdentifier = normalizedIdentifier,
                lookupStatus = ContactLookupStatus.LOOKUP_ERROR
            )
    }
}

/**
 * Platform-independent domain boundary for resolving contact identities.
 */
interface ContactResolver {
    /**
     * Resolves the contact identity and existence for the given [identifier]
     * (phone number, email, contact name, or handle).
     */
    fun resolve(identifier: String): ContactResolution

    /**
     * Convenience method to check whether [identifier] exists as a known contact.
     */
    fun isKnownContact(identifier: String): Boolean = resolve(identifier).isKnownContact
}
