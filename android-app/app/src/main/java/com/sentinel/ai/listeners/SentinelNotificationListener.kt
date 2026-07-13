package com.sentinel.ai.listeners

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import com.sentinel.ai.agents.whatsapp.NotificationAgentCoordinator
import com.sentinel.ai.agents.registry.SupportedAppRegistry
import com.sentinel.ai.core.feature.FeatureManager
import com.sentinel.ai.ui.protection.ProtectionControl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SentinelNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject lateinit var notificationAgentCoordinator: NotificationAgentCoordinator
    @Inject lateinit var supportedAppRegistry: SupportedAppRegistry

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!FeatureManager.isNotificationEnabled()) return
        if (!ProtectionControl.isProtectionEnabled(this)) {
            Log.d(TAG, "Notification ignored: protection_disabled, package=${sbn.packageName}")
            return
        }
        if (!supportedAppRegistry.isSupported(sbn.packageName)) {
            Log.d(TAG, "Notification ignored: unsupported package=${sbn.packageName}")
            return
        }
        val notification = sbn.notification ?: run {
            Log.d(TAG, "Notification ignored: package=${sbn.packageName}, reason=missing_notification")
            return
        }
        // Android (and messaging apps such as WhatsApp) post a synthetic "group summary"
        // notification in addition to the individual conversation notification once a package
        // has multiple active notifications. The summary re-announces content that was already
        // delivered (and processed) as its own onNotificationPosted callback, which is the root
        // cause of the same logical message being scanned more than once. It carries no new
        // conversation content, so it must never enter the pipeline.
        if (notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) {
            Log.d(TAG, "Notification ignored: package=${sbn.packageName}, reason=group_summary")
            return
        }
        val extras = notification.extras ?: run {
            Log.d(TAG, "Notification ignored: package=${sbn.packageName}, reason=missing_extras")
            return
        }
        val snapshot = com.sentinel.ai.agents.whatsapp.WhatsAppNotificationSnapshot(
            packageName = sbn.packageName,
            notificationKey = sbn.key,
            timestampMs = sbn.postTime,
            title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString(),
            conversationTitle = extras.getCharSequence(android.app.Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString(),
            actionLabels = notification.actions?.mapNotNull { it.title?.toString() }.orEmpty()
        )
        Log.d(
            TAG,
            "Supported application detected: package=${snapshot.packageName}, " +
                "sender=${snapshot.title.orEmpty()}, message=${snapshot.bigText ?: snapshot.text.orEmpty()}, " +
                "notification accepted for processing"
        )
        serviceScope.launch {
            notificationAgentCoordinator.onWhatsAppNotification(
                snapshot = snapshot,
                isKnownContact = isKnownContact(snapshot)
            )
        }
    }

    private fun isKnownContact(snapshot: com.sentinel.ai.agents.whatsapp.WhatsAppNotificationSnapshot): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Contact lookup skipped: READ_CONTACTS not granted")
            return false
        }

        val incomingValues = listOfNotNull(snapshot.title, snapshot.sender, snapshot.subText)
            .map(String::trim)
            .filter(String::isNotEmpty)
        val incomingNumbers = incomingValues
            .mapNotNull(::extractPhoneNumber)
            .map(::normalizeNumber)
            .filter { it.length >= MIN_PHONE_DIGITS }
            .toSet()
        val senderName = snapshot.title
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: snapshot.sender?.trim()?.takeIf(String::isNotEmpty)

        return runCatching {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val contactNumber = if (numberIndex >= 0) cursor.getString(numberIndex).orEmpty() else ""
                    val normalizedContact = normalizeNumber(contactNumber)
                    if (normalizedContact.length >= MIN_PHONE_DIGITS && normalizedContact in incomingNumbers) {
                        return@use true
                    }

                    val contactName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    if (incomingNumbers.isEmpty() && contactNamesMatch(senderName, contactName)) {
                        return@use true
                    }
                }
                false
            } ?: false
        }.onFailure {
            Log.w(TAG, "Contact lookup failed", it)
        }.getOrDefault(false)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SentinelNotification"
        const val MIN_PHONE_DIGITS = 7
    }
}

internal fun normalizeNumber(number: String): String = number
    .replace("\\s".toRegex(), "")
    .replace("-", "")
    .replace("+91", "")
    .filter(Char::isDigit)
    .takeLast(10)

internal fun contactNamesMatch(senderName: String?, displayName: String?): Boolean {
    val normalizedSender = senderName?.trim().orEmpty()
    val normalizedContact = displayName?.trim().orEmpty()
    return normalizedSender.isNotEmpty() &&
        normalizedContact.isNotEmpty() &&
        normalizedSender.equals(normalizedContact, ignoreCase = true)
}

private fun extractPhoneNumber(value: String): String? =
    PHONE_FINDER.find(value)?.value

private val PHONE_FINDER = Regex("""\+?[0-9][0-9()\-.\s]{5,}[0-9]""")
