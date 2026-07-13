package com.sentinel.ai.warning

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sentinel.ai.R
import com.sentinel.ai.core.event.ThreatEvent
import com.sentinel.ai.core.event.ThreatEventBus
import com.sentinel.ai.core.event.ThreatJournal
import com.sentinel.ai.core.model.ProtectionDecision
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.CancellationException
import javax.inject.Inject
import android.app.PendingIntent
import android.app.NotificationManager

@AndroidEntryPoint
class ThreatEventSubscriberService : Service() {

    @Inject lateinit var threatEventBus: ThreatEventBus

    private val serviceScope = CoroutineScope(Job() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        Log.d("ThreatSubscriber", "Service started")

        createAlertChannel() // 🔥 ensure channel exists

        serviceScope.launch {
            try {
                threatEventBus.events.collectLatest { event ->
                    ThreatJournal.record(event)
                    handleEvent(event)
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleEvent(event: ThreatEvent) {
        val result = when (event) {
            is ThreatEvent.WhatsAppThreatDetected -> event.scanResult
            else -> return
        }

        val helper = WarningNotificationHelper(this)

        // 🔥 CRITICAL FIX — ALWAYS use full-screen notification for BLOCK
        if (result.decision == ProtectionDecision.BLOCK) {
            showFullScreenAlert(result)
            return
        }

        val warning = result.toWarningUiModel()
        if (warning.severity == WarningSeverity.NONE) return

        when (warning.severity) {
            WarningSeverity.MEDIUM -> helper.showWarning(result, highPriority = false)
            WarningSeverity.HIGH -> helper.showWarning(result, highPriority = true)
            WarningSeverity.CRITICAL -> helper.showWarning(result, highPriority = true)
            WarningSeverity.NONE -> Unit
        }
    }

    // ================= FULL SCREEN FIX =================

    private fun showFullScreenAlert(result: com.sentinel.ai.core.model.ScanResult) {

        val intent = Intent(this, CriticalAlertActivity::class.java).apply {
            // ❌ DO NOT pass object
            // putExtra("scan_result", result)

            // ✅ PASS SAFE DATA
            putExtra("url", result.source)                 // ✅ correct field
            putExtra("score", result.riskScore.toInt())    // ✅ Float → Int
            putExtra("decision", result.decision.name)

            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // ✅ FIXED ICON
            .setContentTitle("🚨 Critical Threat Detected")    // ✅ WILL WORK NOW
            .setContentText("Tap to view details")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(9999, notification)
    }

    // ================= CHANNEL =================

    private fun createAlertChannel() {
        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Critical Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Full screen scam alerts"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "CRITICAL_ALERT_CHANNEL"
    }
}