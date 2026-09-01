package com.sentinel.ai.listeners

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.sentinel.ai.agents.whatsapp.NotificationAgentCoordinator
import com.sentinel.ai.agents.registry.SupportedAppRegistry
import com.sentinel.ai.core.feature.FeatureManager
import com.sentinel.ai.core.sender.ContactResolver
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
    @Inject lateinit var contactResolver: ContactResolver

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

        var titleExtra: String? = null
        var textExtra: String? = null
        var bigTextExtra: String? = null
        var conversationTitleExtra: String? = null
        var subTextExtra: String? = null

        try {
            titleExtra = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
                ?: extras.getCharSequence("android.title.big")?.toString()
            conversationTitleExtra = extras.getCharSequence(android.app.Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            subTextExtra = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString()
            textExtra = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            bigTextExtra = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()

            // Handle InboxStyle / text lines
            if (textExtra.isNullOrBlank() && bigTextExtra.isNullOrBlank()) {
                val lines = extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)
                if (!lines.isNullOrEmpty()) {
                    bigTextExtra = lines.filterNotNull().joinToString("\n") { it.toString() }
                }
            }

            // Handle MessagingStyle extras (used extensively by Google Messages)
            if (textExtra.isNullOrBlank() && bigTextExtra.isNullOrBlank()) {
                val messagesArray = extras.getParcelableArray(android.app.Notification.EXTRA_MESSAGES)
                if (!messagesArray.isNullOrEmpty()) {
                    val lastBundle = messagesArray.lastOrNull() as? android.os.Bundle
                    val msgText = lastBundle?.getCharSequence("text")?.toString()
                    if (!msgText.isNullOrBlank()) {
                        textExtra = msgText
                    }
                    if (titleExtra.isNullOrBlank()) {
                        val senderName = lastBundle?.getCharSequence("sender")?.toString()
                        if (!senderName.isNullOrBlank()) {
                            titleExtra = senderName
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Malformed notification extras for package=${sbn.packageName}", e)
        }

        val snapshot = com.sentinel.ai.agents.whatsapp.WhatsAppNotificationSnapshot(
            packageName = sbn.packageName,
            notificationKey = sbn.key,
            timestampMs = sbn.postTime,
            title = titleExtra,
            text = textExtra,
            bigText = bigTextExtra,
            conversationTitle = conversationTitleExtra,
            subText = subTextExtra,
            actionLabels = try {
                notification.actions?.mapNotNull { it.title?.toString() }.orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
        )

        val isKnown = isKnownContact(snapshot)

        serviceScope.launch {
            try {
                notificationAgentCoordinator.onWhatsAppNotification(
                    snapshot = snapshot,
                    isKnownContact = isKnown
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process notification for package=${snapshot.packageName}", e)
            }
        }
    }

    internal fun isKnownContact(snapshot: com.sentinel.ai.agents.whatsapp.WhatsAppNotificationSnapshot): Boolean {
        val sender = snapshot.title?.trim().takeUnless { it.isNullOrEmpty() }
            ?: snapshot.conversationTitle?.trim().takeUnless { it.isNullOrEmpty() }
            ?: snapshot.subText?.trim().takeUnless { it.isNullOrEmpty() }
            ?: return false

        return contactResolver.isKnownContact(sender)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val TAG = "SentinelNotification"
    }
}