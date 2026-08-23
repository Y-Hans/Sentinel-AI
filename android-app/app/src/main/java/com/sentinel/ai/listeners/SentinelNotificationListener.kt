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

        val notification = sbn.notification ?: return

        if (notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) {
            Log.d(TAG, "Notification ignored: group_summary")
            return
        }

        val extras = notification.extras ?: return

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

        serviceScope.launch {
            try {
                notificationAgentCoordinator.onWhatsAppNotification(
                    snapshot = snapshot,
                    isKnownContact = isKnownContact(snapshot)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process notification for package=${snapshot.packageName}", e)
            }
        }
    }

    private fun isKnownContact(snapshot: com.sentinel.ai.agents.whatsapp.WhatsAppNotificationSnapshot): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "READ_CONTACTS not granted")
            return false
        }

        val sender = snapshot.title?.trim().orEmpty()
        if (sender.isEmpty()) return false

        return isKnownSender(sender, contentResolver)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    internal companion object {
        const val TAG = "SentinelNotification"

        internal fun isKnownSender(sender: String, contentResolver: android.content.ContentResolver): Boolean {
            val trimmed = sender.trim()
            if (trimmed.isEmpty()) return false

            return if (isPhoneNumber(trimmed)) {
                isKnownPhoneNumber(trimmed, contentResolver)
            } else {
                isKnownContactName(trimmed, contentResolver)
            }
        }

        internal fun isKnownPhoneNumber(phoneNumber: String, contentResolver: android.content.ContentResolver): Boolean {
            return runCatching {
                // 1. Targeted indexed query via PhoneLookup
                val lookupUri = android.net.Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    android.net.Uri.encode(phoneNumber)
                )
                val foundInLookup = contentResolver.query(
                    lookupUri,
                    arrayOf(ContactsContract.PhoneLookup._ID),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    cursor.moveToFirst()
                } ?: false

                if (foundInLookup) return true

                // 2. Targeted fallback query on Phone table matching suffix digits
                val normalizedDigits = phoneNumber.filter(Char::isDigit)
                if (normalizedDigits.length >= 7) {
                    val searchSuffix = normalizedDigits.takeLast(10)
                    contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                        arrayOf("%$searchSuffix"),
                        null
                    )?.use { cursor ->
                        val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        while (cursor.moveToNext()) {
                            val contactNumber = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty() else ""
                            if (phoneNumbersMatch(phoneNumber, contactNumber)) {
                                return@use true
                            }
                        }
                        false
                    } ?: false
                } else {
                    false
                }
            }.onFailure {
                Log.e(TAG, "Contact phone lookup failed", it)
            }.getOrDefault(false)
        }

        internal fun isKnownContactName(name: String, contentResolver: android.content.ContentResolver): Boolean {
            val normalized = normalizeName(name)
            if (normalized.isEmpty()) return false

            return runCatching {
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ? COLLATE NOCASE",
                    arrayOf(name.trim()),
                    null
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val contactName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                        if (isNameMatch(name, contactName)) {
                            return@use true
                        }
                    }
                    false
                } ?: false
            }.onFailure {
                Log.e(TAG, "Contact name query failed", it)
            }.getOrDefault(false)
        }

        internal fun isPhoneNumber(text: String): Boolean {
            val trimmed = text.trim()
            val digits = trimmed.filter(Char::isDigit)
            return digits.length in 7..15 && trimmed.matches(Regex("^[+0-9\\s()\\-.]+$"))
        }

        internal fun normalizeNumber(number: String): String {
            val trimmed = number.trim()
            val hasPlus = trimmed.contains("+")
            val digits = trimmed.filter(Char::isDigit)
            return if (hasPlus && digits.isNotEmpty()) "+$digits" else digits
        }

        internal fun phoneNumbersMatch(a: String, b: String): Boolean {
            val na = normalizeNumber(a)
            val nb = normalizeNumber(b)
            if (na.isEmpty() || nb.isEmpty()) return false
            if (na == nb) return true

            val da = na.filter(Char::isDigit)
            val db = nb.filter(Char::isDigit)
            if (da == db) return true

            val minLength = minOf(da.length, db.length)
            if (minLength >= 7 && (da.endsWith(db) || db.endsWith(da))) {
                return true
            }
            return false
        }

        internal fun isNameMatch(a: String, b: String): Boolean {
            val na = normalizeName(a)
            val nb = normalizeName(b)

            return na.isNotEmpty() &&
                    nb.isNotEmpty() &&
                    na == nb
        }

        internal fun normalizeName(name: String): String {
            return name.lowercase(java.util.Locale.ROOT)
                .replace("[^a-z0-9 ]".toRegex(), "")
                .replace("\\s+".toRegex(), " ")
                .trim()
        }
    }
}