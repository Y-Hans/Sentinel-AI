package com.sentinel.ai.warning

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import com.sentinel.ai.R
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.ScanResult

class WarningNotificationHelper(private val context: Context) {

    fun showWarning(result: ScanResult, highPriority: Boolean, fullScreen: Boolean = false) {
        val model = result.toWarningUiModel()
        if (model.severity == WarningSeverity.NONE) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Skipping notification for ${result.id}: POST_NOTIFICATIONS not granted")
            return
        }

        ensureChannel(highPriority)

        val shouldUseFullScreen = fullScreen && result.decision == ProtectionDecision.BLOCK
        val alertIntent = if (shouldUseFullScreen) {
            CriticalAlertActivity.newIntent(context, result)
        } else {
            ScamWarningActivity.newIntent(context, result)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            result.id.hashCode(),
            alertIntent,
            pendingIntentFlags()
        )

        // Android 14 (API 34) introduced a user-revocable, per-app grant for the full-screen
        // intent privilege: even though USE_FULL_SCREEN_INTENT is declared in the manifest, the
        // system may silently refuse to launch the full-screen UI and only show a heads-up
        // notification instead. Checking this avoids requesting a full-screen intent we already
        // know will be downgraded, and keeps the emitted notification (and its contentIntent)
        // correct either way.
        val canUseFullScreenIntent = shouldUseFullScreen && canUseFullScreenIntent()

        val notification = NotificationCompat.Builder(context, channelId(highPriority))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(model.title)
            .setContentText("Risk Level: ${model.riskLevelLabel}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(buildBody(model)))
            .setPriority(if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .apply {
                if (canUseFullScreenIntent) {
                    setFullScreenIntent(contentIntent, true)
                }
            }
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(result.id.hashCode(), notification)
        }
    }

    private fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        return manager.canUseFullScreenIntent()
    }

    /**
     * Starts [CriticalAlertActivity] directly. Only safe to call from a context that Android
     * currently allows to start background activities (e.g. a foreground Activity, or another
     * context covered by a background-activity-launch exemption). Do NOT call this from a plain
     * background Service - use [showWarning] with `fullScreen = true` instead, which delivers the
     * critical alert via a notification full-screen intent and is reliably exempt from Android
     * 10+ background activity launch restrictions.
     */
    fun launchCriticalAlert(result: ScanResult) {
        if (result.decision != ProtectionDecision.BLOCK) return
        val intent = CriticalAlertActivity.newIntent(context, result)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Log.w(TAG, "Unable to launch critical alert directly for ${result.id}", it)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(highPriority: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = channelId(highPriority)
        if (manager.getNotificationChannel(id) != null) return

        val importance = if (highPriority) {
            NotificationManager.IMPORTANCE_HIGH
        } else {
            NotificationManager.IMPORTANCE_DEFAULT
        }

        manager.createNotificationChannel(
            NotificationChannel(id, if (highPriority) HIGH_CHANNEL_NAME else MEDIUM_CHANNEL_NAME, importance)
        )
    }

    private fun buildBody(model: WarningUiModel): String {
        val reasons = model.reasons.take(3).joinToString("\n") { "* $it" }
        return "Risk Level: ${model.riskLevelLabel}\nRisk Score: ${model.riskScore.toInt()}\n\nReasons:\n$reasons"
    }

    private fun channelId(highPriority: Boolean) = if (highPriority) HIGH_CHANNEL_ID else MEDIUM_CHANNEL_ID

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    companion object {
        private const val TAG = "WarningNotifications"
        private const val MEDIUM_CHANNEL_ID = "warning_medium"
        private const val HIGH_CHANNEL_ID = "warning_high"
        private const val MEDIUM_CHANNEL_NAME = "Scam Warnings"
        private const val HIGH_CHANNEL_NAME = "High Priority Scam Warnings"
    }
}
