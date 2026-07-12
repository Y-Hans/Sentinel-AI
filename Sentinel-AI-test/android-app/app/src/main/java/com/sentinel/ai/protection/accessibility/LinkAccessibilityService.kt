package com.sentinel.ai.protection.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.sentinel.ai.protection.intent.IntentPayloadExtras
import com.sentinel.ai.protection.intent.ScanLoadingActivity

/**
 * Detects URLs exposed by other apps through accessibility events and routes each new URL to
 * Sentinel's existing intent scan flow.
 */
class LinkAccessibilityService : AccessibilityService() {

    // ✅ Only monitor important apps (prevents noise + battery drain)
    private val monitoredApps = setOf(
        "com.whatsapp",
        "com.android.chrome",
        "com.google.android.gm",
        "com.google.android.apps.messaging"
    )

    private val recentUrls = LinkedHashMap<String, Long>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !event.isSupportedTextEvent()) return

        val pkg = event.packageName?.toString() ?: return

        // ❌ Ignore Sentinel itself (prevents loop)
        if (pkg == packageName) return

        // ❌ Ignore apps we are not monitoring
        if (pkg !in monitoredApps) return

        val text = AccessibilityUtils.extractText(event)
        if (text.isBlank()) return

        val observedAtMs = SystemClock.elapsedRealtime()

        AccessibilityUtils.extractUrls(text).forEach { url ->
            if (!AccessibilityUtils.shouldScanUrl(recentUrls, url, observedAtMs)) {
                return@forEach
            }

            Log.d(TAG, "Detected URL from $pkg: $url")
            launchScan(url)
        }
    }

    override fun onInterrupt() = Unit

    private fun launchScan(url: String) {
        val scanIntent = Intent(this, ScanLoadingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(IntentPayloadExtras.EXTRA_PAYLOAD_TYPE, IntentPayloadExtras.TYPE_URL)
            putExtra(IntentPayloadExtras.EXTRA_PAYLOAD_VALUE, url)
            putExtra(IntentPayloadExtras.EXTRA_FROM_VIEW_INTENT, false)
        }

        startActivity(scanIntent)
    }

    private fun AccessibilityEvent.isSupportedTextEvent(): Boolean =
        eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED

    private companion object {
        const val TAG = "AccessibilityService"
    }
}