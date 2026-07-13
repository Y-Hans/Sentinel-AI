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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.CancellationException
import javax.inject.Inject

@AndroidEntryPoint
class ThreatEventSubscriberService : Service() {

    @Inject lateinit var threatEventBus: ThreatEventBus

    private val serviceScope = CoroutineScope(Job() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        Log.d("ThreatSubscriber", "Service started")

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

        if (result.decision == ProtectionDecision.BLOCK) {
            helper.showWarning(result, highPriority = true)
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

}
