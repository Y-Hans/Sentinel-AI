package com.sentinel.ai.listeners

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
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
            notificationAgentCoordinator.onWhatsAppNotification(snapshot)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SentinelNotification"
    }
}
