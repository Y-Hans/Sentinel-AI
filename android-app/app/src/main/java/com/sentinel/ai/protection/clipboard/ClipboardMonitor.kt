package com.sentinel.ai.protection.clipboard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sentinel.ai.R
import com.sentinel.ai.core.feature.FeatureManager
import com.sentinel.ai.protection.intent.IntentPayloadExtras
import com.sentinel.ai.protection.intent.ScanLoadingActivity

/** Process-scoped clipboard listener. Android only exposes clipboard contents when access is allowed. */
class ClipboardMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val clipboard = appContext.getSystemService(ClipboardManager::class.java)
    private var started = false
    private var lastUrl: String? = null
    private var lastDetectedAt = 0L

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { onClipboardChanged() }

    fun start() {
        if (started) return
        createNotificationChannel()
        clipboard.addPrimaryClipChangedListener(listener)
        started = true
    }

    fun stop() {
        if (!started) return
        clipboard.removePrimaryClipChangedListener(listener)
        started = false
    }

    private fun onClipboardChanged() {
        if (!FeatureManager.isClipboardEnabled()) return
        val clip = clipboard.primaryClip ?: return
        if (clip.description.extras?.getBoolean(IntentPayloadExtras.EXTRA_INTERNAL_LAUNCH) == true) {
            Log.d(TAG, "ignored Sentinel clipboard content")
            return
        }
        val text = clip.getItemAt(0).coerceToText(appContext)?.toString() ?: return
        val url = ClipboardUrlDetector.firstValidUrl(text) ?: return
        val now = SystemClock.elapsedRealtime()
        if (url == lastUrl && now - lastDetectedAt < DUPLICATE_WINDOW_MS) {
            Log.d(TAG, "ignored duplicate")
            return
        }
        lastUrl = url
        lastDetectedAt = now
        Log.d(TAG, "URL detected")
        showPrompt(url)
    }

    private fun showPrompt(url: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "prompt skipped: notification permission not granted")
            return
        }
        val scanIntent = Intent(appContext, ScanLoadingActivity::class.java).apply {
            putExtra(IntentPayloadExtras.EXTRA_PAYLOAD_TYPE, IntentPayloadExtras.TYPE_URL)
            putExtra(IntentPayloadExtras.EXTRA_PAYLOAD_VALUE, url)
            putExtra(IntentPayloadExtras.EXTRA_FROM_VIEW_INTENT, false)
            putExtra(IntentPayloadExtras.EXTRA_INTERNAL_LAUNCH, true)
        }
        val scanPendingIntent = PendingIntent.getActivity(
            appContext, SCAN_REQUEST_CODE, scanIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ignoreIntent = Intent(appContext, ClipboardActionReceiver::class.java).apply {
            action = ACTION_IGNORE
        }
        val ignorePendingIntent = PendingIntent.getBroadcast(
            appContext, IGNORE_REQUEST_CODE, ignoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Link copied. Scan with Sentinel?")
            .setContentText(url)
            .setStyle(NotificationCompat.BigTextStyle().bigText(url))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(scanPendingIntent)
            .addAction(0, "Scan", scanPendingIntent)
            .addAction(0, "Ignore", ignorePendingIntent)
            .build()
        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, "Copied link scanning", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Prompts to scan links copied to the clipboard" }
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_IGNORE = "com.sentinel.ai.protection.clipboard.action.IGNORE"
        private const val TAG = "ClipboardMonitor"
        private const val CHANNEL_ID = "clipboard_link_scan"
        private const val NOTIFICATION_ID = 2401
        private const val SCAN_REQUEST_CODE = 2401
        private const val IGNORE_REQUEST_CODE = 2402
        private const val DUPLICATE_WINDOW_MS = 5_000L

        fun cancelPrompt(context: Context) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        /** Use this whenever Sentinel writes clipboard text so its listener ignores that write. */
        fun setInternalClipboard(context: Context, text: String) {
            val clip = ClipData.newPlainText("Sentinel", text)
            clip.description.extras = PersistableBundle().apply {
                putBoolean(IntentPayloadExtras.EXTRA_INTERNAL_LAUNCH, true)
            }
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        }
    }
}
