package com.sentinel.ai.warning

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.sentinel.ai.core.event.ThreatEvent
import com.sentinel.ai.core.event.ThreatEventBus
import com.sentinel.ai.core.event.ThreatJournal
import com.sentinel.ai.core.model.ProtectionDecision
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import javax.inject.Inject

@AndroidEntryPoint
class ThreatEventSubscriberService : Service() {

    @Inject lateinit var threatEventBus: ThreatEventBus

    private val serviceScope = CoroutineScope(Job() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        Log.d("ThreatSubscriber", "ThreatEventSubscriberService started")
        serviceScope.launch {
                Log.d("ThreatSubscriber", "Threat event collection begins")
                try {
                    threatEventBus.events.collectLatest { event ->
                        Log.d("ThreatSubscriber", "Received threat event: $event")
                        ThreatJournal.record(event)
                        handleEvent(event)
                    }
                } catch (e: CancellationException) {
                Log.d("ThreatSubscriber", "Threat event collection coroutine cancelled")
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

        val warning = result.toWarningUiModel()
        if (warning.severity == WarningSeverity.NONE) return

        Log.d("ThreatSubscriber", "About to create WarningNotificationHelper")
        val helper = WarningNotificationHelper(this)
        Log.d("ThreatSubscriber", "Created WarningNotificationHelper")
        // Do NOT call helper.launchCriticalAlert(result) here. This service has no visible UI,
        // and starting an Activity directly from a background Service context is subject to
        // Android's background activity launch (BAL) restrictions from Android 10 onward. Whether
        // that direct startActivity() call succeeds depends on transient, OEM-specific exemptions
        // (e.g. whether SYSTEM_ALERT_WINDOW is granted, recent foreground state, manufacturer
        // policy), which is exactly why CriticalAlertActivity behaved inconsistently across
        // Android 10-15 devices. The one launch path Android guarantees from the background is a
        // notification's full-screen intent (NotificationCompat.Builder#setFullScreenIntent),
        // which is explicitly exempted from BAL restrictions. Posting that notification below is
        // therefore the sole, reliable trigger for the critical alert UI.
        when (warning.severity) {
            WarningSeverity.MEDIUM -> helper.showWarning(result, highPriority = false)
            WarningSeverity.HIGH -> helper.showWarning(
                result,
                highPriority = true,
                fullScreen = result.decision == ProtectionDecision.BLOCK
            )
            WarningSeverity.CRITICAL -> helper.showWarning(
                result,
                highPriority = true,
                fullScreen = result.decision == ProtectionDecision.BLOCK
            )
            WarningSeverity.NONE -> Unit
        }
    }
}
