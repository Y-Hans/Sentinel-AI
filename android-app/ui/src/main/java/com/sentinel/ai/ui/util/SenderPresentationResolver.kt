package com.sentinel.ai.ui.util

import android.content.Context
import com.sentinel.ai.core.sender.ContactResolver

data class SenderPresentation(
    val primaryText: String,
    val secondaryText: String? = null
)

object SenderPresentationResolver {
    var contactResolverFactory: ((Context) -> ContactResolver)? = null
}

fun resolveSenderPresentation(
    context: Context,
    senderDisplayName: String?,
    senderIdentifier: String?
): SenderPresentation {
    val resolver = SenderPresentationResolver.contactResolverFactory?.invoke(context)
    return resolveSenderPresentation(
        senderDisplayName = senderDisplayName,
        senderIdentifier = senderIdentifier,
        contactResolver = resolver
    )
}

fun resolveSenderPresentation(
    senderDisplayName: String?,
    senderIdentifier: String?,
    contactResolver: ContactResolver? = null
): SenderPresentation {
    val identifier = senderIdentifier?.trim().takeUnless { it.isNullOrEmpty() }
    if (identifier != null) {
        val contactName = contactResolver?.resolve(identifier)?.takeIf { it.isKnownContact }?.displayName
        return SenderPresentation(
            primaryText = contactName ?: "Unknown Contact",
            secondaryText = identifier
        )
    }

    val preservedValue = senderDisplayName?.trim().takeUnless { it.isNullOrEmpty() }
    return SenderPresentation(primaryText = preservedValue ?: "Unknown Sender")
}
