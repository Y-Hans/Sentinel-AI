package com.sentinel.ai.warning

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.core.warning.WarningNotificationDispatcher
import com.sentinel.ai.core.warning.WarningSeverity
import com.sentinel.ai.core.warning.toWarningUiModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarningNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) : WarningNotificationDispatcher {

    override fun showWarning(result: ScanResult, highPriority: Boolean) {
        val model = result.toWarningUiModel()
        if (model.severity == WarningSeverity.NONE && result.decision != ProtectionDecision.BLOCK) return
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

        val alertIntent = ScamWarningActivity.newIntent(context, result)

        val contentIntent = PendingIntent.getActivity(
            context,
            result.id.hashCode(),
            alertIntent,
            pendingIntentFlags()
        )

        val notification = NotificationCompat.Builder(context, channelId(highPriority))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(model.title)
            .setContentText("Risk Level: ${model.riskLevelLabel}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(buildBody(model)))
            .setPriority(if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
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

    private fun buildBody(model: com.sentinel.ai.core.warning.WarningUiModel): String {
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
